# MiuiCamera 6.8 for houji

Ships Xiaomi's camera 6.8.002090.2 (Xiaomi 18 Pro, CN) on an AOSP ROM for the Xiaomi 14 (houji), with no KernelSU or LSPosed. Put this tree at `device/xiaomi/houji-miuicamera-68`.

It layers on the 6.4 [houji-miuicamera](https://github.com/Nissivardhan/android_device_xiaomi_houji-miuicamera) device and vendor trees: 6.8 loads `libcamera_algoup_jni.xiaomi.so` and `libcamera_mianode_jni.xiaomi.so` from `/system`, and those trees extract and shim them. `MiuiCamera68` overrides their `MiuiCamera`, so only the 6.8 APK is installed.

## Integration

1. Build the patched APK from your own copy of the camera, with `aosp/tools/patch-apk.sh` from [houji-xiaomi17-camera](https://github.com/spundone/houji-xiaomi17-camera):

   ```bash
   aosp/tools/patch-apk.sh MiuiCamera.apk device/xiaomi/houji-miuicamera-68/prebuilt/MiuiCamera.apk
   ```

2. In `BoardConfig.mk`, after the houji-miuicamera include:

   ```make
   # MiuiCamera 6.8
   -include device/xiaomi/houji-miuicamera-68/BoardConfig.mk
   ```

3. In `device.mk`, after the houji-miuicamera inherit:

   ```make
   # MiuiCamera 6.8
   $(call inherit-product-if-exists, device/xiaomi/houji-miuicamera-68/device.mk)
   ```

Leave `camera.package_name` unset: the patched APK stamps its client name on every capture request itself.

## What's here

| File | Purpose |
| --- | --- |
| `Android.bp` | `MiuiCamera68` (`android_app_import`, platform certificate, privileged) and the launcher icon overlay (`runtime_resource_overlay`) |
| `BoardConfig.mk`, `device.mk` | Props, sepolicy, packages, permission and sysconfig files |
| `configs/` | Privileged permissions, hidden-API allowlist, power-save allowlist |
| `sepolicy/vendor/platform_app.te` | What `module/sepolicy.rule` gives `priv_app_36`, for `platform_app` and `platform_app_36` |
| `system.prop`, `product.prop` | Props the camera reads, commented out where the 6.4 tree sets them |
| `overlay/MiuiCameraIconOverlay/` | Adaptive launcher icon (a copy of `icon/res`) |
| `prebuilt/` | Where `patch-apk.sh` writes `MiuiCamera.apk`; not committed |
