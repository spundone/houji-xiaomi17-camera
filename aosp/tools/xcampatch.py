#!/usr/bin/env python3
"""Applies the camera patches to an apktool-decoded smali tree, or checks a patched one.

    xcampatch.py apply    SMALI_TREE PATCHES.json REPORT.json
    xcampatch.py verify   SMALI_TREE REPORT.json
    xcampatch.py assemble ORIGINAL.apk REBUILT.apk HELPERS.dex OUT.apk

Patches are data (patches/<build>/patches.json), not smali diffs, so this repository carries
no decompiled Xiaomi code. Each patch finds its method by class, name and prototype, and
checks fingerprints (strings its body or class must contain) before touching anything; a
patch that matches nothing, or more than it expects, stops the build.

Actions:
  return   Replace the body with a constant: a boolean, int, string or null, a static field,
           or the result of a static helper called with string or class constants.
  wrap     Xposed-style hook without Xposed. The original method is renamed to <name>$xcam
           and made private; a new method under the original name calls it and runs helpers:
           "enter"/"exit" around it (exit also runs if it throws), "guard" to skip it (a
           helper answering false returns void, zero or null instead), "before" to replace an
           argument (or, without "set", just to run), "after" to replace the result. Helpers
           are static methods in the helper dex, given values listed in "pass":
             result, this, argN, str:<text>, class:<Ldescriptor;>
  redirect Rewrite every call to a framework method into a call to a static helper taking
           the receiver as its first argument, for hooks that sat on framework classes. For a
           constructor (<init>) the call stays and the helper is called after it, given the
           new object and the constructor's arguments.
  fields   Add field declarations ("add") to a class, for code loaded at run time that
           expects them.

apply writes REPORT.json: every method it generated, as text, and every redirected call. verify
decodes nothing itself; give it the smali tree of the rebuilt APK and it checks each generated
method is there unchanged and no redirected call is left.
"""

import json
import os
import re
import sys

WRAP_SUFFIX = "$xcam"
ACCESS = ("public", "protected", "private")


def die(msg):
    sys.exit("xcampatch: " + msg)


# ---------------------------------------------------------------------------------------
# Types and prototypes


def parse_types(s):
    """'IJLa/b;[I' -> ['I', 'J', 'La/b;', '[I']"""
    out, i = [], 0
    while i < len(s):
        j = i
        while s[j] == "[":
            j += 1
        if s[j] == "L":
            j = s.index(";", j)
        out.append(s[i:j + 1])
        i = j + 1
    return out


def split_proto(proto):
    """'(ILjava/lang/String;)Z' -> (['I', 'Ljava/lang/String;'], 'Z')"""
    m = re.fullmatch(r"\((.*)\)(.+)", proto)
    if not m:
        die("bad prototype " + proto)
    return parse_types(m.group(1)), m.group(2)


def width(t):
    return 2 if t in ("J", "D") else 1


def is_ref(t):
    return t[0] in "L["


def move_op(t):
    return "move-wide/16" if width(t) == 2 else "move-object/16" if is_ref(t) else "move/16"


def move_result_op(t):
    return "move-result-wide" if width(t) == 2 else "move-result-object" if is_ref(t) else "move-result"


def return_op(t):
    return "return-void" if t == "V" else "return-wide" if width(t) == 2 else "return-object" if is_ref(t) else "return"


def split_ref(ref):
    """'La/b;->name(I)Z' -> ('La/b;', 'name', '(I)Z')"""
    m = re.fullmatch(r"(L[^;]+;)->([^(]+)(\(.*)", ref)
    if not m:
        die("bad method reference " + ref)
    return m.group(1), m.group(2), m.group(3)


# ---------------------------------------------------------------------------------------
# Smali tree


class Method:
    HEADER = re.compile(r"\.method (?P<flags>(?:[\w-]+ )*)(?P<name>[^\s(]+)(?P<proto>\(.*)$")

    def __init__(self, cls, start, end):
        self.cls, self.start, self.end = cls, start, end
        m = self.HEADER.match(cls.lines[start])
        self.flags = m.group("flags").split()
        self.name = m.group("name")
        self.proto = m.group("proto")
        self.params, self.ret = split_proto(self.proto)

    @property
    def static(self):
        return "static" in self.flags

    @property
    def body(self):
        return "\n".join(self.cls.lines[self.start:self.end + 1])

    @property
    def sig(self):
        return self.name + self.proto


