#!/system/bin/sh
# Runs before zygote starts, so the package scanner sees the replaced camera APK.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

TARGET=/system/priv-app/MiuiCamera/MiuiCamera.apk

if [ -f "$MODDIR/MiuiCamera.apk" ] && [ -f "$TARGET" ]; then
    mount --bind "$MODDIR/MiuiCamera.apk" "$TARGET"
fi

for pkg in $VISIBLE_PACKAGES; do
    set_profile "$pkg" false
done
# HEIC Ultra HDR is opt-in (action.sh); the camera reads this property.
if [ -f "$MODDIR/heic_ultrahdr" ]; then
    setprop debug.xcam17.heic_ultrahdr 1
fi
