#!/usr/bin/env bash
# Local-only KernelSU module zips that need no LSPosed: they carry the camera APK patched by the
# AOSP kit (feature/aosp-kit: aosp/tools/patch-apk.sh), signed here. They contain Xiaomi's APK,
# so never publish them.
#
#   scripts/build-kit-zip.sh PATCHED_UNSIGNED_APK [--leica LEICA_PORT_APK] [--gallery MIUI_GALLERY_MODULE_ZIP]
#
# Run scripts/build.sh first (it builds the module files and icon overlay this reuses). The
# module mounts the camera and its icon overlay itself, so no module manager mount is needed.
# With --leica, the Leica port's files are carried in the zip as well (then named
# -standalone): nothing has to be in Download and no mount module is needed. With
# --gallery, a second zip also installs the MIUI Gallery Global module (which, being a plain
# module, does need the mount module).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
OUT="$ROOT/build"
DIST="$ROOT/dist"
patched="" leica="" gallery=""
while [ $# -gt 0 ]; do
    case "$1" in
        --leica) leica="$2"; shift ;;
        --gallery) gallery="$2"; shift ;;
        *) if [ -z "$patched" ]; then patched="$1"; else gallery="$1"; fi ;;
    esac
    shift
done
[ -n "$patched" ] || { echo "usage: $0 PATCHED_UNSIGNED_APK [--leica APK] [--gallery ZIP]" >&2; exit 2; }
[ -d "$OUT/module" ] || { echo "run scripts/build.sh first" >&2; exit 1; }

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
if [ -n "${XCAM17_KEYSTORE:-}" ]; then
    sign=(--ks "$XCAM17_KEYSTORE" --ks-pass env:XCAM17_KEYSTORE_PASS --ks-key-alias "$XCAM17_KEY_ALIAS")
else
    sign=(--ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android)
fi

echo "== Patched camera APK"
camera="$OUT/MiuiCamera-kit.apk"
"$BT/apksigner" sign "${sign[@]}" --v4-signing-enabled false --out "$camera" "$patched" 2> >(grep -v '^WARNING' >&2)
"$BT/apksigner" verify "$camera"

echo "== KernelSU-only module $VERSION"
mod="$OUT/kit-module"
rm -rf "$mod" && cp -R "$OUT/module" "$mod"
rm -f "$mod/xcam17-lsposed.apk"
# The icon overlay is mounted by post-fs-data.sh (in /product/overlay), not the module manager.
mkdir -p "$mod/overlay"
mv "$mod/system/product/overlay/XCam17Icon/XCam17Icon.apk" "$mod/overlay/XCam17Icon.apk"
rm -rf "$mod/system"
cp "$camera" "$mod/MiuiCamera.apk"
echo "$(shasum -a 256 "$camera" | cut -d' ' -f1)  patched-by-aosp-kit" >> "$mod/known-apks.sha256"
touch "$mod/kit"
name="KernelSU only"
kind="ksu-only"
if [ -n "$leica" ]; then
    echo "== Leica port files from $(basename "$leica")"
    unzip -q -l "$leica" assets/legend18/provenance.json >/dev/null \
        || { echo "$leica is not the Leica port (no assets/legend18)" >&2; exit 1; }
    mkdir -p "$mod/leica/lib"
    unzip -o -q "$leica" classes11.dex classes12.dex 'assets/legend18/*' 'assets/leica-v4/*' \
        'assets/leica-v43-ui/*' 'assets/leica_filter_param_*' -d "$mod/leica"
    # The port's own native libraries: those the camera APK doesn't have, and the C++ runtime
    # its helper programs load from the same folder.
    have=$(unzip -Z1 "$camera" 'lib/arm64-v8a/*')
    for lib in $(unzip -Z1 "$leica" 'lib/arm64-v8a/*' | grep '\.so$'); do
        [ "$lib" != lib/arm64-v8a/libc++_shared.so ] && echo "$have" | grep -qx "$lib" && continue
        unzip -o -q -p "$leica" "$lib" > "$mod/leica/lib/${lib##*/}"
    done
    [ -f "$mod/leica/classes11.dex" ] && [ -f "$mod/leica/lib/libmonopan_runner.so" ] \
        || { echo "Leica port files incomplete" >&2; exit 1; }
    echo "   $(du -sh "$mod/leica" | cut -f1)"
    name="standalone"
    kind="standalone"
fi
needs="No LSPosed needed"
[ -n "$leica" ] && needs="No LSPosed, mount module or other downloads needed; Leica Essential included"
sed -i '' -e "s/^name=.*/name=Xiaomi 17 Camera for houji ($name)/" \
    -e "s/^description=.*/description=Xiaomi's camera 6.8.002090.2, patched by the AOSP kit, in place of MiuiCamera. $needs. Local build: carries Xiaomi's APK./" \
    "$mod/module.prop"
zip_ksu="$DIST/xiaomi17_camera-$VERSION-$kind.zip"
rm -f "$zip_ksu"
(cd "$mod" && zip -q -r -X -0 "$zip_ksu" .)
echo "   $zip_ksu (contains Xiaomi's APK: do not publish)"

if [ -n "$gallery" ]; then
    mkdir -p "$mod/gallery"
    cp "$gallery" "$mod/gallery/MIUIGallery.zip"
    sed -i '' -e "s/^name=.*/name=Xiaomi 17 Camera for houji ($name, with MIUI Gallery)/" "$mod/module.prop"
    zip_gallery="$DIST/xiaomi17_camera-$VERSION-$kind-gallery.zip"
    rm -f "$zip_gallery"
    (cd "$mod" && zip -q -r -X -0 "$zip_gallery" .)
    echo "   $zip_gallery (contains Xiaomi's APKs: do not publish)"
fi