class SmaliClass:
    def __init__(self, path):
        self.path = path
        with open(path, encoding="utf-8") as f:
            self.lines = f.read().split("\n")
        self.desc = self.lines[0].split()[-1]
        self.dirty = False
        self.parse()

    def parse(self):
        self.methods, start = [], None
        for i, line in enumerate(self.lines):
            if line.startswith(".method "):
                start = i
            elif line == ".end method" and start is not None:
                self.methods.append(Method(self, start, i))
                start = None

    def method(self, name, proto):
        for m in self.methods:
            if m.name == name and m.proto == proto:
                return m
        return None

    @property
    def text(self):
        return "\n".join(self.lines)

    def replace(self, method, new_lines):
        self.lines[method.start:method.end + 1] = new_lines
        self.dirty = True
        self.parse()

    def save(self):
        if self.dirty:
            with open(self.path, "w", encoding="utf-8") as f:
                f.write(self.text)
            self.dirty = False


class Tree:
    def __init__(self, root):
        self.root = root
        self.paths = {}
        for d in sorted(os.listdir(root)):
            if not (d == "smali" or d.startswith("smali_classes")):
                continue
            for dirpath, _, files in os.walk(os.path.join(root, d)):
                for fn in files:
                    if fn.endswith(".smali"):
                        p = os.path.join(dirpath, fn)
                        with open(p, encoding="utf-8") as f:
                            desc = f.readline().split()[-1]
                        self.paths[desc] = p
        if not self.paths:
            die("no smali in " + root)
        self.loaded = {}

    def get(self, desc, required=True):
        if desc not in self.loaded:
            p = self.paths.get(desc)
            if p is None:
                if required:
                    die("class not found: " + desc)
                return None
            self.loaded[desc] = SmaliClass(p)
        return self.loaded[desc]

    def all_paths(self):
        return self.paths.items()

    def save(self):
        for c in self.loaded.values():
            c.save()


# ---------------------------------------------------------------------------------------
# Finding methods


def check_fingerprints(tree, patch):
    for c in patch.get("check", []):
        cls = tree.get(c["class"])
        for s in c.get("has", []):
            if s not in cls.text:
                die(f"{patch['id']}: fingerprint '{s}' not in {c['class']}")


def find_methods(tree, patch):
    f = patch["find"]
    classes = f["classes"] if "classes" in f else [f["class"]]
    found = []
    for desc in classes:
        cls = tree.get(desc, required=not f.get("optional_classes"))
        if cls is None:
            continue
        for m in cls.methods:
            if m.name != f["name"]:
                continue
            if "proto" in f and m.proto != f["proto"]:
                continue
            if "proto_re" in f and not re.fullmatch(f["proto_re"], m.proto):
                continue
            if "static" in f and m.static != f["static"]:
                continue
            if any(x in m.flags for x in ("abstract", "native")):
                continue
            if not all(s in m.body for s in f.get("has", [])):
                continue
            found.append(m)
    want = f.get("count", 1)
    lo, hi = (1, 99) if want == "+" else (want, want)
    if not lo <= len(found) <= hi:
        where = ", ".join(classes)
        die(f"{patch['id']}: expected {want} match(es) for {f['name']}{f.get('proto', '')} in {where}, found {len(found)}")
    return found


# ---------------------------------------------------------------------------------------
# Code generation


def keep_annotations(method):
    """Method-level annotation blocks (generic signatures, throws) from the original."""
    out, inside = [], False
    for line in method.cls.lines[method.start + 1:method.end]:
        s = line.strip()
        if s.startswith(".annotation"):
            inside = True
        if inside:
            out.append(line)
        if s == ".end annotation":
            inside = False
    return out


