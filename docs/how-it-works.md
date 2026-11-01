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

## 10. Dolby Vision in the viewfinder, and on the front camera

The Dolby component, `t2.c` ("True Colour"), has two keys: a Settings switch (`pref_true_colour_video_mode_setting_key`) and quick-menu keys per mode (`pref_true_colour_video_mode_menu_key`, plus Pro video, Slow motion and Super night variants). `getKey` picks the menu keys when device flag `h3` is set, as on the 17 Ultra. All 25 readers of `h3` are Dolby-related, so the module turns it on.

Which qualities get Dolby comes from `com.xiaomi.HDRVideoDisplayMode.availableconfigurations`, a list of (type, quality, fps) triplets checked by `n9.e.h1`. houji's rear camera lists (3, 1080p, 30/60) and (3, 4K, 24/30/60); its front camera only (3, 1080p, 30), though it advertises `DYNAMIC_RANGE_TEN_BIT` with HLG. The module also answers (3, 4K, 30), so front 4K30 offers Dolby Vision; the recorded file is 3840x2160 10-bit HLG with Dolby Vision profile 8.4.

## 11. Saliency checker crash

The camera crashed with SIGSEGV in a native thread named `p_aivs_init`, which belongs to `/odm/lib64/libmialgo_ai_vision.so`. The only thing in the APK that links that library is `libmialgo_saliency.so`, loaded through `com.android.camera.saliencychecker.SaliencyChecker` (`mialgo_saliency_jni`). The checker is used by the focus code (`x6.p`, `Camera2Module.onFocusAreaChanged`). The APK's saliency library is the Xiaomi 17's; the vision library it links is houji's older HyperOS build, so its init crashes. `SaliencyChecker.init`, `process` and `hasInit` now return false before reaching native code. The library still gets loaded, but its init thread never starts. Tapping to focus repeatedly no longer crashes.

## 12. Other Xiaomi 17 / 17 Ultra flags

Comparing each no-argument boolean on the device profiles (`com.mi.device.Houji` against `Pudding`, the 17, and `Nezha`, the 17 Ultra) gave 46 flags that are on for the newer phones and off for houji. Each candidate was turned on and checked: by diffing every Settings entry against a build without it, by opening the relevant UI, and by recording.

| Flag | Result |
| --- | --- |
| `G4`, `K6` | Kept: "Take photos while recording" gains a Live moments option; files are Google-format motion photos (`GCamera:MotionPhoto=1`) with an embedded 3840x2160 HEVC clip |
| `U5` | Kept: tracking focus also offered in Pro photo and Pro video |
| `v5` | Dropped: Master Live mode crashes in `ComponentRunningMasterLive` (null default-value map) |
| `T4`, `V2` | Dropped: remove the "Correct lens distortion" setting |
| `k5`, `s9`, `f3`, `y5`, `Q6`, `c5` | Dropped: no visible change found |

Grain and a Leica-off style aren't in camera 6.7 at all: there's no grain label in any language, and the style toggle only has Leica Vibrant and Leica Authentic.

## 13. Camera 6.8 (Xiaomi 18 Pro)

Obfuscated names change between builds (6.7's `ei.c` is 6.8's `Th.c`), so `CameraBuild` keeps a table per build and identifies the build by its consent-region enum. On an unknown build only the name-independent hooks run.

