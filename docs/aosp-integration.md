# Shipping the camera in an AOSP ROM build

Research notes on building this camera into a ROM out of the box, with no KernelSU or LSPosed. The kit these notes proposed is in [`aosp/`](../aosp/README.md); [Using the kit](#using-the-kit) at the end says how to use it and what's been checked.

## How ROMs ship Xiaomi's camera today

ROMs take Xiaomi's camera APK and unpack it with apktool. They apply patches to the decompiled code (smali), repack it, and ship it as a prebuilt system app signed with the **platform key**. A device-tree folder alongside it carries SELinux policy, permission XMLs and properties.

### Closest template: houji on Android 17

The repos are [Nissivardhan/android_device_xiaomi_houji-miuicamera](https://github.com/Nissivardhan/android_device_xiaomi_houji-miuicamera) (lineage-24.0) and its [vendor repo](https://github.com/Nissivardhan/android_vendor_xiaomi_houji-miuicamera).

- **APK source:** houji's own HyperOS firmware (OS3.0.303.0.WNCMIXM).
- **Patching:** LineageOS extract-utils applies the smali patches in `patches/` to `MiuiCamera.apk` (`blob_fixup().apktool_patch('patches')`). It signs the result with `certificate: "platform"` (see [bp_builder.py](https://github.com/LineageOS/android_tools_extract-utils/blob/lineage-24.0/extract_utils/bp_builder.py)).
- **Patches that match fixes in this repo:**
  - "Assume that mod_device is global" and "Remove Leica permissions limited popup": the consent dialog.
  - "Stop setting NAL-length prefixed encoder output": the same fix as `AnnexBVideo`.
  - "Provide Leica camera client name" and "Preserve client name in MIVI capture requests": the same fix as `Entry.hookClientName`. They add a `LeicaClientName` helper class, the one the ROM's 6.4 camera also has.
- **Native shims:** `libgui_shim_miuicamera`, `libts_graphicbuffer_shim`, and a byte patch on `libcamera_algoup_jni`.
- **SELinux:** `sepolicy/vendor/platform_app.te` gives `platform_app` / `platform_app_36` the access this repo's `module/sepolicy.rule` gives `priv_app_36`:
  - the background save service;
  - binder calls and fds with the camera HAL and Xiaomi's post-processing HAL;
  - the camera and display properties.
- **Other files:**
  - privapp permissions;
  - a hidden-API allowlist;
  - `system.prop` with `ro.miui.build.region`, `ro.product.mod_device` and `persist.vendor.camera.privapp.list`.

### Other examples

- [gitea vherawidatama/vendor_xiaomi_camera](https://gitea.com/vherawidatama/vendor_xiaomi_camera) (houji and the 14 Pro / Ultra, lineage-22.2): `android_app_import` with platform certificate and `privileged: true`, overriding the stock camera packages.
- [PocoF3Releases/device_xiaomi_camera](https://github.com/PocoF3Releases/device_xiaomi_camera): gives the camera its own SELinux domain through `seapp_contexts` instead of widening `platform_app`.
- [Evolution-X-Devices/device_xiaomi_miuicamera](https://github.com/Evolution-X-Devices/device_xiaomi_miuicamera), and a [MiuiCamera 6.8 tree for Redmi devices](https://github.com/AbdoAhmedElbanaa/android_device_xiaomi_miuicamera_v6.8_miatoll).

### A framework-side option for the client name

LineageOS-derived `frameworks/av` can fill a vendor tag with the client package name (`CAMERA_PACKAGE_NAME`, set with `soong_config_set,camera,package_name,...`). It only sets the session parameter at stream configuration, not every request, so the houji tree uses the APK patch instead.

## Replacing each hook

Patching the APK is the practical route. Because a patched APK must be re-signed, it then runs as `platform_app`, which the ROM's policy mostly covers already. No signature self-check was found in the camera. Xiaomi cloud features (sticker downloads, account features) may not work with another signer; that hasn't been checked.

| Current piece | In a ROM build | Effort |
| --- | --- | --- |
| Client name (`Entry.hookClientName`) | APK patch: helper class plus calls in the request builders, as in the houji tree | Low |
| `AnnexBVideo` | APK patch: skip the NAL-length branch in the recorder setup | Low |
| `SaliencyOff` | APK patch: the checker's init/process return false | Low |
| Consent region | APK patch: the consent region returns GLOBAL | Low |
| Missing profile, marketing name | APK patch: load `CommonFlagship` and clear the missing-profile flag (or add a Houji profile class); name from `ro.product.marketname` | Medium |
| `DeviceFeatures` flags | APK patches generated from the flag table: constant returns on the profile classes | Medium |
| Lens switching while recording, front Dolby 4K, `NaturalStyle`, `OpenGate` | APK patches: small return-value edits | Low to medium |
| `MutexDrop`, `WatermarkSubimage`, `VideoFilterLuts`, `MovieRecorder`, `CameraHaptics` | Java helpers compiled into an extra dex, plus call sites patched in | Medium |
| `FillLight60`, `UltraHdLenses`, `UltraHdrJpeg` | Same, with more call sites | Medium to high |
| KernelSU swap, profile and package-cache steps | Not needed: the ROM ships the app | None |
| `module/sepolicy.rule` | Device tree, rewritten for the platform domain (or a dedicated one) | Low |

Other routes considered:
- **Framework patches** (`frameworks/av`, `frameworks/base`): they change behaviour for every app and are harder to land. They're only worth it for the client name, where an option already exists.
- **[LSPatch](https://github.com/JingMatrix/LSPatch):** embeds this LSPosed module into the APK so it runs without root. It's quick as a stopgap, but ships a hooking runtime inside a system app, which ROM maintainers are unlikely to accept.

## Redistribution

ROM vendor repos commonly carry Xiaomi's APKs and libraries. That is tolerated, not licensed; LineageOS only asks that blobs come from a public stock image. This project keeps its rule: it never publishes the APK. Smali patch files include small fragments of decompiled code as context. Patches that find methods by fingerprint and are written in code avoid that, and also survive name changes between camera builds better.

## Proposed kit

An `aosp/` folder in this repo:

- `device/`: a drop-in `device/xiaomi/houji-miuicamera` with:
  - `Android.bp` (`android_app_import`, platform certificate, privileged, overriding the ROM's camera);
  - the makefiles;
  - permission and sysconfig XMLs;
  - `sepolicy/vendor/platform_app.te`;
  - properties.
- `patches/6.8.002090.2/`: apktool smali patches, in the format extract-utils applies.
- `helpers/`: the hook logic rewritten as plain static Java methods, compiled into an extra dex that the patches call.
- `tools/patch-apk.sh`: checks the user's APK against `module/known-apks.sha256`, then decompiles it, applies the patches, adds the helper dex, rebuilds and aligns it. The ROM build signs it with the platform key.

Estimated at one to two weeks to port and re-test everything in the feature table. Each new camera build needs the patches rebased.

Infinity X 4.0's houji build (read over adb, Nov 1) uses Nissivardhan's houji-miuicamera tree, or a copy of it. Its props are that tree's `system.prop` exactly (`ro.product.mod_device=shennong`, `persist.vendor.camera.privapp.list=com.android.camera,org.lineageos.aperture`, `ro.miui.ui.version.name=V816`). Its files are there too: `privapp-permissions-miuicamera.xml` and `-miuiscanner.xml`, `miuicamera-hiddenapi-package-allowlist.xml`, both public-library lists, and `libgui_shim_miuicamera.so` beside the camera JNI libraries in `/system/lib64`.

Not verified yet:
- whether 6.8 needs newer camera JNI libraries than houji's firmware has (the KernelSU swap working suggests not);
- whether 6.8 behaves differently when platform-signed.

## Using the kit

The kit was built from `main` at v1.7.0 (2f8c709). Two things differ from the proposal above:

- **Patches are data, not smali diffs.** `aosp/patches/6.8.002090.2/patches.json` (generated by `patches.py`) lists each method by class, name and prototype, with fingerprint strings it must contain, and what to do to it. `aosp/tools/xcampatch.py` applies it. The repo carries no decompiled code, and a patch whose method has moved or changed fails the build instead of patching the wrong thing. extract-utils' `apktool_patch` takes `git am` style patches, so the kit runs as its own step (`patch-apk.sh`) before the ROM build instead.
- **The device tree layers on the 6.4 tree.** 6.8 loads `libcamera_algoup_jni.xiaomi.so` and `libcamera_mianode_jni.xiaomi.so` from `/system`, not from its APK, and the existing houji-miuicamera device and vendor trees extract and shim those. So the kit's tree goes to `device/xiaomi/houji-miuicamera-68`, and its `MiuiCamera68` module overrides their `MiuiCamera`.

### Steps

1. Get the camera APK (6.8.002090.2, SHA-256 `729b0b19…2c48`); see [houji-camera.md](houji-camera.md#4-getting-the-camera-apks).
2. Patch it:

   ```bash
   aosp/tools/patch-apk.sh MiuiCamera.apk out/MiuiCamera.apk
   ```

   Needs JDK 11+, Python 3, an Android SDK (build-tools, a platform 34 or newer) apktool (tested with 3.0.3) and about 1 GB of free disk. It stops on any other APK, and on any patch that doesn't match exactly.
3. Copy `aosp/device/` to `device/xiaomi/houji-miuicamera-68` in the ROM tree, and the patched APK to its `prebuilt/MiuiCamera.apk`.
4. Include it after the houji-miuicamera tree: `-include device/xiaomi/houji-miuicamera-68/BoardConfig.mk` in `BoardConfig.mk`, and `$(call inherit-product-if-exists, device/xiaomi/houji-miuicamera-68/device.mk)` in `device.mk`.
5. Build. The ROM build signs the APK with the platform key; the camera then runs as `platform_app` (or `platform_app_36`), which `sepolicy/vendor/platform_app.te` covers.

Upgrading from the KernelSU build: remove the KernelSU and LSPosed modules first, and clear the camera's data once (`pm clear com.android.camera`), since the app's signer changes.

### What each hook became

[aosp/README.md](../aosp/README.md#ported-hooks) has the table. In short: all low-effort rows, and every medium row except `CameraHaptics`, are ported (53 patches). `UltraHdLenses`, `UltraHdrJpeg`/`HeifGainMap` and `CameraHaptics` aren't, so a ROM build doesn't get 50MP lens switching at the zoom stops, Ultra HDR JPEG, or haptics yet.

### Status

- `patch-apk.sh` runs end to end on 6.8.002090.2 in under a minute. All 53 patches match exactly the methods they expect. The rebuilt APK keeps its package name and version, gains the helper dex as `classes11.dex`, and passes `zipalign -c`.
- The script's own check (the rebuilt APK decoded again) finds all 59 generated methods and all 44 redirected call sites, with no calls to the original framework methods left outside the helpers.
- The dex files were also checked independently with `dexdump -d`. Each patched method has its new body, each wrapper calls its `$xcam` original and its helper, and the helper dex holds the 15 helper and stand-in classes.
- The output is the original APK with only its dex files replaced (and the helper dex added). The first build repacked everything through apktool. On macOS's case-insensitive disk that merged 131 resource files whose obfuscated names differ only in case (`res/NSC.xml` and `res/nsc.xml`), and the camera crashed loading one.
- On the phone (Nov 1, Infinity X 4.0) the patched APK ran debug-signed in place of the KernelSU module's APK, with the LSPosed module disabled. The camera opens, passes ART verification, and reaches the viewfinder in 4K30 Dolby Vision video (so the CommonFlagship profile is in use), with its encoder configured. Each feature hasn't been retested one by one yet.
- Not tested as `platform_app`: that needs the ROM's platform key, so in a ROM build.
- The run needs about 1 GB of free disk: apktool decodes the APK twice (to patch, then to verify).