def const_lines(value, t, reg="v0"):
    if t == "Z":
        return [f"    const/4 {reg}, {'0x1' if value else '0x0'}"]
    if t in ("I", "S", "B", "C"):
        return [f"    const {reg}, {int(value):#x}"]
    if t == "Ljava/lang/String;":
        return [f"    const-string {reg}, {json.dumps(value, ensure_ascii=False)}"]
    if value is None and is_ref(t):
        return [f"    const/4 {reg}, 0x0"]
    die(f"no constant of type {t} for {value!r}")


def token_type(tok, method):
    if tok == "result":
        return method.ret
    if tok == "this":
        if method.static:
            die(f"'this' passed from static {method.sig}")
        return method.cls.desc
    if tok.startswith("arg"):
        return method.params[int(tok[3:])]
    if tok.startswith("str:"):
        return "Ljava/lang/String;"
    if tok.startswith("class:"):
        return "Ljava/lang/Class;"
    die("unknown token " + tok)


def assignable(src, dst):
    if src == dst:
        return True
    return is_ref(src) and dst == "Ljava/lang/Object;"


def param_reg(method, tok):
    """The p register holding 'this' or argN."""
    regs, n = {}, 0
    if not method.static:
        regs["this"] = 0
        n = 1
    for i, t in enumerate(method.params):
        regs[f"arg{i}"] = n
        n += width(t)
    return f"p{regs[tok]}"


def param_count(method):
    return (0 if method.static else 1) + sum(width(t) for t in method.params)


def call_helper(pid, helper, tokens, method, block):
    """Moves the tokens into v<block>.. and calls the helper; returns lines and its return type."""
    hcls, hname, hproto = split_ref(helper)
    hparams, hret = split_proto(hproto)
    types = [token_type(t, method) for t in tokens]
    if len(types) != len(hparams) or not all(assignable(s, d) for s, d in zip(types, hparams)):
        die(f"{pid}: {helper} takes {hparams}, given {tokens} = {types}")
    lines, reg = [], block
    for tok, t in zip(tokens, types):
        if tok == "result":
            lines.append(f"    {move_op(t)} v{reg}, v0")
        elif tok == "this" or tok.startswith("arg"):
            lines.append(f"    {move_op(t)} v{reg}, {param_reg(method, tok)}")
        elif tok.startswith("str:"):
            lines.append(f"    const-string/jumbo v{reg}, {json.dumps(tok[4:], ensure_ascii=False)}")
        else:
            lines.append(f"    const-class v{reg}, {tok[6:]}")
        reg += width(t)
    if reg == block:
        lines.append(f"    invoke-static {{}}, {helper}")
    else:
        lines.append(f"    invoke-static/range {{v{block} .. v{reg - 1}}}, {helper}")
    return lines, hret, reg - block


def gen_return(patch, method):
    a = patch["action"]
    t = method.ret
    header = method.cls.lines[method.start]
    body = keep_annotations(method)
    if "field" in a:
        ftype = a["field"].split(":", 1)[1]
        if ftype != t:
            die(f"{patch['id']}: field type {ftype} is not the return type {t}")
        op = "sget-object" if is_ref(t) else "sget-boolean" if t == "Z" else "sget"
        code = [f"    {op} v0, {a['field']}"]
        locals_ = 1
    elif "helper" in a:
        code, hret, used = call_helper(patch["id"], a["helper"], a.get("pass", []), method, 2)
        if hret != t and not (is_ref(t) and is_ref(hret)):
            die(f"{patch['id']}: {a['helper']} returns {hret}, method returns {t}")
        code.append(f"    {move_result_op(t)} v0")
        if is_ref(t) and hret != t:
            code.append(f"    check-cast v0, {t}")
        locals_ = 2 + used
    else:
        code = const_lines(a["value"], t)
        locals_ = 1
    return [header, f"    .locals {locals_}", *body, "", *code, "", f"    {return_op(t)} v0", ".end method"]


def renamed_header(method):
    flags = [f for f in method.flags if f not in ACCESS and f not in ("bridge", "varargs")]
    return ".method private " + " ".join(flags + [method.name + WRAP_SUFFIX + method.proto]).replace("  ", " ")


