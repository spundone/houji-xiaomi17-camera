# How the Xiaomi 14 camera stack works

This is the reference for porting newer Xiaomi camera apps to the Xiaomi 14 (houji): what the phone's hardware and HAL offer, how the camera app is organised, what each piece of this repo does, where to get the camera APKs, and how the problems in [how-it-works.md](how-it-works.md) were found. Obfuscated names are for MiuiCamera **6.8.002090.2** unless noted; 6.7.000280.0 names differ (see `CameraBuild.java`).

## 1. The phone

### Cameras

`adb shell dumpsys media.camera` lists these. "Role" is Xiaomi's vendor role id, which the app uses to pick lenses and profile entries.

| Id | What it is | Role | Full size | Notes |
| --- | --- | --- | --- | --- |
| 0 | Logical: ultra-wide + main | 64 | 4096x3072 | physical 3, 2 |
| 1 | Front | 1 | 3264x2448 | no 4096x3072 stream |
| 2 | Main (wide) | 0 | 8192x6144 quad-CFA | 50MP; 4096x3072 binned |
| 3 | Ultra-wide | 21 | 8160x6144 quad-CFA | 50MP |
| 4 | Telephoto 3.2x (floating, close focus) | 20 | 8160x6144 quad-CFA | 50MP; telemacro |
| 5 | Logical SAT: ultra-wide + main + telephoto | 60 | 4096x3072 | Photo mode's normal camera |
| 6, 8 | Logical SAT variants (3, 2, 4) | 100, 65 | 4096x3072 | |
| 7 | Front variant | 101 | 3264x2448 | |
| 10 | Other | 108 | | |

- Full-resolution streams are listed as `org.codeaurora.qcamera3.quadra_cfa.availableStreamConfigurations` and `xiaomi.scaler.availableHeicStreamConfigurations`, not in the standard maximum-resolution configs.
- The rear cameras stream 4096x3072 in PRIVATE, YUV and P010, which is what Open Gate records.
- The main camera also lists 8192x4608 (16:9) at full resolution. Nothing in the app uses it yet (see the roadmap).

### HAL facts that mattered

- **Qualcomm camx HAL**, the Xiaomi 14's own (2024) version. It's older than the 17 / 18 apps expect, so some vendor tags they use don't exist.
- **Present and used:**
  - `xiaomi.capabilities.stylizationTypeMask` is 6 (Leica Authentic and Vibrant).
  - `xiaomi.MacroMode.enabled` and `xiaomi.capabilities.macro_zoom_feature` (telemacro).
  - `xiaomi.snapshot.XDREnable` and `XDRVersion` are defined, but the HAL never produces an HDR gain map, even when asked.
  - Stream-config and session tags for Ultra HD (`ALGO_UP_MANUAL_ULTRA_PIXEL` 0x9007, QCFA 0x9001).
  - Android's JPEG_R and HEIC Ultra HDR stream configurations are listed, but the Xiaomi app never configures them.
- **Missing, which limits features:**
  - `com.xiaomi.sessionparams.legendMode`: Leica Legendary / Essential looks.
  - `com.xiaomi.camera.supportedfeatures.videoOpenGate`: the module answers this itself.
  - `com.xiaomi.capabilities.Cinelook.quality`.
  - `livePhoto.version`.
- **`com.xiaomi.sessionparams.clientName`** must be set on every request, or the HAL crashes at 2x (`Entry.hookClientName`).
- **Video filter colour tables** live in firmware: `/odm/etc/camera/videofilter/<id>_<Name>.png`. houji has ids 110, 129–134, 144 and 146–162. The front-camera filters of the 18 Pro (136, 138 and others) aren't there.
- **Saving photos** goes through Xiaomi's background service `vendor.xiaomi.hardware.aidlbgservice.IBGService`, hosted in the camera HAL process. That's why the SELinux rules in `module/sepolicy.rule` are needed.

## 2. The camera app

### Device profiles

The app decides most per-phone features from a **device profile**: `com.mi.device.<Device>` extends `com.mi.device.common.Common`, with one no-argument method per feature. The method names are obfuscated, and the class names are stored XOR-scrambled (see "Reading obfuscated names" in how-it-works.md).

- **6.7** contains `com.mi.device.Houji`. It is the best reference for what the Xiaomi 14 should report.
- **6.8** has no Houji profile. The module loads `com.mi.device.xiaomi.CommonFlagship` instead and answers individual flags (`DeviceFeatures.java`).
- Profile values found in 6.7's Houji profile and set for 6.8:

