#!/usr/bin/env bash
# Builds the LSPosed module APK and the KernelSU module zip into dist/.
#
#   scripts/build.sh                 debug-signed APK, module zip without the camera APK
#   scripts/build.sh --release       requires XCAM17_KEYSTORE / XCAM17_KEYSTORE_PASS / XCAM17_KEY_ALIAS
#   scripts/build.sh --apk FILE      also bundle a camera APK into a local-only zip
#
# The module zip is all in one: it carries the LSPosed module APK (installed and, with Vector,
# enabled by customize.sh) and the adaptive icon overlay (icon/, mounted at /product/overlay).
#
# Needs JDK 11+ and an Android SDK (ANDROID_HOME) with platforms;android-34 and build-tools.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
VERSION_CODE=$(echo "$VERSION" | awk -F. '{ printf "%d", $1 * 10000 + $2 * 100 + $3 }')
DIST="$ROOT/dist"
OUT="$ROOT/build"

release=0
camera_apk=""
while [ $# -gt 0 ]; do
    case "$1" in
        --release) release=1 ;;
        --apk) camera_apk="$2"; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
[ -f "$ANDROID_JAR" ] || { echo "missing $ANDROID_JAR (sdkmanager 'platforms;android-34')" >&2; exit 1; }

rm -rf "$OUT" "$DIST" && mkdir -p "$OUT"/{stubs,classes,dex} "$DIST"

echo "== LSPosed module $VERSION ($VERSION_CODE)"
src="$ROOT/lsposed"
find "$src/stubs" -name '*.java' -print0 | xargs -0 javac --release 11 -nowarn -d "$OUT/stubs" -cp "$ANDROID_JAR"
(cd "$OUT/stubs" && jar cf "$OUT/stubs.jar" .)
find "$src/src" -name '*.java' -print0 | xargs -0 javac --release 11 -Xlint:-options -d "$OUT/classes" -cp "$ANDROID_JAR:$OUT/stubs.jar"
find "$OUT/classes" -name '*.class' -print0 | xargs -0 "$BT/d8" --release --min-api 29 \
    --lib "$ANDROID_JAR" --classpath "$OUT/stubs.jar" --output "$OUT/dex"
"$BT/aapt2" compile --dir "$src/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$src/AndroidManifest.xml" \
    -A "$src/assets" --version-name "$VERSION" --version-code "$VERSION_CODE" --replace-version "$OUT/res.zip"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ "$release" = 1 ]; then
    : "${XCAM17_KEYSTORE:?set XCAM17_KEYSTORE for --release}"
    : "${XCAM17_KEYSTORE_PASS:?set XCAM17_KEYSTORE_PASS for --release}"
    : "${XCAM17_KEY_ALIAS:?set XCAM17_KEY_ALIAS for --release}"
    sign=(--ks "$XCAM17_KEYSTORE" --ks-pass env:XCAM17_KEYSTORE_PASS --ks-key-alias "$XCAM17_KEY_ALIAS")
    suffix=""
else
    debug_ks="$HOME/.android/debug.keystore"
    mkdir -p "$(dirname "$debug_ks")"
    [ -f "$debug_ks" ] || keytool -noprompt -genkeypair -keystore "$debug_ks" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
    sign=(--ks "$debug_ks" --ks-pass pass:android --key-pass pass:android)
    suffix="-debug"
fi
lsposed_apk="$DIST/xcam17-lsposed-$VERSION$suffix.apk"
"$BT/apksigner" sign "${sign[@]}" --v4-signing-enabled false --out "$lsposed_apk" "$OUT/aligned.apk" \
    2> >(grep -v '^WARNING' >&2)
"$BT/apksigner" verify "$lsposed_apk"
echo "   $lsposed_apk"

echo "== Icon overlay $VERSION"
icon="$ROOT/icon"
"$BT/aapt2" compile --dir "$icon/res" -o "$OUT/icon-res.zip"
"$BT/aapt2" link -o "$OUT/icon-unsigned.apk" -I "$ANDROID_JAR" --manifest "$icon/AndroidManifest.xml" \
    --version-name "$VERSION" --version-code "$VERSION_CODE" --replace-version "$OUT/icon-res.zip"
"$BT/zipalign" -f -p 4 "$OUT/icon-unsigned.apk" "$OUT/icon-aligned.apk"
icon_apk="$OUT/XCam17Icon.apk"
"$BT/apksigner" sign "${sign[@]}" --v4-signing-enabled false --out "$icon_apk" "$OUT/icon-aligned.apk" \
    2> >(grep -v '^WARNING' >&2)

echo "== KernelSU module $VERSION"
mod="$OUT/module"
cp -R "$ROOT/module" "$mod"
# The icon overlay is mounted by the module manager; the LSPosed module is installed by customize.sh.
mkdir -p "$mod/system/product/overlay/XCam17Icon"
cp "$icon_apk" "$mod/system/product/overlay/XCam17Icon/XCam17Icon.apk"
cp "$lsposed_apk" "$mod/xcam17-lsposed.apk"
cat > "$mod/module.prop" <<EOF
id=xiaomi17_camera
name=Xiaomi 17 Camera for houji
version=$VERSION
versionCode=$VERSION_CODE
author=spundone
description=Runs Xiaomi's newer camera (6.8.002090.2 from the 18 Pro, or 6.7.000280.0 from the 17) in place of MiuiCamera on AOSP ROMs for the Xiaomi 14. All in one: installs the LSPosed module and an adaptive launcher icon.
EOF
module_zip="$DIST/xiaomi17_camera-$VERSION.zip"
rm -f "$module_zip"
(cd "$mod" && zip -q -r -X "$module_zip" .)
echo "   $module_zip"

if [ -n "$camera_apk" ]; then
    sum=$(shasum -a 256 "$camera_apk" | cut -d' ' -f1)
    grep -q "^$sum " "$ROOT/module/known-apks.sha256" || { echo "camera APK $sum is not in known-apks.sha256" >&2; exit 1; }
    cp "$camera_apk" "$mod/MiuiCamera.apk"
    local_zip="$DIST/xiaomi17_camera-$VERSION-with-apk.zip"
    rm -f "$local_zip"
    (cd "$mod" && zip -q -r -X -0 "$local_zip" .)
    echo "   $local_zip (contains Xiaomi's APK: do not publish)"
fi

(cd "$DIST" && shasum -a 256 "$(basename "$lsposed_apk")" "$(basename "$module_zip")" > "SHA256SUMS-$VERSION$suffix")