def gen_wrap(patch, method):
    a = patch["action"]
    pid = patch["id"]
    t = method.ret
    if method.name.startswith("<"):
        die(f"{pid}: constructors can't be wrapped")
    if method.cls.method(method.name + WRAP_SUFFIX, method.proto):
        die(f"{pid}: {method.sig} is already wrapped")
    flags = [f for f in method.flags if f not in ("synchronized", "declared-synchronized")]
    header = ".method " + " ".join(flags + [method.sig])
    # v0-v1 result, v2 exception, v3.. helper arguments, then the parameters.
    block = 3
    code = []
    if "enter" in a:
        code.append(f"    invoke-static {{}}, {a['enter']}")
    for i, g in enumerate(a.get("guard", [])):
        lines, hret, _ = call_helper(pid, g["helper"], g["pass"], method, block)
        if hret != "Z":
            die(f"{pid}: guard {g['helper']} must return a boolean")
        code += lines + ["    move-result v0", f"    if-nez v0, :xcam_go_{i}"]
        # A guard that answers false skips the original: void, or zero, false or null.
        if t == "V":
            code.append("    return-void")
        else:
            zero = "const-wide/16 v0, 0x0" if width(t) == 2 else "const/4 v0, 0x0"
            code += [f"    {zero}", f"    {return_op(t)} v0"]
        code.append(f"    :xcam_go_{i}")
    for b in a.get("before", []):
        lines, hret, _ = call_helper(pid, b["helper"], b["pass"], method, block)
        if "set" not in b:
            code += lines
            continue
        target = b["set"]
        ttype = token_type(target, method)
        if not assignable(hret, ttype) and not (is_ref(ttype) and is_ref(hret)):
            die(f"{pid}: {b['helper']} returns {hret}, {target} is {ttype}")
        code += lines
        code.append(f"    {move_result_op(ttype)} v0")
        if is_ref(ttype) and hret != ttype:
            code.append(f"    check-cast v0, {ttype}")
        code.append(f"    {move_op(ttype)} {param_reg(method, target)}, v0")
    n = param_count(method)
    kind = "invoke-static" if method.static else "invoke-direct"
    call = f"{method.cls.desc}->{method.name}{WRAP_SUFFIX}{method.proto}"
    if n == 0:
        invoke = f"    {kind} {{}}, {call}"
    else:
        invoke = f"    {kind}/range {{p0 .. p{n - 1}}}, {call}"
    if "exit" in a:
        code.append("    :xcam_try_start")
    code.append(invoke)
    if t != "V":
        code.append(f"    {move_result_op(t)} v0")
    if "exit" in a:
        code += ["    :xcam_try_end", "    .catchall {:xcam_try_start .. :xcam_try_end} :xcam_catch",
                 f"    invoke-static {{}}, {a['exit']}"]
    used = 0
    for af in a.get("after", []):
        lines, hret, u = call_helper(pid, af["helper"], af["pass"], method, block)
        used = max(used, u)
        code += lines
        if t != "V":
            if hret != t and not (is_ref(t) and is_ref(hret)):
                die(f"{pid}: {af['helper']} returns {hret}, method returns {t}")
            code.append(f"    {move_result_op(t)} v0")
            if is_ref(t) and hret != t:
                code.append(f"    check-cast v0, {t}")
    for b in a.get("before", []) + a.get("guard", []):
        used = max(used, sum(width(token_type(x, method)) for x in b["pass"]))
    code.append(f"    {return_op(t)}" + ("" if t == "V" else " v0"))
    if "exit" in a:
        code += ["    :xcam_catch", "    move-exception v2", f"    invoke-static {{}}, {a['exit']}", "    throw v2"]
    wrapper = [header, f"    .locals {block + used}", *keep_annotations(method), "", *code, ".end method"]
    original = method.cls.lines[method.start:method.end + 1]
    original[0] = renamed_header(method)
    return wrapper + [""] + original


# ---------------------------------------------------------------------------------------
# Redirects