| 6.8 flag | 6.7 Houji | Value | Meaning |
| --- | --- | --- | --- |
| `w0` | `d1` | {0, 20, 21} | Camera roles that are 50MP lenses |
| `v0` / `y0` / `u0` | `c1` / `f1` / `b1` | "4:8192x6144", "4:8160x6144", "4:8160x6144" | Ultra HD size per lens: main, ultra-wide, telephoto (the leading 4 selects REARx5) |
| `i3` | `s5` | true | Macro supported |
| `Z0` | `N1` | "tele" | Macro camera type, which makes it telemacro |
| `e2`, `j3`, `K2` | `O3`, `t5`, `I4` | true | Super Macro label, macro in mode 172, macro in the fast list |
| `c2` | `M3` | true | Fastshot (Street) mode; also answered on `StreetModuleEntry.support()` because the flag method can be inlined |

### Modes

Modes are integer module indexes. The ones that came up:

| Index | Mode | Index | Mode |
| --- | --- | --- | --- |
| 162 | Video | 186 | Documents |
| 163 | Photo | 206 | Dual video |
| 167 | Pro (photo) | 225 | Street / Fastshot |
| 175 | Ultra-pixel (200MP) | 227 | Movie (CinematicModule) |
| 180 | Pro video | 231 | Live cinematography (MasterLiveModule) |
| 185, 210 | Clone | 256 | Legendary |

- The **More** panel opens with a swipe up on the viewfinder.
- Pro photo runs in `ProModule`. It uses `Camera2Module`'s lens-switch check (`isCameraSwitchingDuringZoomingAllowed`), so one hook covers both.

### Settings components

Each setting is a "component" class holding a value per mode. The ones touched here:

| Component | What it holds |
| --- | --- |
| `r2.e0` | Ultra HD (50MP); values `OFF`, `REARx5` (50MP), `REARx7` (18 Pro 200MP telephoto) and so on; pref `pref_camera_ultra_pixel_<mode>` |
| `r2.T` | Aspect ratio; `4x3`, `1x1`, `16x9`, `20x9`, `open_gate` (Pro video); pref `pref_camera_picturesize_key` |
| `r2.a` | Master (video) filters; item values are 0x700 + the colour-table id |
| `v2.i0` | Macro (Super Macro); top-config item 521 |
| `r2.m` | Photo style (Leica Vibrant / Authentic / Style ORIG) |
| `r2.w` | Flash; front fill light is value 104 |

### Feature mutexes

Features switch each other off through a rule table (`v3.g.b` returns the rules for a feature id). Ultra HD is 209, Style 190; 206 and 186 relate to the aspect ratio. `MutexDrop` removes chosen pairs.

### Lens choice

`B2.c.c` ("getActualOpenCameraId") picks the physical camera for a mode.
- With Ultra HD on, it calls `B2.c.l`: below 1x it opens the ultra-wide, at the telephoto ratio the telephoto, otherwise the main.
- With macro on, it opens the profile's macro camera.
- In Photo, `i9.g` (ZoomManager) switches lenses when zooming. In Pro, `i9.g.L9` does it ("switchLensInPro").

### Photo saving

- `ImageSaver` (`l7.k`) runs a task list per shot: Water (watermark), Effect, Doc, Exif, StoPara (store).
- The watermark task is `q7.f.a`.
- The final image is reported through `l7.k.w(uri, …, isPreview)`.

### Haptics

- The camera's own haptics go through a vibrator context (`Rr.d`, instance `Rr.d.b()`). Each trigger (`m` shutter, `q` zoom tick, `a` mode switch, `p`/`i` Pro dial ticks, `r` filter strip...) only runs if the context holds a strategy (`Rr.d.e`, interface `Rr.a`, whose methods have the same letters).
- The strategy is created at startup (`E1.q3`) only if `miui.util.HapticFeedbackUtil.isSupportLinearMotorVibrate()` works. That is a HyperOS framework class, missing on AOSP, so the strategy stays null.
- `CameraHaptics` fills the slot with a proxy that plays `VibrationEffect` predefined effects with touch usage. It also maps the miuix widgets' HyperOS haptic ids (0x10000000 and up, via `miuix.view.HapticCompat.performHapticFeedback`) to standard `HapticFeedbackConstants`.
- The vibrator HAL itself is Xiaomi's (`sys.haptic.version=2.0`, linear motor) and supports click, tick and heavy click.

### Video recording

- `RecorderController` (`com.android.camera.module.video.z`) builds Xiaomi's recorder:
  - `iq.t` MiMediaCodecRecorder for normal video.
  - `iq.r` MiCinematicRecorder for Movie. It refers to `android.media.AudioParaManger`; the module's stand-in is added to the camera's dex path (`BaseDexClassLoader.addDexPath` with the module APK), because ART resolves app classes natively and a `findClass` hook isn't consulted.
- The video quality setup (`video.E`) picks sizes, including 4096x3072 for Open Gate.

## 3. This repo's pieces

### KernelSU module (`module/`)

