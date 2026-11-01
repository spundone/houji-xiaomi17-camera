# Changelog

## Unreleased

- 50MP on all three lenses in Pro mode as well: the 0.6 / 1 / 3.2 buttons stay at 50M and the ultra-wide and telephoto shoot 6120x8160. The module gives the camera houji's 50MP lens list from the 6.7 profile (main 8192x6144, ultra-wide and telephoto 8160x6144), which 6.8 lacks.
- Telemacro: Super Macro in Photo's menu (the expanded top bar) switches to the telephoto for close-ups, as on the stock Xiaomi 14 (profile flags from houji's 6.7 profile: macro supported, macro camera "tele").
- Open Gate video: Pro video > ratio > Open Gate records the full 4:3 sensor at 4096x3072, at 30 or 60 fps, on the rear cameras. The camera offers it only when the HAL declares an Open Gate capability, which houji's doesn't, though its rear cameras do stream 4096x3072.
- Dual video (More > Dual video): rear and front recorded into one file with the front as a picture-in-picture (2160x3840).
- Checked and left out: Live cinematography (crashes on entry, no configuration data on this phone) and Sticker avatars (needs Xiaomi's cloud download).
- Video filters no longer turn the viewfinder black. The filters are colour tables in the phone's firmware, and the 18 Pro's list includes ones the Xiaomi 14 doesn't have (all of the front camera's, such as Poetry and Nocturne); those are now hidden. The rear Leica styles (VIV, NAT, BW, BW HC, Sepia, Blue) remain. Log LUTs were not affected.

## 1.4.2

- Ultra HDR for JPEG photos (Settings > Photo format: JPEG). houji's HAL never makes the HDR gain map, so the module estimates one from the photo, brightening highlights by up to 2 stops, and adds it as an Android Ultra HDR gain map. The photo itself is not re-encoded; HDR screens show brighter highlights and other screens show it unchanged. HEIF photos and motion photos are left as they are.
- Leica Essential is the 17 Ultra's name for Legendary mode, which needs HAL support houji lacks (see 1.4.0).

## 1.4.1

- Camera 6.8: Take photos while recording offers Live moments (motion photo) or Regular photos (flag `d4`, the 6.7 feature carried over).
- Camera 6.8: Motion tracking focus also works in Pro photo (flag `C3`).
- Camera 6.8: Import LUT in Pro video Log, next to the built-in LUTs (flag `f3`).
- Camera 6.8: 50MP keeps the chosen style. Turning on 50M used to switch the photo to Leica Vibrant, because the camera treats Ultra HD and Style as mutually exclusive; the module drops that one rule, so Leica Authentic and Style ORIG work at 50M.
- Camera 6.8: fix a crash with a watermark on and the format set to HEIF. Frame watermarks (the Leica band below the photo) cover none of the photo, yet the HEIF path still built "watermark remover" data for the covered area and crashed on its absence, losing the photo. The remover data is now dropped when there is nothing to restore, as the JPEG path already does. Works at 12.5M and 50M.
- Camera 6.8: 50MP on all three lenses. At 50M the 0.6 / 1 / 2 / 3.2 buttons now show and switch between the ultra-wide, main and telephoto, each shooting its full sensor (6120x8160 on the ultra-wide and telephoto, 6144x8192 on the main).
- Camera 6.8: 50MP in 1:1 as well as 4:3 (6144x6144 on the main). 16:9, full screen and widescreen still switch back to 12.5M: the 50M sensor stream is 4:3 and the camera only crops squares from it.
- Camera 6.8: Movie mode works. It opened to a frozen, blurred frame because its recorder (MiCinematicRecorder) needs a HyperOS audio class this ROM doesn't have; it now records with the regular video recorder (tested: 1080p 24 fps HEVC with stereo sound). Open it by swiping up on the viewfinder.
- Checked and left out: Clone mode (it appears and downloads, but the camera HAL aborts when it starts), and Ultra HDR photos (this phone's HAL never adds the gain map; no photo on it has one, from before the port either).
- Checked and left out: 35 more 18 Pro flags. Most changed nothing visible; one batch removed Anti banding and Pocket mode from Settings.

## 1.4.0

Adds support for MiuiCamera **6.8.002090.2** from the Xiaomi 18 Pro, alongside 6.7. The module detects which camera build is installed and applies only the hooks written for it.

- 6.8 has no profile for the Xiaomi 14, so it treated itself as the wrong APK version and closed after 3 seconds. The module clears that check and gives the camera the shared Xiaomi flagship profile instead of the bare generic one, which kept only 1080p30 and no lens buttons.
- Film grain: Filters > tap the selected filter > Parameters now has Noise, next to Saturation, Tone, Soft light, Cold/Warm, Cyan/Magenta, Sharpness and Vignette, plus the 18 Pro's Palette pad and saved presets (style version 2).
- Leica-off look: the Style picker offers Style ORIG beside Leica Vibrant and Leica Authentic. The HAL only reports the two Leica looks; the third is Xiaomi's own processing, and photos come out measurably flatter, brighter and less vignetted.
- 50MP in Photo mode: a Photo Resolution tile switches between 12.5M and 50M, not just in Pro.
- Front fill light at 60 fps: turning on the screen fill light no longer drops video to 30 fps, and the frame rate is held at 60 instead of drifting to about 35 fps.
- The watermark shows the phone's own name (Xiaomi 14) instead of "MI PHONE".
- Front/rear switching while recording and the Dolby Vision viewfinder toggle carry over to 6.8.
- Tried and left out: Leica Legendary mode (M9 Iconic CCD, M3 B&W film, M10R; the look is rendered by HAL support the Xiaomi 14 doesn't have, so photos came out unprocessed), Street and Ultra-pixel modes (never appeared), Sticker avatars (needs Xiaomi's cloud download).

## 1.3.0

- Live moments while recording: the snapshot button during video can save a motion photo (still plus a short 4K clip) instead of a regular photo. Choose in Settings > Video > Take photos while recording. Device flags `G4`/`K6` from the Xiaomi 17.
- Subject tracking focus is also offered in Pro photo and Pro video (flag `U5`).
- Tried and left out: Master Live mode (`v5`, crashes: no configuration data on this phone), the ultra-wide distortion flags (`T4`/`V2`, they remove the "Correct lens distortion" setting), and `k5`, `s9`, `f3`, `y5`, `Q6`, `c5` (no visible effect found).

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