def redirect(tree, patch, report):
    a = patch["action"]
    total = 0
    for frm, helper_name in a["calls"].items():
        recv, name, proto = split_ref(frm)
        params, ret = split_proto(proto)
        to = f"{a['helper']}->{helper_name}({recv}{''.join(params)}){ret}"
        ctor = name == "<init>"
        kind = "direct" if ctor else "(?:virtual|interface)"
        pat = re.compile(r"^(\s*)invoke-" + kind + r"(/range)? (\{[^}]*\}), " + re.escape(frm) + "$")
        count = 0
        for desc, path in tree.all_paths():
            with open(path, encoding="utf-8") as fh:
                if frm not in fh.read():
                    continue
            cls = tree.get(desc)
            for i in reversed(range(len(cls.lines))):
                m = pat.match(cls.lines[i])
                if not m:
                    continue
                call = f"{m.group(1)}invoke-static{m.group(2) or ''} {m.group(3)}, {to}"
                if ctor:
                    # The new object is usable only once constructed: the helper is called
                    # after the constructor, with the same registers.
                    cls.lines.insert(i + 1, call)
                else:
                    cls.lines[i] = call
                cls.dirty = True
                count += 1
            if cls.dirty:
                cls.parse()
        if count == 0:
            die(f"{patch['id']}: no calls to {frm}")
        report["redirects"].append({"patch": patch["id"], "from": frm, "to": to, "count": count, "after": ctor})
        total += count
    return total


# ---------------------------------------------------------------------------------------
# Fields


def add_fields(tree, patch, report):
    a = patch["action"]
    cls = tree.get(a["class"])
    # Before the first method: a field may be followed by its annotations and .end field.
    at = next((i for i, ln in enumerate(cls.lines) if ln.startswith(".method ")), len(cls.lines))
    for decl in a["add"]:
        name = decl.split()[-1].split(":")[0]
        if any(ln.startswith(".field ") and ln.split()[-1].split(":")[0] == name for ln in cls.lines):
            die(f"{patch['id']}: {a['class']} already has a field {name}")
    cls.lines[at:at] = a["add"] + [""]
    cls.dirty = True
    cls.parse()
    report.setdefault("fields", []).append({"patch": patch["id"], "class": a["class"], "fields": a["add"]})
    return len(a["add"])


# ---------------------------------------------------------------------------------------


def apply(tree_dir, patches_file, report_file):
    with open(patches_file, encoding="utf-8") as f:
        spec = json.load(f)
    tree = Tree(tree_dir)
    report = {"build": spec["build"], "methods": [], "redirects": []}
    for patch in spec["patches"]:
        pid = patch["id"]
        check_fingerprints(tree, patch)
        kind = patch["action"]["type"]
        if kind == "redirect":
            n = redirect(tree, patch, report)
            print(f"  {pid}: {n} call sites")
            continue
        if kind == "fields":
            n = add_fields(tree, patch, report)
            print(f"  {pid}: {n} fields in {patch['action']['class']}")
            continue
        methods = find_methods(tree, patch)
        for m in methods:
            cls = m.cls
            lines = gen_return(patch, m) if kind == "return" else gen_wrap(patch, m)
            sig = m.sig
            cls.replace(m, lines)
            report["methods"].append({"patch": pid, "class": cls.desc, "method": sig, "smali": lines})
        print(f"  {pid}: {', '.join(m.cls.desc + '->' + m.sig for m in methods)}")
    tree.save()
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)


def normalise(lines):
    """Instructions as baksmali writes them back: /jumbo depends on the dex's string count, and
    labels are renamed (:xcam_try_start becomes :try_start_0), so both are made uniform."""
    out, labels = [], {}

    def label(m):
        return labels.setdefault(m.group(0), f":L{len(labels)}")

    for ln in lines:
        s = ln.strip()
        if not s or s.startswith((".line", ".param", ".end param")):
            continue
        s = s.replace("const-string/jumbo ", "const-string ")
        out.append(re.sub(r"(?<![\w$])(:\w+)", label, s))
    return out


