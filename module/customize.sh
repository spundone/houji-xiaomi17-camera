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
set_perm "$MODPATH/action.sh" 0 0 0755
# Keep the HEIC Ultra HDR choice across updates.
[ -f /data/adb/modules/xiaomi17_camera/heic_ultrahdr ] && touch "$MODPATH/heic_ultrahdr"

# KernelSU-only zips (scripts/build-kit-zip.sh) carry a camera APK patched by the AOSP kit, so
# the LSPosed module must not hook it a second time.
if [ -f "$MODPATH/kit" ]; then
    ui_print "- KernelSU only: the camera APK is patched, no LSPosed module needed"
    vector=/data/adb/modules/zygisk_vector/cli
    if pm path dev.spundone.xcam17 >/dev/null 2>&1; then
        if [ -x "$vector" ] && "$vector" modules disable dev.spundone.xcam17 >/dev/null 2>&1; then
            ui_print "- Disabled the LSPosed module in Vector"
        else
            ui_print "! Disable or uninstall the 'Xiaomi 17 Camera for houji' LSPosed module"
        fi
    fi
fi

# The MIUI Gallery Global module, when a zip carries it: staged like a KernelSU module update.
gallery_zip="$MODPATH/gallery/MIUIGallery.zip"
if [ -f "$gallery_zip" ]; then
    staged=/data/adb/modules_update/MIUIGallery
    rm -rf "$staged" && mkdir -p "$staged"
    if unzip -o -q "$gallery_zip" -d "$staged"; then
        set_perm_recursive "$staged" 0 0 0755 0644
        for d in system system_ext; do
            [ -d "$staged/$d" ] && set_perm_recursive "$staged/$d" 0 0 0755 0644 u:object_r:system_file:s0
        done
        [ -d /data/adb/modules/MIUIGallery ] && touch /data/adb/modules/MIUIGallery/update
        ui_print "- MIUI Gallery Global module installed"
    else
        rm -rf "$staged"
        ui_print "! Could not install the MIUI Gallery module"
    fi
    rm -rf "$MODPATH/gallery"
fi

# Leica Essential looks (optional). A community port of camera 6.8 (com.kobbin.leica) renders
# them in the app; its code, libraries and models are copied from the user's copy of it to
# /system/etc/xcam17/leica. Nothing of it is redistributed. Kept across updates.
kit="$MODPATH/system/etc/xcam17/leica"
old_kit=/data/adb/modules/xiaomi17_camera/system/etc/xcam17/leica
leica_apk=""
[ -f "$MODPATH/kit" ] && old_kit=/nonexistent
for f in /sdcard/Download/*.apk; do
    [ -f "$MODPATH/kit" ] && break
    [ -f "$f" ] || continue
    unzip -l "$f" assets/legend18/provenance.json 2>/dev/null | grep -q provenance.json || continue
    unzip -p "$f" classes11.dex 2>/dev/null | grep -q LegendaryPostProcessor && leica_apk="$f" && break
done
if [ -n "$leica_apk" ]; then
    ui_print "- Leica Essential looks from $(basename "$leica_apk")"
    mkdir -p "$kit/lib"
    unzip -o -q "$leica_apk" classes11.dex classes12.dex 'assets/legend18/*' 'assets/leica-v4/*' \
        'assets/leica-v43-ui/*' 'assets/leica_filter_param_*' -d "$kit"
    # The port's own native libraries: those the camera APK doesn't have, and the C++ runtime
    # its helper programs load from the same folder.
    have=$(unzip -l "$MODPATH/MiuiCamera.apk" 'lib/arm64-v8a/*' | awk '{print $4}')
    for lib in $(unzip -l "$leica_apk" 'lib/arm64-v8a/*' | awk '{print $4}' | grep '^lib/arm64-v8a/.*\.so$'); do
        [ "$lib" != lib/arm64-v8a/libc++_shared.so ] && echo "$have" | grep -qx "$lib" && continue
        unzip -o -q -p "$leica_apk" "$lib" > "$kit/lib/${lib##*/}"
    done
    [ -f "$kit/classes11.dex" ] && [ -f "$kit/lib/libmonopan_runner.so" ] \
        || { rm -rf "$MODPATH/system/etc/xcam17"; ui_print "! Could not copy the Leica port's files; Leica Essential stays off"; }
elif [ -f "$old_kit/classes11.dex" ]; then
    mkdir -p "${kit%/*}" && cp -a "$old_kit" "${kit%/*}/" && ui_print "- Leica Essential looks kept from the installed version"
fi

# The camera APK is bind-mounted by post-fs-data.sh; system/ holds the icon overlay
# (product/overlay) and the Leica files, mounted by the module manager. Older versions set skip_mount.
rm -f "$MODPATH/skip_mount"
[ -d "$MODPATH/system" ] && set_perm_recursive "$MODPATH/system" 0 0 0755 0644 u:object_r:system_file:s0
# The Leica processor runs its helpers as programs.
for f in "$kit"/lib/*_runner.so; do
    [ -f "$f" ] && set_perm "$f" 0 0 0755 u:object_r:system_file:s0
done

# The launcher reads the camera's label and icon from the camera's files, so it must see the
# module's files too. Remember the current home app for post-fs-data.sh and uninstall.sh.
home=$(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null \
    | tail -1 | cut -d/ -f1)
case "$home" in
    *.*) echo "$home" > "$MODPATH/launcher" && ui_print "- Launcher: $home" ;;
esac
# The launcher caches icons by package and keeps the one it drew before the overlay was
# mounted; post-fs-data.sh drops that cache once on the next boot.
touch "$MODPATH/reset_icon_cache"

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

if [ -n "$lsposed_enabled" ] || [ -f "$MODPATH/kit" ]; then
    ui_print "- Done. Reboot"
else
    ui_print "- Done. Enable 'Xiaomi 17 Camera for houji' in LSPosed (scope: Camera), then reboot"
fi
