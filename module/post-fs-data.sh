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

# Once after an install: the launcher's icon cache (rebuilt on its next start).
if [ -f "$MODDIR/reset_icon_cache" ]; then
    [ -f "$MODDIR/launcher" ] && rm -f "/data/data/$(cat "$MODDIR/launcher")/databases/app_icons.db"*
    rm -f "$MODDIR/reset_icon_cache"
fi

# HEIC Ultra HDR is opt-in (action.sh); the camera reads this property.
if [ -f "$MODDIR/heic_ultrahdr" ]; then
    setprop debug.xcam17.heic_ultrahdr 1
fi
