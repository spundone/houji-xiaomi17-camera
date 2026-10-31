# How it works

Each problem below blocked the Xiaomi 17 camera on an AOSP ROM for the Xiaomi 14, in the order they showed up.

## 1. Installing the APK in place of the ROM's camera

The ROM ships a re-signed MiuiCamera 6.4 at `/system/priv-app/MiuiCamera`. The module bind-mounts the 6.7 APK over that path in `post-fs-data`, before zygote starts, so the package scanner picks it up as the same system app (`com.android.camera`). Android notices the signature changed and keeps the app's data.

KernelSU Next 3.x only mounts module files when a metamodule is installed, so the module doesn't rely on it: it sets `skip_mount` and does its own bind mount.

The installer also deletes the cached parse of the old package (`/data/system/package_cache/*/MiuiCamera-*`) and its compiled code. Otherwise Android keeps reporting version 6.4 and runs the old code.

## 2. "Class not found" crashes: KernelSU hiding the swap

The 6.7 camera crashed on start with `ClassNotFoundException` for classes that are inside its APK. An LSPosed probe showed the camera's class loader holding 43,059 classes, exactly the stock 6.4 APK's count, while the 6.7 APK has 35,387.

KernelSU's default app profile unmounts module changes for every app that isn't granted root, so the camera process saw the original 6.4 file at the same path. The package manager had parsed 6.7, so the app ran 6.7's manifest against 6.4's code.

Fix: the module sets `umount_modules=false` in the camera's KernelSU profile, via `ksud profile set`, on every boot. The uninstaller sets it back to the default.

## 3. Stuck on the intro: China's privacy consent (CTA)

The CN build won't open the camera (`setupCamera: skipped ... mIsNewCTAShowing: true`) until the user accepts a consent dialog. It asks `com.miui.securitycenter` to show that dialog (`miui.intent.action.SYSTEM_PERMISSION_DECLARE`), which AOSP doesn't have, so the request fails with result `-789` and the intro screen never goes away.

`ei.c.a()` returns the consent region (`CN`, `KR` or `GLOBAL`). The LSPosed module makes it return `GLOBAL`, whose policy needs no Security app. The app's other region checks are separate, so the CN features stay.

## 4. Photos not saving: SELinux

Shutter presses produced a capture but no file. Saving goes through Xiaomi's background service `vendor.xiaomi.hardware.aidlbgservice.IBGService`, hosted in the camera HAL process. The ROM's own camera is signed with the platform key and runs as `platform_app`, which the ROM's policy lets reach that service. The Xiaomi-signed 6.7 runs as `priv_app_36`, so `servicemanager` denied `find` on `hal_aidlbgservice_service`.

`sepolicy.rule` gives `priv_app_36` the same access the ROM grants its camera: finding that service and the post-processing HAL, binder calls and fd passing with `hal_camera_default`, and reading the camera and display properties.

These rules have to load at boot. Patching them into a running system isn't enough, because `servicemanager` caches its earlier "deny" decision.

## 5. Lens switching at 4K60

While recording, the camera only offers other lenses when multi-lens ("SAT") video is supported for the current resolution and frame rate. It reads that from the HAL tag `com.xiaomi.videosat.supportedRange`, decoded in `com.android.camera.data.data.j.U1(mode, quality, hdr)`:

| Quality | Bit (SDR) | Bit (HDR) |
| --- | --- | --- |
| 720p (`5`) | `0x1` | `0x100` |
| 1080p30 (`6`) | `0x2` | `0x200` |
| 1080p60 (`6,60`) | `0x4` | `0x400` |
| 4K30 (`8`) | `0x8` | `0x800` |
| 4K60 (`8,60`) | `0x10` | `0x1000` |
| 8K24 (`3001,24`) | `0x20` | `0x2000` |
| 8K (`3001`) | `0x40` | `0x4000` |
| 4K24 (`8,24`) | `0x80` | `0x8000` |

houji reports `0x8F8F`, so 4K60 and 8K are left out. The LSPosed module makes `U1` return true for `8,60`. The HAL handled it in testing: `satMasterCameraId` changed with the lens, and the file stayed 4K at about 58 fps.

## 6. Camera service crash at 2x: the client name tag

Photos at 2x killed the camera service with a null dereference in `CamX::SensorNode::ExecuteProcessRequest` (`strcmp` against `com.android.cts.verifier`). The HAL expects the request tag `com.xiaomi.sessionparams.clientName` to hold the app's package name. HyperOS sets it; the ROM's 6.4 port adds it through a patched `LeicaClientName` class on every request builder. The 6.7 camera has no such patch.

The LSPosed module hooks `CameraDeviceImpl.createCaptureRequest` in the camera process and sets the tag to `com.android.camera` plus a terminating NUL on every builder. Hooking the framework method means it doesn't depend on the camera's obfuscated names.

## 7. Lens switching with Dolby Vision at 4K30

With Dolby Vision on, `j.U1` also asks the "True Colour" component, `t2.c.n(2, quality)`, whether the HAL's Dolby config allows multi-lens video at that quality. houji's config leaves out 4K30, so the lens locked, even though the `supportedRange` mask has the HDR bit for 4K30 (`0x800`). HDR10 (`t2.a.o`, `t2.b.n`) bails out the same way. The module answers those three checks only while `U1` is running, using a thread-local depth counter, so `U1` falls through to the mask. Recording 4K30 Dolby Vision then keeps all four lenses, and the file stays Dolby Vision profile 8.4.

## 8. Front camera while recording

The camera offers this only when the device profile says so. `Common.z6()` checks for one model, `com.mi.device.Annibale`. Tapping the switch pauses the recorder through `tq.o.c(IntFunction)` and waits for a "paused" callback before reopening the other camera. Only `MiMediaCodecRecorder` (`tq.s`) sends that callback, and the camera picks it when `w5()` is true.

houji's profile class, `com.mi.device.Houji`, overrides 236 of `Common`'s flags, `w5` among them, so a hook on `Common` alone never runs. The module hooks each flag on both classes.

With the codec recorder on, the switch works: the log shows `runContinuesVideoRecording ... success`. Tested at 1080p30 (with and without Dolby Vision) and 4K30. Each file is one continuous video with a ~0.2 s gap at the switch. The camera itself hides the button at 4K60, and with Dolby Vision at 4K.

## 9. Xiaomi's recorder and start codes

`MiMediaCodecRecorder` configures Qualcomm's HEVC encoder with `vendor.qti-ext-enc-nal-length-bs.num-bytes=4`, asking for length-prefixed NAL units. HyperOS's `MPEG4Writer` handles that; AOSP's needs Annex-B start codes (`csd0 doesn't start with a start code`, then `Video track stopped with an error`), so files came out with audio only. The module removes the NAL-length keys (Qualcomm, MediaTek and generic variants) in `MediaCodec.configure` and `setParameters` for video encoders. Verified with valid files in 720p30, 1080p30/60, 4K24/30/60.

## Reading obfuscated names

The camera hides some class names behind a per-character XOR. For example `䏖䏚䏘䎛…䏶䏚䏘䏘䏚䏛` decodes to `com.mi.device.common.Common`: XOR each character with the key that turns the 4th character into `.`. Each hook checks the class and method shape it expects and skips itself on any other build.
