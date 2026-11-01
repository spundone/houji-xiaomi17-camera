#!/usr/bin/env python3
"""Writes patches.json, the patch list for camera 6.8.002090.2, next to this script.

The list is generated so the obfuscated names come from one place per hook and every entry
says which LSPosed hook it replaces. Run it after editing; commit both files.
"""

import json
import os

H = "Ldev/spundone/xcam17/aosp/"
COMMON = "L\u8e48\u8e44\u8e46\u8e05\u8e46\u8e42\u8e05\u8e4f\u8e4e\u8e5d\u8e42\u8e48\u8e4e\u8e05" \
         "\u8e48\u8e44\u8e46\u8e46\u8e44\u8e45\u8e05\u8e68\u8e44\u8e46\u8e46\u8e44\u8e45;"
# com.mi.device.xiaomi.CommonFlagship, as stored in the APK
FLAGSHIP = "L\u6671\u667d\u667f\u663c\u667f\u667b\u663c\u6676\u6677\u6664\u667b\u6671\u6677\u663c" \
           "\u666a\u667b\u6673\u667d\u667f\u667b\u663c\u6651\u667d\u667f\u667f\u667d\u667c" \
           "\u6654\u667e\u6673\u6675\u6661\u667a\u667b\u6662;"
PROFILES = [COMMON, FLAGSHIP]
PROFILE_CHECK = {"class": FLAGSHIP, "has": [".super " + COMMON]}

patches = []


def add(pid, hook, effort, find, action, check=None):
    p = {"id": pid, "hook": hook, "effort": effort, "find": find, "action": action}
    if check:
        p["check"] = check
    patches.append(p)


def flag(method, ret, value, what, helper=None, helper_pass=None, wrap_after=None):
    """A DeviceFeatures flag: a constant on every profile class that defines it."""
    find = {"classes": PROFILES, "name": method, "proto": "()" + ret, "static": False, "count": "+"}
    if wrap_after:
        action = {"type": "wrap", "after": [wrap_after]}
    elif helper:
        action = {"type": "return", "helper": helper, "pass": helper_pass or []}
    else:
        action = {"type": "return", "value": value}
    add("flag-" + method, "DeviceFeatures " + method + ": " + what, "low", find, action, [PROFILE_CHECK])


# --- Low effort -------------------------------------------------------------------------

add("client-name", "Entry.hookClientName", "low", None, {
    "type": "redirect", "helper": H + "ClientName;",
    "calls": {
        "Landroid/hardware/camera2/CameraDevice;->createCaptureRequest(I)"
        "Landroid/hardware/camera2/CaptureRequest$Builder;": "createCaptureRequest",
        "Landroid/hardware/camera2/CameraDevice;->createReprocessCaptureRequest("
        "Landroid/hardware/camera2/TotalCaptureResult;)Landroid/hardware/camera2/CaptureRequest$Builder;":
            "createReprocessCaptureRequest",
    }})

add("annexb-video", "AnnexBVideo", "low", None, {
    "type": "redirect", "helper": H + "AnnexBVideo;",
    "calls": {
        "Landroid/media/MediaCodec;->configure(Landroid/media/MediaFormat;Landroid/view/Surface;"
        "Landroid/media/MediaCrypto;I)V": "configure",
        "Landroid/media/MediaCodec;->setParameters(Landroid/os/Bundle;)V": "setParameters",
    }})

for name, proto in [
        ("init", "(Lcom/android/camera/saliencychecker/data/SaliencyInitInputObject;)Z"),
        ("process", "(Lcom/android/camera/saliencychecker/data/SaliencyProcessInputObject;"
                    "Lcom/android/camera/saliencychecker/data/SaliencyProcessOutputObject;)Z"),
        ("hasInit", "()Z")]:
    add("saliency-off-" + name, "SaliencyOff", "low",
        {"class": "Lcom/android/camera/saliencychecker/SaliencyChecker;", "name": name, "proto": proto},
        {"type": "return", "value": False})

add("consent-region", "Entry.hookConsentRegion", "low",
    {"class": "LTh/c;", "name": "a", "proto": "()LTh/c$c;", "static": True, "has": ['"KR"']},
    {"type": "return", "field": "LTh/c$c;->c:LTh/c$c;"},
    [{"class": "LTh/c$c;", "has": ['"CN"', '"KR"', '"GLOBAL"', 'sput-object v2, LTh/c$c;->c:LTh/c$c;']}])

