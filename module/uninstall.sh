#!/system/bin/sh
# Hand the stock camera back: restore the camera's default KernelSU profile and drop cached
# package data so the next boot reparses the ROM's own MiuiCamera. The launcher's profile is
# left as it is: other modules (such as MIUI Gallery's) may rely on it.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

set_profile com.android.camera true
clear_camera_package_cache