- **Closing after 3 seconds.** The profile loader (`Eu.f2.l`) looks for `com.mi.device.<Device>`; 6.8 has no Houji class, so it falls back to `Common` and sets a static flag that `Camera.onCreate` treats as an APK version error ("finish activity after 3 seconds"). The hook clears the flag and substitutes `com.mi.device.xiaomi.CommonFlagship`. Profile class names are XOR-scrambled with a different key per class, so the stored name is in the table rather than derived.
- **Grain.** The filter parameter panel (with Noise) needs flag `k3` (on in CommonFlagship) and style version `q0` >= 2; the 18 Pro profiles return 2.
- **Watermark name.** Watermarks print `@{series}@{versionNumber}` from the profile's name table `d()`; CommonFlagship has `{XIAOMI, MI PHONE}`. The hook answers `{XIAOMI, 14}` from `ro.product.marketname`.
- **Style ORIG.** The Style choices come from the HAL mask `xiaomi.capabilities.stylizationTypeMask`, read a bit at a time. houji reports 6 (Authentic, Vibrant). Photo mode adds the natural style when bit 3 is set; with it forced on, photos come out with lower contrast, saturation and vignetting than Vibrant.
- **50MP.** Photo mode gets the Ultra HD (Photo Resolution) item when profile flag `t1` is on; the sizes come from the HAL, which gives 12.5M and 50M.
- **Fill light at 60 fps.** With the screen fill light on (flash value 104), the video quality code caps fps at 30 unless the profile's `W0()` list includes 60 (18 Pro: `[30, 60]`). Even then the front camera records with a dynamic `[30, 60]` range, and the brighter exposure target stretched frames to 30-50 ms (about 35 fps). While the fill light is on, `FillLight60` rewrites the range to `[60, 60]`; tested at front 4K: 291 frames, all 17 ms apart.
- **More 18 Pro flags.** Tested in batches by diffing every Settings entry against the previous build, plus the viewfinder menus. Kept: `d4` (Take photos while recording: Live moments), `C3` (tracking focus in Pro photo), `f3` (Import LUT in Pro video Log). Dropped: `S2`, `X2`, `Z5`, `k4`, `e6`, `B4`, `O2`, `l2`, `y3`, `C4`, `M3`, `w3`, `I3`, `j4` (no visible change), and `A1`, `D3`, `G5`, `H3`, `K2`, `O1`, `U2`, `X1`, `e2`, `g5`, `h4`, `i5`, `j3`, `k5`, `n2`, `o2`, `p3`, `y5`, `d3`, `w5` (that batch only added AI privacy entries and removed Anti banding and Pocket mode). Pro photo and Pro video already have metering, zebras, focus peaking, histogram, waveform, RAW, Log and presets on the flagship profile.
- **50MP and Style.** Features exclude each other through mutex rules (`v3.g.b` returns the rules for a feature; Ultra HD is 209, Style 190). The Ultra HD rule disables Style, and a disabled Style reports "0", Leica Vibrant. `MutexDrop` removes the rules between 209 and 190 and keeps Style's disabled flag (`r2.m.v`) off; tested at 6144x8192 with Leica Authentic.
- **Watermarks in HEIF.** The watermark task (`q7.f.a`) draws the watermark (`U8.d.f`), re-encodes the HEIF, and when remover info (`zi.a.s`) is present also wraps the covered sub-image (`zi.a.t`) and builds XMP from it. Frame watermarks have remover info but no sub-image, so `Vh.g.a(null)` threw and the saver thread crashed; patching that only reached the next null, since the XMP helper (`Qg.r`) is never given its source. The JPEG flow ends with no remover info in the same case, so `WatermarkSubimage` clears it when there is no sub-image and HEIF takes the "skip addHeifWaterData" path. The HEIC encoder was a red herring: its `encoded 0 frames` line is printed on working shots too.
- **50MP on every lens.** houji's ultra-wide (camera 3) and telephoto (4) list full-sensor 8160x6144 quad-CFA streams, and the Ultra HD lens picker (`B2.c.l`) already opens them by zoom ratio (below 1x the ultra-wide, at the telephoto ratio the telephoto). Three things stopped it, all handled by `UltraHdLenses`: the zoom toggle state (`H4.p0.a`) comes back hidden (-1) at Ultra HD, so it is set to the 12.5M state (1); the 50M ratio list (`data.i.S(163)`) leaves out 3.2x, because the 18 Pro's telephoto Ultra HD goes through its separate 200MP `REARx7` option, so 3.2 is added back; and once an aux lens is open the Ultra HD check reads off, so `Camera2Module.isCameraSwitchingDuringZoomingAllowed` refused further switches ("current status not support switch camera lens"), so it is allowed while the open camera is 3 or 4. Tested: tele 6120x8160, main 6144x8192, ultra-wide 6120x8160, switching both ways.
- **50MP in Pro.** Pro (167) builds the lens bar differently: at Ultra HD its toggle state (`H4.p0.a`) is hidden or single-lens and `ZoomRatioToggleView.u` filters the list to 1x, so both are answered with what the mode gives at 12.5M (computed by re-running them with "Ultra HD on" answering false). Lens switches in Pro reopen the camera only when Pro's zoom-support check (`data.i.n1`) is false, and every switch reloads the Ultra HD options from the new camera (`r2.e0.N`), which switches 50M off unless the camera is a listed 50MP lens. 6.7 had that list in houji's profile: roles {0, 20, 21} (`w0`) with sizes "4:8192x6144" for the main (`v0`) and "4:8160x6144" for the ultra-wide (`y0`) and telephoto (`u0`); the leading 4 selects REARx5. With those set the Pro session on cameras 3 and 4 is `ALGO_UP_MANUAL_ULTRA_PIXEL` (0x9007). Tested: 6120x8160 on both, 6144x8192 on the main.
- **Telemacro.** The 18 Pro code still has the Xiaomi 14's path: the macro toggle (top-config item 521, `v2.i0`, "Super Macro") opens the camera of the profile's macro type. `i3` (macro supported) and `Z0` = "tele" (6.7 houji `s5` / `N1`), plus `e2` (Super Macro label), `j3` and `K2`. The actual-camera picker then opens role 20, camera 4, with `xiaomi.MacroMode.enabled` set. Tested: a sharp close-up through the telephoto.
- **Open Gate.** A ratio option of Pro video (mode 180, value "open_gate" under `pref_camera_picturesize_key_180`); `r2.T.t` adds it only when `l9.h.L4(caps)` is true, which reads the vendor tag `com.xiaomi.camera.supportedfeatures.videoOpenGate`. houji defines no such tag, but its rear cameras list 4096x3072 PRIVATE/YUV/P010 outputs (the binned full sensor). `OpenGate` answers `L4` true when the camera's LENS_FACING is back. The video setup (`video.E.l`) then records 4096x3072. Tested: 30 fps (199 frames in 6.6 s) and 60 fps (347 frames, every frame 16.7 ms apart).
- **Dual video.** Flag `G2` adds More > Dual video (`DualVideoRecordModule`, mode 206), opening the front camera (1) and the rear logical camera (5) together. Tested: one 2160x3840 file with the front as a picture-in-picture; normal 4K Dolby Vision video still records with the flag on. `l3` (Live cinematography, mode 231) crashes on entry with a null configuration map, and `P3` brings Sticker avatars, which need Xiaomi's cloud download.
- **Video filters.** The filter engine (CandySDK) loads `/odm/etc/camera/videofilter/<id>_<name>.png`. houji's firmware has ids 110, 129-134, 144, 146-162; the 18 Pro's front filters (Poetry 138, Nocturne 136, ...) have none, so the engine opened "videofilter/.png" and drew black. `VideoFilterLuts` drops items of the filter component (`r2.a.getItems`) whose value (0x700 + table id) has no table.
- **50MP aspect ratios.** The ratio component (`r2.T.getComponentValue`) answers "4x3" whenever Ultra HD is on, and the ratio-change handler (`r6.f0.U5`) turns Ultra HD off (`data.p.X0`) for any change. The sensor stream stays 8192x6144 (4:3) at 50M and the camera crops squares from it, but not 16:9 or wider (those still saved 6144x8192 with the ratio shown as 9:16). So only 1:1 is let through: the getter returns the saved 1:1, and a change to 1:1 or 4:3 keeps Ultra HD on. Tested 1:1 at 6144x6144 (main) and 6120x6120 (ultra-wide). The HAL lists an 8192x4608 (16:9) stream too, but the Ultra HD stream setup always takes the best raw size (8192x6144); wider ratios would need that changed.
- **Movie mode.** CinematicModule (227) opens the wide camera and stops before starting a session: the recorder controller (`com.android.camera.module.video.z.j`) builds `iq.r` (MiCinematicRecorder) when `a0.d()` says the mode is 227, and its audio code needs `android.media.AudioParaManger$TuneListener`, a HyperOS framework class. The `NoClassDefFoundError` is swallowed with no log, found by tracing the method. This was first worked around by answering "not Movie" to `a0.d()` while `z.j` ran, so the controller built `iq.t` (MiMediaCodecRecorder). Now `AudioParaStub` supplies a do-nothing `AudioParaManger` (and its listener interfaces) on the camera's class path instead, so Movie keeps its own recorder; tested at 1080p24 with stereo AAC. Tested: preview with the aperture (f/) control, an 8.6 s clip at 1080p 24 fps HEVC with stereo AAC. MiCinematicRecorder's audio effects (AudioParaManger's zoom/focus audio) are not used.
- **Clone.** Gated by `camera.feature.clone`; with it set the mode appears and downloads its plugin, but the QTI camera provider aborts (`Usecase::DumpSystemEvent`, in-flight list 257) and a second try fails `configureStreams` with -38. Not kept.
- **Ultra HDR.** The 6.8 app writes `HdrDisplay*` EXIF only when the HAL's capture result carries `xiaomi.snapshot.XDREnable`; it never requests it (nor does houji's stock camera). No photo on the phone has a gain map, so houji's HAL doesn't produce one. Setting `xiaomi.snapshot.XDREnable = 1` on every capture request changed nothing either: no `HdrDisplay` tags and no gain map in HEIF or JPEG. `UltraHdrJpeg` estimates one instead: when the saver reports a final image (`l7.k.w`, not a preview), it reads the JPEG back, decodes it at 1/4 size, maps sRGB luminance from 0.72 to 0.98 onto a 0 to 2 stop boost (smoothstep), encodes that as a grey JPEG with the hdrgm XMP, and appends it with the Container XMP (merged into the photo's own XMP when there is one) and an MPF index. The photo's own bytes are copied, not re-encoded, and the result is only written back if `BitmapFactory` reports `hasGainmap()`. Tested at 12.5M, 50M and with a watermark. The HAL lists Android's JPEG_R and HEIC Ultra HDR streams, but the Xiaomi app never configures them.
- **Telephoto.** Photo mode already uses the logical camera combining ultra-wide, wide and telephoto, switching lenses with zoom; Settings > Adaptive telephoto controls it.
- **Leica Essential.** The "All-new Leica Essential" entry (`R5.D.a`) is gated by the same `c3` flag as Legendary mode below: it is the 17 Ultra's name for the M9 / M3 / M10R looks mode.
- **Left out.** Legendary mode (`c3`, `b3`, module 256) shows its picker but passes the look to the HAL as `com.xiaomi.sessionparams.legendMode`, which houji's HAL doesn't define, so photos are unprocessed. Street (`c2`, 225) and Ultra-pixel (`o3`, 175) never appeared in the mode list.

## Reading obfuscated names

The camera hides some class names behind a per-character XOR. For example `䏖䏚䏘䎛…䏶䏚䏘䏘䏚䏛` decodes to `com.mi.device.common.Common`: XOR each character with the key that turns the 4th character into `.`. Each hook checks the class and method shape it expects and skips itself on any other build.