add("missing-profile", "Entry.hookMissingProfile", "low",
    {"class": "LEu/f2;", "name": "l", "proto": "()" + COMMON, "static": True,
     "has": ["Landroid/os/Build;->MANUFACTURER", "Ljava/lang/Class;->newInstance()",
             "sput-object v0, LEu/f2;->a:Ljava/lang/Boolean;"]},
    {"type": "wrap", "after": [{
        "helper": H + "Profile;->afterLoad(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;"
                  "Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
        "pass": ["result", "class:LEu/f2;", "str:a", "str:b", "class:" + FLAGSHIP]}]},
    [PROFILE_CHECK])

add("marketing-name", "Entry.hookMarketingName", "low",
    {"class": FLAGSHIP, "name": "d", "proto": "()Landroid/util/SparseArray;", "static": False},
    {"type": "return", "helper": H + "Profile;->marketingName()Landroid/util/SparseArray;"},
    [PROFILE_CHECK])

flag("q0", "I", 2, "style version 2: Filter parameters panel with Noise (grain)")
flag("W3", "Z", True, "front/rear camera switch while recording")
flag("P1", "Z", True, "Dolby Vision toggle in the viewfinder")
flag("d4", "Z", True, "take photos while recording")
flag("C3", "Z", True, "Motion tracking focus in Pro photo")
flag("f3", "Z", True, "Import LUT for Pro video Log")
flag("t1", "Z", True, "Ultra HD toggle in Photo mode")
flag("W0", "Ljava/util/ArrayList;", None, "front fill light allowed at 60 fps video",
     helper=H + "Values;->intList(Ljava/lang/String;)Ljava/util/ArrayList;", helper_pass=["str:30,60"])
flag("w0", "[I", None, "50MP lens roles: main, telephoto, ultra-wide",
     wrap_after={"helper": H + "Values;->appendInts([ILjava/lang/String;)[I", "pass": ["result", "str:0,20,21"]})
flag("v0", "Ljava/lang/String;", "4:8192x6144", "main camera 50MP size")
flag("y0", "Ljava/lang/String;", "4:8160x6144", "ultra-wide 50MP size")
flag("u0", "Ljava/lang/String;", "4:8160x6144", "telephoto 50MP size")
flag("Z3", "Z", True, "portrait zoom toggles from the profile table")
flag("c2", "Z", True, "Street (Fastshot) mode")
flag("G2", "Z", True, "Dual video")
flag("i3", "Z", True, "macro supported")
flag("Z0", "Ljava/lang/String;", "tele", "macro on the telephoto")
flag("e2", "Z", True, "label the macro toggle Super macro")
flag("j3", "Z", True, "macro in mode 172 as well")
flag("K2", "Z", True, "macro in the fast-mode config list")
flag("R0", "[I", None, "filter type 16: the 2024 flagship filter sets",
     helper=H + "Values;->ints(Ljava/lang/String;)[I", helper_pass=["str:16"])
flag("S0", "I", 6, "filter version 6: front video filters from houji's firmware")

# --- Low to medium: small return-value edits ---------------------------------------------

add("street-mode", "Entry.hookStreetMode", "low",
    {"class": "Lcom/android/camera/features/mode/street/StreetModuleEntry;", "name": "support", "proto": "()Z"},
    {"type": "return", "value": True})

add("lens-sat-check", "Entry.hookVideoLensSwitching (U1)", "medium",
    {"class": "Lcom/android/camera/data/data/i;", "name": "U1", "proto": "(ILjava/lang/String;)Z",
     "static": True, "has": ['"getVideoSatSupportedQualities  = "']},
    {"type": "wrap", "enter": H + "VideoLens;->satEnter()V", "exit": H + "VideoLens;->satExit()V",
     "after": [{"helper": H + "VideoLens;->satResult(ZLjava/lang/String;)Z", "pass": ["result", "arg1"]}]})
for cls, name, has in [("Ls2/a;", "o", []), ("Ls2/b;", "n", ['"pref_hlg_video_mode_key"'])]:
    add("lens-hdr-" + cls[1:-1].replace("/", "."), "Entry.hookVideoLensSwitching (HDR10)", "medium",
        {"class": cls, "name": name, "proto": "()Z", "has": has},
        {"type": "wrap", "after": [{"helper": H + "VideoLens;->hdrInsideSatCheck(Z)Z", "pass": ["result"]}]})
