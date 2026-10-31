# Changelog

## 1.2.1

- Fix a camera crash (SIGSEGV in thread `p_aivs_init`). The camera's saliency checker, used when the focus area changes, runs the Xiaomi 17's `libmialgo_saliency.so` against the phone's older `/odm/lib64/libmialgo_ai_vision.so`. The module now reports the checker as unavailable so that native code never starts; focus falls back to the normal path.

## 1.2.0

- Dolby Vision is a toggle in the viewfinder's quick menu (and in Pro video, Slow motion and Super night), like on the Xiaomi 17 Ultra, instead of a switch in Settings. Turned on through device flag `h3`.
- Dolby Vision on the front camera at 4K30, as well as 1080p30. The HAL's Dolby list only had front 1080p30, though the front camera records 10-bit HLG at every size; tested to produce Dolby Vision profile 8.4 files at 4K30.

## 1.1.0

- Lens switching while recording now works at 4K30 with Dolby Vision. The camera's Dolby ("True Colour") check locked 4K30 to one lens even though the hardware's HDR bit for 4K30 is set.
- Switch between the rear and front camera mid-recording, at 720p, 1080p and 4K30. Turned on through the Xiaomi 17's device-profile flags, hooked on the Xiaomi 14's profile class too, since it overrides many of them.
- Video is now recorded by Xiaomi's own recorder, which the camera switch needs. Its encoder asks for length-prefixed video packets that only HyperOS's muxer accepts, so the module strips those encoder options. Without that, 4K60 files had no playable video.

## 1.0.1

- Fix the camera service crashing at 2x in photo mode. Qualcomm's camera HAL reads the requesting app's name from the `com.xiaomi.sessionparams.clientName` request tag and crashes when it's missing. The LSPosed module now sets it on every capture request, as the ROM's own camera port does.

## 1.0.0

First release, for MiuiCamera 6.7.000280.0 on the Xiaomi 14 (houji).

- KernelSU module: replaces the ROM's MiuiCamera at boot, adds the SELinux rules for photo saving, and stops KernelSU hiding the swap from the camera. Checks the device and the camera APK's SHA-256 before installing.
- LSPosed module: skips the China-only privacy consent that needs Xiaomi's Security app, and allows switching lenses while recording at 4K60.
- Not included: switching to the front camera while recording (it stalls the recording on this ROM).
