#!/system/bin/sh
# Runs before zygote starts, so the package scanner sees the replaced camera APK.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

TARGET=/system/priv-app/MiuiCamera/MiuiCamera.apk

# Standalone zips carry the Leica files next to the APK in app/, mounted as the whole app
# folder so no module manager mount is needed.
if [ -f "$MODDIR/app/MiuiCamera.apk" ] && [ -f "$TARGET" ]; then
    mount --bind "$MODDIR/app" "${TARGET%/*}"
elif [ -f "$MODDIR/MiuiCamera.apk" ] && [ -f "$TARGET" ]; then
    mount --bind "$MODDIR/MiuiCamera.apk" "$TARGET"
fi

# Standalone zips: the camera declares nothing overlayable, which Android lets only overlays
# preinstalled on system or product (not vendor) ignore. /product/overlay (a few MB) is rebuilt
# each boot from the ROM's overlays plus the icon, and mounted in place.
if [ -f "$MODDIR/overlay/XCam17Icon.apk" ] && [ -d /product/overlay ]; then
    ov="$MODDIR/product_overlay"
    rm -rf "$ov" && mkdir -p "$ov/XCam17Icon"
    cp -a /product/overlay/. "$ov/" && cp "$MODDIR/overlay/XCam17Icon.apk" "$ov/XCam17Icon/" \
        && chmod 0755 "$ov" "$ov/XCam17Icon" && chmod 0644 "$ov/XCam17Icon/XCam17Icon.apk" \
        && chcon -R u:object_r:system_file:s0 "$ov" \
        && mount --bind "$ov" /product/overlay
fi

for pkg in $VISIBLE_PACKAGES; do
    set_profile "$pkg" false
done
# HEIC Ultra HDR is opt-in (action.sh); the camera reads this property.
if [ -f "$MODDIR/heic_ultrahdr" ]; then
    setprop debug.xcam17.heic_ultrahdr 1
fi