| File | Does |
| --- | --- |
| `customize.sh` | Checks the device (houji), KernelSU and Android 15+. Takes the camera APK from the zip or `/sdcard/Download` and checks its SHA-256 against `known-apks.sha256`. |
| `post-fs-data.sh` | Before zygote: bind-mounts the APK over `/system/priv-app/MiuiCamera/MiuiCamera.apk` and sets the camera's KernelSU profile so module files stay visible to it. |
| `common.sh` | Shared helpers: APK hash check, package-cache clearing, KernelSU profile edits. |
| `sepolicy.rule` | Lets the Xiaomi-signed camera (`priv_app_36`) reach the background save service and the camera HAL, as the ROM's platform-signed camera can. |
| `uninstall.sh` | Restores the camera's KernelSU profile and clears cached package data. |
| `known-apks.sha256` | The tested camera builds. |

### LSPosed module (`lsposed/src/dev/spundone/xcam17/`)

| Class | Does |
| --- | --- |
| `Entry` | Load order. Build-independent fixes: the client-name tag, consent region `GLOBAL`, profile substitution, lens switching while recording, front Dolby 4K. |
| `CameraBuild` | Per-build table of obfuscated names. The build is recognised by its consent enum; unknown builds get only the build-independent hooks. |
| `DeviceFeatures` | Profile flag answers (Boolean, int, String, int[] appended, List). |
| `AnnexBVideo` | Keeps the encoder on Annex-B output, so AOSP's muxer writes valid files from Xiaomi's recorder. |
| `SaliencyOff` | Keeps the saliency checker from loading houji's older AI library, which crashed. |
| `FillLight60` | Holds 60 fps while the front screen fill light is on. |
| `NaturalStyle` | Adds Style ORIG to the HAL style mask. |
| `MutexDrop` | Lets 50MP and Style work together. |
| `WatermarkSubimage` | Frame watermarks in HEIF: drops remover data when there is no covered area. |
| `AudioParaStub` + `android/media/AudioParaManger` | A stand-in for HyperOS's audio parameter manager, added to the camera's class path, so Movie mode's own recorder loads. |
| `StreetFocus` | Fastshot's AF/MF: an empty depth-of-field table instead of a missing one, so the camera's default values apply. |
| `UltraHdLenses` | 50MP lens buttons and switching in Photo and Pro, and 1:1 at 50MP. |
| `UltraHdrJpeg` | Estimated Ultra HDR gain map appended to JPEG photos. |
| `VideoFilterLuts` | Hides video filters the phone has no colour table for. |
| `OpenGate` | Answers the HAL's Open Gate capability for rear cameras. |
| `CameraHaptics` | Plays the camera's haptics with Android's standard effects (see below). |

### Build and release

`scripts/build.sh` builds both pieces; see the README. `--apk FILE` writes a local-only zip that includes the camera APK. Never publish that one.

## 4. Getting the camera APKs

The APK is Xiaomi's and isn't in this repo. Pull it from the matching phone's firmware, or get it from a camera-app channel; for example, the 6.8 Leica camera from the 18 Pro was posted at https://t.me/VoyagerMIUIUpdate/17368. Check the SHA-256 against `module/known-apks.sha256` before installing; the installer refuses anything else:

| Build | Phone | SHA-256 |
| --- | --- | --- |
| 6.8.002090.2 | Xiaomi 18 Pro, CN | `729b0b193d1684e7c14ed0ca56ba15247d58016c70164329de233b49727a2c48` |
| 6.7.000280.0 | Xiaomi 17, CN | `1cec1ff2203d9d1c358b7ea24f6db5b62adeb5d3f6ceca48b0fa1df932d685d9` |

A new build needs its own `CameraBuild` entry before the name-based hooks apply.

## 5. How to investigate

- **Disassemble** with the SDK's `dexdump -d` on each `classes*.dex`. Method headers look like `|[addr] class.method:(sig)`. Log strings aren't obfuscated and are the best anchors: find the string, then the nearest preceding method header.
- **Resources:** `aapt2 dump resources` maps English strings to resource ids. In code, ids appear as `#7f14xxxx` constants.
- **Logs:**
  - `adb logcat -b all` right after an action.
  - Camera tags start with `MCAM_`; the HAL logs as `CamX` / `ChiX`.
  - Crashes are in `adb logcat -b crash`, and native ones in `/data/tombstones`.
  - The module's own log is in `/data/adb/lspd/log/modules_*` (lines start with `xcam17:`).
- **Swallowed exceptions:** when nothing is logged, a temporary hook that logs `getThrowable()` after a method finds them. That's how the Movie mode cause showed up.
- **Stuck threads:** `kill -3 <camera pid>` writes stack traces to `/data/anr`.
- **Comparing with 6.7:** the same logic usually exists under other names. Match methods by their log strings, then compare 6.7's Houji profile values.
- **Driving the UI:** `uiautomator dump` gives element bounds and content descriptions for `input tap`. Leave the phone alone while a script drives it.
- **Settings are in** `/data/data/com.android.camera/shared_prefs/`: `camera_settings_global.xml`, and per mode `camera_settings_simple_mode_local_0.xml`.
