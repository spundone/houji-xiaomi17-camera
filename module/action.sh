#!/system/bin/sh
# KernelSU / Magisk "Action" button: turns HEIC Ultra HDR on or off. HEIF photos then get the
# estimated gain map JPEG photos always get. Off by default: Google Photos can't back up HEIF
# files with a gain map ('tmap' item), and macOS and ffmpeg can't open them; Android can.
# Applies from the next photo.
MODDIR=${0%/*}
FLAG="$MODDIR/heic_ultrahdr"
if [ -f "$FLAG" ]; then
    rm -f "$FLAG"
    setprop debug.xcam17.heic_ultrahdr 0
    echo "HEIC Ultra HDR: off"
else
    touch "$FLAG"
    setprop debug.xcam17.heic_ultrahdr 1
    echo "HEIC Ultra HDR: on. Google Photos can't back these HEIF photos up, and macOS can't open them"
fi
