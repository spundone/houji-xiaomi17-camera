#!/usr/bin/env bash
# Patches your own copy of Xiaomi's camera so a ROM can ship it without KernelSU or LSPosed.
#
#   aosp/tools/patch-apk.sh MiuiCamera.apk OUT.apk [--work DIR]
#
# 1. Checks the APK's SHA-256 against module/known-apks.sha256 and picks aosp/patches/<build>/.
# 2. Builds the helper dex from aosp/helpers/ (and the AudioParaManger stand-in from lsposed/).
# 3. Decodes the APK with apktool (code only, resources untouched), applies patches.json with
#    xcampatch.py, rebuilds, adds the helper dex as one more classesN.dex, checks every patch
#    is in the rebuilt APK, and zipaligns it.
#
# OUT.apk is unsigned: the ROM build signs it with the platform key (certificate: "platform").
# To try it outside a ROM build, sign it yourself with apksigner.
#
# Needs JDK 11+, Python 3, an Android SDK (ANDROID_HOME) with build-tools and a platform 34 or
# newer, apktool (tested with 3.0.3; on PATH, or APKTOOL_JAR pointing at the jar), and about
# 1 GB of free disk.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
KIT="$ROOT/aosp"

in="" out="" work=""
while [ $# -gt 0 ]; do
    case "$1" in
        --work) work="$2"; shift ;;
        -h|--help) awk 'NR > 1 && !/^#/ { exit } NR > 1 { sub(/^# ?/, ""); print }' "$0"; exit 0 ;;
        *) if [ -z "$in" ]; then in="$1"; elif [ -z "$out" ]; then out="$1"; else echo "unexpected argument: $1" >&2; exit 2; fi ;;
    esac
    shift
done
[ -n "$in" ] && [ -n "$out" ] || { echo "usage: $0 MiuiCamera.apk OUT.apk [--work DIR]" >&2; exit 2; }
[ -f "$in" ] || { echo "no such file: $in" >&2; exit 1; }

die() { echo "patch-apk: $*" >&2; exit 1; }

# --- Which build is this? ---
sum=$(shasum -a 256 "$in" 2>/dev/null | cut -d' ' -f1 || sha256sum "$in" | cut -d' ' -f1)
line=$(grep "^$sum " "$ROOT/module/known-apks.sha256" || true)
[ -n "$line" ] || die "$(basename "$in") ($sum) is not a known camera build; see module/known-apks.sha256"
build=$(echo "$line" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1)
patches="$KIT/patches/$build/patches.json"
[ -f "$patches" ] || die "camera $build is known to the KernelSU module but has no AOSP patches yet ($patches)"
echo "== MiuiCamera $build"

# --- Tools ---
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] && [ -x "$BT/d8" ] || die "no Android build-tools under $SDK"
ANDROID_JAR=$(find "$SDK/platforms" -maxdepth 2 -name android.jar 2>/dev/null | sort -V | tail -1)
[ -f "$ANDROID_JAR" ] || die "no platform android.jar under $SDK/platforms"
if [ -n "${APKTOOL_JAR:-}" ]; then
    apktool() { java -Xmx6g -jar "$APKTOOL_JAR" "$@"; }
elif ! command -v apktool >/dev/null; then
    jar=$(ls "$HOME"/.local/share/apktool/apktool_*.jar 2>/dev/null | sort -V | tail -1 || true)
    [ -n "$jar" ] || die "apktool not found: install it, or set APKTOOL_JAR"
    apktool() { java -Xmx6g -jar "$jar" "$@"; }
fi

if [ -z "$work" ]; then
    work=$(mktemp -d "${TMPDIR:-/tmp}/xcam17-patch.XXXXXX")
    trap 'rm -rf "$work"' EXIT
else
    rm -rf "$work" && mkdir -p "$work"
fi

# --- Helper dex ---
echo "== Helpers"
mkdir -p "$work/helpers/classes" "$work/helpers/dex"
{ find "$KIT/helpers/src" -name '*.java'; find "$ROOT/lsposed/src/android" -name '*.java'; } > "$work/helpers/sources"
javac --release 11 -nowarn -Xlint:-options -d "$work/helpers/classes" -cp "$ANDROID_JAR" @"$work/helpers/sources"
find "$work/helpers/classes" -name '*.class' -print0 | xargs -0 "$BT/d8" --release --min-api 29 \
    --lib "$ANDROID_JAR" --output "$work/helpers/dex"
echo "   $(find "$work/helpers/classes" -name '*.class' | wc -l | tr -d ' ') classes"

# --- Decode, patch, rebuild ---
echo "== Decode"
apktool d -q -f -r -o "$work/decoded" "$in"
echo "== Patch ($patches)"
python3 "$KIT/tools/xcampatch.py" apply "$work/decoded" "$patches" "$work/report.json"
echo "== Rebuild"
apktool b -q -o "$work/rebuilt.apk" "$work/decoded"
rm -rf "$work/decoded"

# The helpers go in as one more dex; the camera's class loader reads every classesN.dex.
n=$(unzip -Z1 "$work/rebuilt.apk" | grep -cE '^classes[0-9]*\.dex$')
cp "$work/helpers/dex/classes.dex" "$work/helpers/classes$((n + 1)).dex"
(cd "$work/helpers" && zip -q -0 "$work/rebuilt.apk" "classes$((n + 1)).dex")
echo "   helpers added as classes$((n + 1)).dex"

# --- Check the rebuilt APK ---
echo "== Verify"
apktool d -q -f -r --no-assets -o "$work/check" "$work/rebuilt.apk"
python3 "$KIT/tools/xcampatch.py" verify "$work/check" "$work/report.json"
helper_classes=$("$BT/dexdump" "$work/helpers/classes$((n + 1)).dex" 2>/dev/null | grep -cE "^ *Class descriptor" || true)
[ -d "$work/check/smali_classes$((n + 1))/dev/spundone/xcam17/aosp" ] || die "helper dex missing from the rebuilt APK"
echo "   helper dex: $helper_classes classes"

rm -rf "$work/check"

"$BT/zipalign" -f -p 4 "$work/rebuilt.apk" "$out"
"$BT/zipalign" -c -p 4 "$out" >/dev/null
echo "== $out (unsigned; the ROM build signs it with the platform key)"
