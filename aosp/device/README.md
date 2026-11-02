# MiuiCamera 6.8 for houji (device tree)

Builds Xiaomi's camera 6.8.002090.2 (from the Xiaomi 18 Pro) into an AOSP ROM for the Xiaomi 14 (houji), in place of the 6.4 MiuiCamera. No root, KernelSU or LSPosed is involved: the APK is already patched, and the ROM build signs it with the platform key.

Made by [houji-miuicamera-port](https://github.com/spundone/houji-miuicamera-port), where the patches, the patcher and the feature list live.

## Requirements

Your houji tree must already include the 6.4 camera trees: [android_device_xiaomi_houji-miuicamera](https://github.com/Nissivardhan/android_device_xiaomi_houji-miuicamera) and its vendor repo (or copies of them, as Infinity X has). 6.8 loads the camera JNI libraries those trees ship in `/system` and uses their shims. This tree adds the 6.8 APK and overrides their `MiuiCamera`.

## Add it to a ROM

1. Sync it to `device/xiaomi/houji-miuicamera-port`, for example with a local manifest:

   ```xml
   <project path="device/xiaomi/houji-miuicamera-port"
            name="spundone/android_device_xiaomi_houji-miuicamera-port"
            remote="github" revision="main" />
   ```

2. In the device's `BoardConfig.mk`, after the houji-miuicamera include:

   ```make
   -include device/xiaomi/houji-miuicamera-port/BoardConfig.mk
   ```

3. In the device's `device.mk`, after the houji-miuicamera inherit:

   ```make
   $(call inherit-product-if-exists, device/xiaomi/houji-miuicamera-port/device.mk)
   ```

4. Build as usual. Leave `camera.package_name` unset; the patched APK sets the client name on each capture request itself.

Users coming from the KernelSU module should remove it and the LSPosed module first, then clear the camera's data once (`pm clear com.android.camera`), because the APK's signer changes.

## Contents

| Path | What it is |
| --- | --- |
| `prebuilt/MiuiCamera.apk.part-*` | The patched, unsigned APK in 90 MB parts (GitHub's file limit is 100 MB). `Android.bp` joins them; `MiuiCamera.apk.sha256` is the joined file's hash |
| `Android.bp` | `MiuiCamera68`: privileged, platform-signed, overrides `MiuiCamera`, `Aperture`, `Camera2` and `Snap`. `MiuiCameraIconOverlay`: the adaptive launcher icon |
| `BoardConfig.mk`, `device.mk` | Properties, SELinux policy, packages, permission and sysconfig files |
| `configs/` | Privileged permissions, hidden-API allowlist, power-save allowlist |
| `sepolicy/vendor/platform_app.te` | Lets the camera (`platform_app`, `platform_app_36`) reach Xiaomi's background save service and post-processing HAL, and the camera HAL |
| `system.prop`, `product.prop` | Properties the camera reads, commented out where the 6.4 tree already sets them |
| `leica/` | Leica Essential's files (below), installed to `/system/etc/xcam17/leica`, with `leica.mk`, the libraries as `prebuilt_etc` modules, and `config.fs` making its three helper programs executable |

## Leica Essential

Essential Moment (More > Essential Moment) takes photos in the 17 Ultra's M9 Iconic CCD, M3 B&W film and M10R Modern vintage looks. houji's HAL can't render them, so the community Leica port of camera 6.8 (`com.kobbin.leica`) renders them in the app after the shot, using the Leica algorithms from Xiaomi's Leica app for the 17 Ultra. `leica/` carries that port's processor (`classes11.dex`, `classes12.dex`), its native libraries and helper programs, and its models. The patched camera loads them when present and shows the mode; without `leica/`, the mode stays hidden.

- JPEG photos only (Settings > Photo format), about 3 to 5 s each.
- The port's AI style model needs the Snapdragon 8 Elite Gen 5, so its fallback tone is used on the Xiaomi 14.
- Its notices (toasts) are in Chinese.

`aosp/tools/make-device-tree.sh --leica` in houji-miuicamera-port writes `leica/` from a copy of the port.

## Other notes

- **The camera's region is CN.** Its China-only extras stay; only the consent check is patched.

## Status

The same patched APK runs on houji (Infinity X 4.0, Android 17) in place of the 6.4 camera, installed by the KernelSU module and signed with a test key. A full ROM build with this tree, and the camera running as `platform_app`, haven't been tested yet. Please open an issue in [houji-miuicamera-port](https://github.com/spundone/houji-miuicamera-port/issues) with what you find.

## Legal

The APK is Xiaomi's, patched. `leica/` is from the community Leica port and contains Xiaomi's and Leica's code and models. Xiaomi and Leica own their trademarks. The tree's own files are Apache-2.0.