add("lens-dolby", "Entry.hookVideoLensSwitching (Dolby)", "medium",
    {"class": "Ls2/c;", "name": "n", "proto": "(ILjava/lang/String;)Z", "has": ['"getDolbyConfig: "']},
    {"type": "wrap", "after": [{"helper": H + "VideoLens;->dolbyInsideSatCheck(ZI)Z", "pass": ["result", "arg0"]}]})

add("front-dolby-4k", "Entry.hookFrontDolby4k", "medium",
    {"class": "Ll9/h;", "name": "g1", "proto": "(IIILl9/g;)Z", "static": True,
     "has": ['"isCurrentQualitySupportHdr10 AVAILABLE_CONFIGURATIONS is not defined"']},
    {"type": "wrap", "after": [{"helper": H + "VideoLens;->dolby4k30(ZIII)Z",
                                "pass": ["result", "arg0", "arg1", "arg2"]}]})

add("natural-style", "NaturalStyle", "medium",
    {"class": "Ll9/g;", "name": "J0", "proto": "(I)Z", "has": ["shr-int/2addr"]},
    {"type": "wrap", "after": [{"helper": H + "CameraCaps;->styleBit(ZI)Z", "pass": ["result", "arg0"]}]})

add("open-gate", "OpenGate", "medium",
    {"class": "Ll9/h;", "name": "L4", "proto": "(Ll9/g;)Z", "static": True},
    {"type": "wrap", "after": [{"helper": H + "CameraCaps;->openGate(ZLjava/lang/Object;Ljava/lang/String;)Z",
                                "pass": ["result", "arg0", "str:d"]}]},
    [{"class": "Ll9/g;", "has": [".field public final d:Landroid/hardware/camera2/CameraCharacteristics;"]}])

add("tele-fallback-default", "TeleFallback (default)", "medium",
    {"class": "LXh/a;", "name": "h", "proto": "(Ljava/lang/String;Z)Z",
     "has": ["Landroid/content/SharedPreferences;->getBoolean"]},
    {"type": "wrap", "before": [{"helper": H + "Settings;->booleanDefault(Ljava/lang/String;Z)Z",
                                 "pass": ["arg0", "arg1"], "set": "arg1"}]})
add("tele-fallback-tag", "TeleFallback (tag check)", "medium",
    {"class": "Ll9/g;", "name": "S0", "proto": "(Ljava/lang/String;)Z", "has": ["Ljava/util/HashMap;->containsKey"]},
    {"type": "wrap", "after": [{"helper": H + "CameraCaps;->tagAvailable(ZLjava/lang/String;)Z",
                                "pass": ["result", "arg0"]}]})

add("street-focus", "StreetFocus", "medium",
    {"class": "Lr2/b0;", "name": "o", "proto": "()V",
     "has": ['"com.xiaomi.sensor.info.depthOfField invalid ,used default value "']},
    {"type": "wrap", "after": [{"helper": H + "Modes;->afterDepthOfField(Ljava/lang/Object;Ljava/lang/String;)V",
                                "pass": ["this", "str:c"]}]},
    [{"class": "Lr2/b0;", "has": [".field public c:[F"]}])

add("video-caps-check", "VideoCaps (quality check)", "medium",
    {"class": "Lr2/g0;", "name": "C", "proto": "(Lr2/r1$a;Ljava/util/ArrayList;ILl9/g;I)V", "has": ['"8,60"']},
    {"type": "wrap", "enter": H + "CameraCaps;->qualityEnter()V", "exit": H + "CameraCaps;->qualityExit()V"})
add("video-caps-dolby", "VideoCaps (all-scenes Dolby)", "medium",
    {"class": "Ll9/h;", "name": "f2", "proto": "(Ll9/g;)Z", "static": True},
    {"type": "wrap", "after": [{"helper": H + "CameraCaps;->allScenesDolby(Z)Z", "pass": ["result"]}]})

add("short-video", "ShortVideo", "medium",
    {"class": "Lcom/android/camera/module/t;", "name": "getOperatingMode", "proto": "()I"},
    {"type": "wrap", "after": [{"helper": H + "Modes;->operatingMode(ILjava/lang/Object;Ljava/lang/String;)I",
                                "pass": ["result", "this", "str:mOperatingMode"]}]},
    [{"class": "Lcom/android/camera/module/t;", "has": [".field protected mOperatingMode:I"]}])

# --- Medium: helpers with more logic -------------------------------------------------------

