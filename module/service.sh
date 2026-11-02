#!/system/bin/sh
# Runs after boot. Once after an install, the launcher's icon cache is dropped so it draws the
# overlay's icon instead of the one it cached before the overlay was mounted. App data stays
# encrypted until the first unlock, so this waits for the launcher's data to appear.
MODDIR=${0%/*}

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
