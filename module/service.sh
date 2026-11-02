#!/system/bin/sh
# Runs after boot. With MIUI Gallery installed, sets the haptics version its editor can use.
# Once after an install, the launcher's icon cache is dropped so it draws the
# overlay's icon instead of the one it cached before the overlay was mounted. App data stays
# encrypted until the first unlock, so this waits for the launcher's data to appear.
MODDIR=${0%/*}

# MIUI Gallery's editor carries HyperOS's haptic code, which it uses when houji reports
# sys.haptic.version 2.0, and which crashes on AOSP (missing HyperOS framework resources) when
# a list bounces at its end. At 1.0 Xiaomi's apps use Android's haptics; the camera feels the same.
if [ -d /data/adb/modules/MIUIGallery ] && [ ! -f /data/adb/modules/MIUIGallery/disable ]; then
    /data/adb/ksu/bin/resetprop sys.haptic.version 1.0
    (
        i=0
        until [ "$(getprop sys.boot_completed)" = 1 ] || [ $i -ge 300 ]; do sleep 2; i=$((i + 1)); done
        /data/adb/ksu/bin/resetprop sys.haptic.version 1.0
    ) &
fi

[ -f "$MODDIR/reset_icon_cache" ] && [ -f "$MODDIR/launcher" ] || exit 0
launcher=$(cat "$MODDIR/launcher")
data="/data/data/$launcher/databases"
i=0
until [ -d "$data" ] || [ $i -ge 600 ]; do
    sleep 2
    i=$((i + 1))
done
[ -d "$data" ] || exit 0
rm -f "$data"/app_icons.db*
am force-stop "$launcher"
rm -f "$MODDIR/reset_icon_cache"