def verify(tree_dir, report_file):
    with open(report_file, encoding="utf-8") as f:
        report = json.load(f)
    tree = Tree(tree_dir)
    bad = 0
    for e in report["methods"]:
        cls = tree.get(e["class"])
        want = normalise(e["smali"])
        # Wrapped methods are reported with the renamed original after them.
        n = want.index(".end method") + 1
        got = []
        name_proto = re.match(r"\.method (?:[\w-]+ )*(\S+)$", want[0]).group(1)
        for m in cls.methods:
            if m.sig == name_proto:
                got = normalise(m.body.split("\n"))
        # apktool writes .locals as .registers on rebuild? Compare instructions only.
        strip = lambda ls: [x for x in ls if not x.startswith((".locals", ".registers"))]
        ok = strip(got) == strip(want[:n])
        if n < len(want):
            orig = re.match(r"\.method (?:[\w-]+ )*(\S+)$", want[n]).group(1)
            ok = ok and any(m.sig == orig for m in cls.methods)
        print(f"  {'ok ' if ok else 'BAD'} {e['patch']}: {e['class']}->{e['method']}")
        bad += not ok
    for r in report["redirects"]:
        left = to = 0
        helper_pkg = r["to"].split("->")[0].rsplit("/", 1)[0] + "/"
        for desc, path in tree.all_paths():
            if desc.startswith(helper_pkg):
                continue  # The helpers call the real framework method.
            with open(path, encoding="utf-8") as fh:
                s = fh.read()
            left += s.count(", " + r["from"] + "\n")
            to += s.count(", " + r["to"] + "\n")
        if r.get("after"):
            ok = to == r["count"] and left >= to
        else:
            ok = left == 0 and to == r["count"]
        print(f"  {'ok ' if ok else 'BAD'} {r['patch']}: {r['to']} x{to} (left: {left})")
        bad += not ok
    for e in report.get("fields", []):
        have = {ln.strip() for ln in tree.get(e["class"]).lines}
        ok = all(decl.strip() in have for decl in e["fields"])
        print(f"  {'ok ' if ok else 'BAD'} {e['patch']}: {len(e['fields'])} fields in {e['class']}")
        bad += not ok
    if bad:
        die(f"{bad} patch(es) missing from the rebuilt APK")


DEX = re.compile(r"classes\d*\.dex")
SIGNATURE = re.compile(r"META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)")


def assemble(original, rebuilt, helper_dex, out):
    """The original APK with only its code replaced: every entry is copied from the original
    (resources, libraries and assets never touch the disk, whose case-insensitivity can merge
    Xiaomi's obfuscated resource names), the classes*.dex come from the rebuilt APK, and the
    helper dex is added after them. The original signature is dropped."""
    import zipfile
    with zipfile.ZipFile(original) as src, zipfile.ZipFile(rebuilt) as new, \
            zipfile.ZipFile(out, "w") as dst:
        new_dex = sorted((n for n in new.namelist() if DEX.fullmatch(n)), key=lambda n: (len(n), n))
        old_dex = [i for i in src.infolist() if DEX.fullmatch(i.filename)]
        if len(new_dex) != len(old_dex):
            die(f"rebuilt APK has {len(new_dex)} dex files, the original {len(old_dex)}")
        dex_compress = old_dex[0].compress_type
        written_dex = False
        for info in src.infolist():
            name = info.filename
            if SIGNATURE.fullmatch(name):
                continue
            if DEX.fullmatch(name):
                if not written_dex:
                    for n in new_dex:
                        dst.writestr(zipfile.ZipInfo(n, info.date_time), new.read(n), dex_compress)
                    helper = f"classes{len(new_dex) + 1}.dex"
                    with open(helper_dex, "rb") as f:
                        dst.writestr(zipfile.ZipInfo(helper, info.date_time), f.read(), dex_compress)
                    written_dex = True
                continue
            dst.writestr(info, src.read(name), info.compress_type)
    with zipfile.ZipFile(original) as src, zipfile.ZipFile(out) as dst:
        want = {n for n in src.namelist() if not SIGNATURE.fullmatch(n)} | {helper}
        if set(dst.namelist()) != want:
            die(f"assembled APK entries differ from the original: {sorted(set(dst.namelist()) ^ want)[:5]}")
    print(f"  {len(new_dex)} dex files from the rebuild, helpers as {helper}, other entries from the original")


if __name__ == "__main__":
    if len(sys.argv) == 5 and sys.argv[1] == "apply":
        apply(*sys.argv[2:])
    elif len(sys.argv) == 4 and sys.argv[1] == "verify":
        verify(*sys.argv[2:])
    elif len(sys.argv) == 6 and sys.argv[1] == "assemble":
        assemble(*sys.argv[2:])
    else:
        sys.exit(__doc__)
