#!/system/bin/sh
# Hand the stock camera back: restore default KernelSU profiles and drop cached package
# data so the next boot reparses the ROM's own MiuiCamera.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

for pkg in $VISIBLE_PACKAGES; do
    set_profile "$pkg" true
done
clear_camera_package_cache
