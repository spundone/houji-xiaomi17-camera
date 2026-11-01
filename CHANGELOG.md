# Changelog

## Unreleased

- HEIC Ultra HDR (opt-in): HEIF photos can get the same estimated gain map as JPEG, as an ISO 21496-1 gain map (a hidden HEVC gain-map item and a 'tmap' item, the layout Android's camera service writes). Android shows them as Ultra HDR; the photo's own image, Exif and XMP are kept. Off by default because Google Photos can't back them up ("Can't back up") and macOS and ffmpeg can't open HEIF files with a 'tmap' item; turn it on or off with the module's Action button in the KernelSU manager.

## 1.7.0

- All-in-one zip: the KernelSU zip now carries the LSPosed module, installs it, and with Vector also enables it for the camera. Other LSPosed builds still need it ticked once.
- Adaptive launcher icon (with a themed-icon layer) on AOSP launchers, as a static overlay at /product/overlay. The module now lets KernelSU mount its system/ folder (it set skip_mount before), and lets the current launcher see module files, which it needs to read the camera's name and icon.
- Movie mode records with its own recorder again (MiCinematicRecorder) instead of the regular video one. Its recorder refers to a HyperOS audio class (android.media.AudioParaManger) this ROM lacks; the module now supplies a stand-in that reports "no AI audio", which the camera already handles. Tested: 1080p 24 fps with stereo sound.
- Fastshot: opening AF/MF no longer crashes. Its depth-of-field maths reads a sensor table houji's HAL doesn't have; the camera's built-in values are used instead. Manual focus and the distance scale work.
- 50MP Photo: sliding the zoom down from the telephoto no longer crashes the camera. At 50MP the main camera only takes its full sensor, and any digital zoom made the HAL abort. Zoom now works as in Pro mode: each lens keeps its own view while you slide, the lens changes at the next stop (0.6x, 1x, 3.2x), and the zoom settles on the stop of the lens in use. The 2x stop is hidden at 50MP. The telephoto still zooms in past 3.2x.
- Values from houji's own 6.7 profile: Fastshot presets 28, 35, 50 and 75 mm, Portrait zoom buttons 1x and 2.6x, and a 1/8000 s shortest shutter in Pro.
- Lens switching at 12.5MP works again with 50MP left on in Pro: the 50MP zoom handling now applies only to the cameras 50MP opens on their own.
- Video filters up to 4K (default 1080p 30 fps; they were limited to 1080p).
- Front video filters: the camera uses houji's own filter set (Mild, LilyWhite, Bright, Fresh, Limpid, KC64, V250, H400, ColdWhite, Native, BWClassical, Natural); before, the front list was empty because 6.8's flagship set names tables houji's firmware doesn't have.
- Adaptive telephoto (Settings) is off by default. Not yet seen to change the HAL's lens choice: the HAL still falls back to the main camera in dim light.
- Short video (swipe up > Short video) opens and records. Its engine downloads on first use; the HAL then refused its video session ("Can't connect to camera"), so it now uses the standard session. Known issues: zoom doesn't reach the camera, and you can't record several clips in a row.
- Tried and left out because the HAL crashes: Open Gate in Video mode, Movie at 4K, Video HDR at 4K 60 fps and on the front camera.

## 1.6.0

- Haptics: the camera vibrates again for the shutter, zoom ring and lens buttons, mode switches, Pro dials, the filter strip and its sliders. Its haptics need a HyperOS framework class (miui.util.HapticFeedbackUtil) this ROM lacks, so they were silent; the module plays Android's standard effects instead, following the touch-feedback setting.
- Fastshot (Street) mode in More: quick shooting with focal-length presets (35mm, 1x, 2x, 3.2x) on the multi-lens camera. The Xiaomi 14's own 6.7 profile has it; 6.8's flagship profile doesn't.
- The LSPosed module reports its real version in Android's app info (it said 1.0.0).

## 1.5.0

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
