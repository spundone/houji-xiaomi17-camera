# Shared helpers for the module scripts. Paths are absolute so this works from any stage.
# shellcheck shell=sh disable=SC2034,SC2154

MODDIR_COMMON=/data/adb/modules/xiaomi17_camera
KSUD=/data/adb/ksud

# Apps that must see the module's files. KernelSU's default profile unmounts module
# changes for every non-root app, which made the camera load the stock 6.4 APK. The launcher
# loads the camera's label and icon (and the icon overlay) from those files as well.
VISIBLE_PACKAGES="com.android.camera"
[ -f "$MODDIR_COMMON/launcher" ] && VISIBLE_PACKAGES="$VISIBLE_PACKAGES $(cat "$MODDIR_COMMON/launcher")"
# MIUI Gallery Global (installed by the gallery zips) is a module too: without this its apps
# can't open their own APKs and close on start, and the system apps that show its icon, share
# sheet or picker entries can't load them. KernelSU forgets a profile when its app is
# uninstalled, such as while the Gallery module is disabled, so it's set on every boot.
[ -d /data/adb/modules/MIUIGallery ] \
    && VISIBLE_PACKAGES="$VISIBLE_PACKAGES com.miui.gallery com.miui.mediaeditor com.xiaomi.account \
com.android.systemui com.android.settings com.android.permissioncontroller com.android.intentresolver \
com.android.documentsui"

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