add("profile-exposure", "ProfileTables (Pro shortest shutter)", "medium",
    {"classes": PROFILES, "name": "Q0", "proto": "()Ljava/lang/String;", "static": False, "count": "+"},
    {"type": "wrap", "after": [{"helper": H + "Tables;->exposureRanges(Ljava/lang/String;)Ljava/lang/String;",
                                "pass": ["result"]}]}, [PROFILE_CHECK])
add("profile-zoom-toggles", "ProfileTables (portrait 1x, 2.6x)", "medium",
    {"classes": PROFILES, "name": "X0", "proto": "()Landroid/util/SparseArray;", "static": False, "count": "+"},
    {"type": "wrap", "after": [{"helper": H + "Tables;->zoomToggles(Landroid/util/SparseArray;)Landroid/util/SparseArray;",
                                "pass": ["result"]}]}, [PROFILE_CHECK])
add("profile-street-presets", "ProfileTables (Fastshot presets)", "medium",
    {"classes": PROFILES, "name": "J0", "proto": "()Landroid/util/SparseArray;", "static": False, "count": "+"},
    {"type": "wrap", "after": [{"helper": H + "Tables;->streetPresets(Landroid/util/SparseArray;Ljava/lang/Class;)"
                                          "Landroid/util/SparseArray;",
                                "pass": ["result", "class:LRe/b;"]}]},
    [PROFILE_CHECK, {"class": "LRe/b;", "has": ["constructor <init>()V", "constructor <init>(IFF)V"]}])

add("mutex-rules", "MutexDrop (rule list)", "medium",
    {"class": "Lv3/g;", "name": "b", "proto": "(Lv3/c;)Ljava/util/ArrayList;", "has": ['"findFeatureMutexInfoList: "']},
    {"type": "wrap", "after": [{"helper": H + "Photo;->mutexRules(Ljava/util/ArrayList;Ljava/lang/Object;"
                                          "Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;)Ljava/util/ArrayList;",
                                "pass": ["result", "arg0", "str:a", "class:Lv3/h;", "str:b"]}]},
    [{"class": "Lv3/c;", "has": [".field public final a:I"]}, {"class": "Lv3/h;", "has": [".field public final b:I"]}])
add("mutex-style", "MutexDrop (Style kept enabled)", "medium",
    {"class": "Lr2/m;", "name": "v", "proto": "(IZ)V", "has": ["Landroid/util/SparseBooleanArray;->put"]},
    {"type": "wrap", "before": [{"helper": H + "Values;->no(Z)Z", "pass": ["arg1"], "set": "arg1"}]})

add("watermark-subimage", "WatermarkSubimage", "medium",
    {"class": "LU8/d;", "name": "f", "proto": "(Lzi/a;ZI)Lzi/f;", "has": ['"processWatermark: start: "']},
    {"type": "wrap", "after": [{"helper": H + "Photo;->afterWatermark(Ljava/lang/Object;Ljava/lang/Object;"
                                          "Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                                "pass": ["result", "arg0", "str:s", "str:t"]}]})

add("video-filter-luts", "VideoFilterLuts", "medium",
    {"class": "Lr2/a;", "name": "getItems", "proto": "()Ljava/util/List;"},
    {"type": "wrap", "after": [{"helper": H + "Video;->filterItems(Ljava/util/List;)Ljava/util/List;",
                                "pass": ["result"]}]})

for name, has in [("L", '"setFpsRange: "'), ("f0", '"setVideoFpsRange: "')]:
    add("fill-light-60-" + name, "FillLight60", "medium",
        {"class": "Ll9/i0;", "name": name, "proto": "(Landroid/util/Range;)V", "has": [has]},
        {"type": "wrap", "before": [{
            "helper": H + "Video;->fillLightRange(Landroid/util/Range;Ljava/lang/Class;Ljava/lang/String;"
                      "Ljava/lang/Class;)Landroid/util/Range;",
            "pass": ["arg0", "class:Lg2/a;", "str:a", "class:Lr2/w;"], "set": "arg0"}]},
        [{"class": "Lg2/a;", "has": [".method public static a()Lr2/n1;"]}])

spec = {
    "build": "6.8.002090.2",
    "sha256": "729b0b193d1684e7c14ed0ca56ba15247d58016c70164329de233b49727a2c48",
    "ported_from": "spundone/houji-xiaomi17-camera v1.7.0 (2f8c709), lsposed/src/dev/spundone/xcam17",
    "patches": patches,
}
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "patches.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(spec, f, ensure_ascii=False, indent=1)
    f.write("\n")
print(f"{out}: {len(patches)} patches")
