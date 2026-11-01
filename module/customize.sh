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

# The APK is bind-mounted by post-fs-data.sh, so the module manager must not mount system/.
touch "$MODPATH/skip_mount"

# Android caches parsed packages by path; drop the cached 6.4 so the new APK is read.
clear_camera_package_cache

ui_print "- Done. Reboot, then enable the 'Xiaomi 17 Camera for houji' module in LSPosed"
