# KernelSU module installer, sourced by ksud with MODPATH, ZIPFILE, ui_print and abort set.
# shellcheck shell=sh disable=SC2034,SC2154
SKIPUNZIP=0

. "$MODPATH/common.sh"

ui_print "- Xiaomi 17 camera for Xiaomi 14 (houji)"

device=$(getprop ro.product.vendor.device)
[ "$device" = "houji" ] || abort "! This module is for the Xiaomi 14 (houji); this phone is '$device'"
[ "$KSU" = "true" ] || abort "! KernelSU is required (the installer sets KernelSU app profiles)"
[ "$(getprop ro.build.version.sdk)" -ge 35 ] || abort "! Android 15 or newer is required"
[ -f /system/priv-app/MiuiCamera/MiuiCamera.apk ] || abort "! This ROM has no /system/priv-app/MiuiCamera to replace"

# The camera APK is Xiaomi's and is not redistributed. A zip built with --apk carries it;
# otherwise take it from the phone's Download folder.
apk=""
if [ -f "$MODPATH/MiuiCamera.apk" ]; then
    apk="$MODPATH/MiuiCamera.apk"
else
    for f in /sdcard/Download/*.apk; do
        [ -f "$f" ] || continue
        if known_apk "$f"; then
            apk="$f"
            break
        fi
    done
    [ -n "$apk" ] || abort "! Put a supported camera APK (6.8.002090.2 or 6.7.000280.0) in /sdcard/Download and install again"
    ui_print "- Using $(basename "$apk")"
    cp "$apk" "$MODPATH/MiuiCamera.apk" || abort "! Could not copy the camera APK"
fi

known_apk "$MODPATH/MiuiCamera.apk" || abort "! Camera APK does not match a tested build (see known-apks.sha256)"

set_perm "$MODPATH/MiuiCamera.apk" 0 0 0644 u:object_r:system_file:s0
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

# The camera APK is bind-mounted by post-fs-data.sh; system/ only holds the icon overlay
# (product/overlay), mounted by the module manager. Older versions set skip_mount.
rm -f "$MODPATH/skip_mount"
[ -d "$MODPATH/system" ] && set_perm_recursive "$MODPATH/system" 0 0 0755 0644 u:object_r:system_file:s0

# The launcher reads the camera's label and icon from the camera's files, so it must see the
# module's files too. Remember the current home app for post-fs-data.sh and uninstall.sh.
home=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null \
    | tail -1 | cut -d/ -f1)
case "$home" in
    *.*) echo "$home" > "$MODPATH/launcher" && ui_print "- Launcher: $home" ;;
esac

# All in one: install the LSPosed module carried in the zip, and enable it if Vector is the
# LSPosed framework. pm reads the APK as system, so it goes through /data/local/tmp.
if [ -f "$MODPATH/xcam17-lsposed.apk" ]; then
    tmp=/data/local/tmp/xcam17-lsposed.apk
    cp "$MODPATH/xcam17-lsposed.apk" "$tmp" && chmod 0644 "$tmp"
    if pm install -r "$tmp" >/dev/null 2>&1; then
        ui_print "- LSPosed module installed"
        vector=/data/adb/modules/zygisk_vector/cli
        if [ -x "$vector" ] && "$vector" modules enable dev.spundone.xcam17 >/dev/null 2>&1 \
            && "$vector" scope set dev.spundone.xcam17 com.android.camera/0 >/dev/null 2>&1; then
            lsposed_enabled=1
            ui_print "- LSPosed module enabled in Vector for the camera"
        fi
    else
        ui_print "! Could not install the LSPosed module; install xcam17-lsposed APK by hand"
    fi
    rm -f "$tmp" "$MODPATH/xcam17-lsposed.apk"
fi

# Android caches parsed packages by path; drop the cached 6.4 so the new APK is read.
clear_camera_package_cache

if [ -n "$lsposed_enabled" ]; then
    ui_print "- Done. Reboot"
else
    ui_print "- Done. Enable 'Xiaomi 17 Camera for houji' in LSPosed (scope: Camera), then reboot"
fi
