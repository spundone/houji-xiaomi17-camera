# Shared helpers for the module scripts. Paths are absolute so this works from any stage.
# shellcheck shell=sh disable=SC2034,SC2154

MODDIR_COMMON=/data/adb/modules/xiaomi17_camera
KSUD=/data/adb/ksud

# Apps that must see the module's files. KernelSU's default profile unmounts module
# changes for every non-root app, which made the camera load the stock 6.4 APK.
VISIBLE_PACKAGES="com.android.camera"

known_apk() {
    # $1: APK path. Matches against the hashes shipped next to this script.
    list="$(dirname "$0")/known-apks.sha256"
    [ -f "$list" ] || list="$MODPATH/known-apks.sha256"
    [ -f "$list" ] || list="$MODDIR_COMMON/known-apks.sha256"
    sum=$(sha256sum "$1" 2>/dev/null | cut -d' ' -f1)
    [ -n "$sum" ] && grep -q "^$sum " "$list"
}

clear_camera_package_cache() {
    rm -f /data/system/package_cache/*/MiuiCamera-* 2>/dev/null
    rm -f /data/dalvik-cache/*/system@priv-app@MiuiCamera@* 2>/dev/null
}

# set_profile <package> <umount_modules true|false>
set_profile() {
    [ -x "$KSUD" ] || return 1
    tmp=/data/local/tmp/xcam17-profile.json
    "$KSUD" profile get "$1" > "$tmp" 2>/dev/null || return 1
    if [ "$2" = "false" ]; then
        sed -i 's/"use_default": true/"use_default": false/; s/"umount_modules": true/"umount_modules": false/' "$tmp"
    else
        sed -i 's/"use_default": false/"use_default": true/; s/"umount_modules": false/"umount_modules": true/' "$tmp"
    fi
    "$KSUD" profile set "$tmp" >/dev/null 2>&1
    rc=$?
    rm -f "$tmp"
    return $rc
}
