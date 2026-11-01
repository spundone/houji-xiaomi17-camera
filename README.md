# Xiaomi 17 camera for the Xiaomi 14 (houji)

Runs Xiaomi's newer camera app on AOSP-based ROMs for the Xiaomi 14, in place of the older MiuiCamera the ROM ships. Two CN builds are supported: **6.8.002090.2** from the Xiaomi 18 Pro (recommended) and **6.7.000280.0** from the Xiaomi 17. You get the newer interface, Leica looks and filters, lens switching while recording (including 4K60 and 4K30 Dolby Vision), and switching to the front camera mid-recording. With 6.8 you also get film grain, a Leica-off style and 50MP in Photo mode.

It's two pieces that work together:

| Piece | What it does |
| --- | --- |
| `xiaomi17_camera-<version>.zip` (KernelSU module) | Swaps the ROM's camera APK for the Xiaomi 17 one at boot, adds the SELinux rules it needs, and stops KernelSU hiding the swap from the camera |
| `xcam17-lsposed-<version>.apk` (LSPosed module) | Patches the camera at runtime: skips the China-only consent screen, fixes a HAL crash at 2x, unlocks lens and front-camera switching while recording, and makes Xiaomi's recorder write standard video files |

Xiaomi's camera APK is not included. You supply it.

## Tested on

- Xiaomi 14 (houji), Infinity X 4.0, Android 17, kernel 6.1
- KernelSU Next 3.4.2 with the Hybrid Mount metamodule
- Vector (LSPosed fork) 2.2
- MIUI Gallery 4.3.1.15-global from the "Miui Gallery Global" KernelSU module ([docs/miui-gallery.md](docs/miui-gallery.md)), optional
- MiuiCamera 6.8.002090.2 (Xiaomi 18 Pro), SHA-256 `729b0b193d1684e7c14ed0ca56ba15247d58016c70164329de233b49727a2c48`
- MiuiCamera 6.7.000280.0 (Xiaomi 17), SHA-256 `1cec1ff2203d9d1c358b7ea24f6db5b62adeb5d3f6ceca48b0fa1df932d685d9`

Other houji ROMs should work if they ship Xiaomi's camera stack (`/system/priv-app/MiuiCamera` plus the stock camera HAL). The installer refuses other devices and other camera builds.

## What works

| Feature | Status |
| --- | --- |
| Photo (main, 0.6x, 2x, 3.2x), saved through Xiaomi's background processing | Works |
| Leica Vibrant / Authentic, filters, watermarks | Works |
| Video up to 4K60, Dolby Vision (profile 8.4, HLG) | Works |
| Switching lenses while recording, including 4K60 and 4K30 Dolby Vision | Works |
| Switching between rear and front camera while recording (720p, 1080p, 4K30) | Works, with a ~0.2 s hitch at the switch |
| Dolby Vision toggle in the viewfinder's quick menu (17 Ultra layout), instead of in Settings | Works |
| Dolby Vision on the front camera at 1080p30 and 4K30 | Works |
| Live moments (motion photos with a 3 s clip) from the snapshot button while recording | Works |
| Subject tracking focus in Pro photo and Pro video, not just Photo and Video | Works |
| Film grain (Noise), with the 18 Pro's filter parameters, palette and presets | Works (6.8) |
| Style ORIG (no Leica look) beside Leica Vibrant and Authentic | Works (6.8) |
| 50MP Photo Resolution toggle in Photo mode, keeping Leica Authentic or Style ORIG | Works (6.8) |
| Front screen fill light at a steady 60 fps | Works (6.8) |
| Watermark shows "Xiaomi 14" | Works (6.8) |
| Watermarks with HEIF photos (crashed before) | Works (6.8) |
| 50MP on the ultra-wide, main and telephoto, in Photo (4:3 or 1:1) and Pro | Works (6.8) |
| 50MP RAW (DNG) in Pro on all three lenses, and 12.5MP RAW on the telephoto (both saved empty files before) | Works (6.8) |
| 50MP zoom works as in Pro: the lens changes at each stop (0.6x, 1x, 3.2x), no 2x | Works (6.8) |
| Telemacro: Super Macro in Photo's menu uses the telephoto | Works (6.8) |
| Video filters limited to the ones the phone has colour tables for | Works (6.8) |
| Video filters up to 4K (default 1080p30) | Works (6.8) |
| Front camera video filters (Xiaomi 14's own set: Mild, Fresh, KC64, H400 and others) | Works (6.8) |
| Open Gate video (Pro video, 4096x3072 at 30/60 fps) | Works (6.8) |
| Dual video (front and rear together) | Works (6.8) |
| Haptics (shutter, zoom, mode switch, dials, sliders) | Works (6.8) |
| Fastshot (Street) mode, with 28 / 35 / 50 / 75 mm presets | Works (6.8) |
| Portrait zoom buttons 1x and 2.6x, and 1/8000 s shutter in Pro (Xiaomi 14's own profile values) | Works (6.8) |
| Short video (swipe up > Short video) | Partly (6.8): records and saves; zoom and multi-clip recording don't work |
| Ultra HDR JPEG photos, with an estimated gain map (set the photo format to JPEG) | Works (6.8) |
| Ultra HDR HEIF photos (opt-in with the module's Action button in KernelSU; Google Photos can't back them up, and macOS and ffmpeg can't open them) | Works (6.8) |
| Movie mode (swipe up on the viewfinder > Movie) | Works (6.8), 1080p 24 fps |
| Live moments while recording, tracking focus in Pro photo | Works (6.7 and 6.8) |
| Import LUT for Pro video Log | Works (6.8) |
| Essential Moment (Leica Essential): M9 Iconic CCD, M3 B&W film and M10R Modern vintage looks, rendered in the app by the Leica port's processor (see Install, step 2) | Works (6.8), JPEG, about 3 to 5 s per photo |

## Install

1. Root with **KernelSU** (KernelSU Next works) and install an **LSPosed** framework (Vector or LSPosed).
2. Copy a supported camera APK (6.8.002090.2 or 6.7.000280.0) to the phone's `Download` folder. [docs/houji-camera.md](docs/houji-camera.md#4-getting-the-camera-apks) says where to find them and lists their SHA-256.
   Optional, for Essential Moment's Leica looks: also copy the community Leica port of camera 6.8 (package `com.kobbin.leica`, 6.8.001960.0; tested SHA-256 `b2bf1186e6c0004621e19d914feb1d0e8106e2d976edc0634e01c30d6bc90082`). The installer copies the port's processor, libraries and models from it into the module; the port app itself doesn't need to be installed. Updates keep the copied files if the APK is no longer there.
3. In the KernelSU manager, install `xiaomi17_camera-<version>.zip`. It checks the device and the APK's SHA-256, then installs the LSPosed module it carries (and, with Vector, enables it for the camera).
4. With an LSPosed build other than Vector, enable **Xiaomi 17 Camera for houji** in LSPosed. Its scope is set to the camera already.
5. Reboot. The camera gets an adaptive icon on AOSP launchers; this needs a KernelSU mount module (such as hybrid_mount) to mount the module's system/ folder.

From a computer with Vector, steps 4 and 5 can be scripted:

```bash
adb install xcam17-lsposed-1.8.0.apk
```

```bash
adb shell su -c '/data/adb/modules/zygisk_vector/cli modules enable dev.spundone.xcam17 && /data/adb/modules/zygisk_vector/cli scope set dev.spundone.xcam17 com.android.camera/0'
```

```bash
adb reboot
```

## Uninstall

Remove the module in the KernelSU manager and reboot. The uninstaller restores the camera's default KernelSU profile and clears the cached package data, so the ROM's own camera comes back. Then uninstall the LSPosed module.

## Known limitations

- **Front camera while recording** isn't offered at 4K60, or with Dolby Vision at 4K, matching Xiaomi's own rules. Switching drops about 0.2 s of video at the switch point.
- **All video now goes through Xiaomi's own recorder** (needed for the front-camera switch). Every mode was tested to produce valid files: 720p30, 1080p30/60, 4K24/30/60, with and without Dolby Vision.
- **4K60 lens switching isn't advertised by the hardware.** The Xiaomi 14's camera HAL lists multi-lens video for 720p, 1080p30, 1080p60, 4K24 and 4K30, not 4K60. The LSPosed module overrides that. In testing the lens changed cleanly and files stayed 3840x2160 at about 58 fps, but long 4K60 clips may run hot.
- **Camera 6.8 runs on a shared profile.** It has no Xiaomi 14 profile, so the module gives it Xiaomi's generic flagship profile plus the flags above. Some 18 Pro features stay off: the separate Ultra-pixel mode, and Essential Moment unless the Leica port is installed (its looks are otherwise rendered by HAL support the Xiaomi 14 lacks). There's no 25MP option; this sensor offers 12.5MP and 50MP.
- **Switching camera builds** resets the camera's saved mode. If the camera closes with "invalid module index" after switching, clear its data (`pm clear com.android.camera`).
- **The telephoto falls back to the main camera up close** in Photo and Portrait (12.5MP), below the telephoto's focus distance. Settings > Adaptive telephoto (off by default) now reaches the HAL. 50MP opens the telephoto directly.
- **No Original portrait.** Portrait offers Master and Leica portrait; Original portrait needs HAL support houji lacks and rendered 2.6x from the main camera.
- **Not possible on this HAL:** Open Gate in normal Video mode (Pro video has it), Movie at 4K, video HDR at 4K60 or on the front camera. Each crashed the camera service when forced.
- **Grain and Leica-off need 6.8.** Camera 6.7 has no UI for either.
- **Essential Moment** renders each look in the app after the shot, which takes about 3 to 5 seconds, and only for JPEG photos (Settings > Photo format). The port's AI style model and person mask need the Snapdragon 8 Elite Gen 5's DSP, so on the Xiaomi 14 its fallback tone is used, without a toast. Watermarks work: the camera draws its own over the finished look.
- **HDR vs Dolby Vision.** They're separate. Dolby Vision records 10-bit HLG video with Dolby Vision profile 8.4 metadata. The HDR tile is multi-frame video HDR: it merges exposures for a wider dynamic range but, with Dolby Vision off, still records standard 8-bit SDR (BT.709). They can be on together.
- **Region.** The CN build's China-only extras stay on. Only the consent check is redirected.
- **The camera app shows Xiaomi's terms on first launch.** Accepting them is up to you.

## Build

Needs JDK 11+ and an Android SDK with `platforms;android-34` and build-tools.

```bash
scripts/build.sh
```

Artifacts land in `dist/`. Options:

- `--release` signs with the key named by `XCAM17_KEYSTORE`, `XCAM17_KEYSTORE_PASS` and `XCAM17_KEY_ALIAS`.
- `--apk FILE` also writes a `-with-apk.zip` that carries a camera APK, for installing without copying it to the phone first. It contains Xiaomi's binary, so never publish it.

Releases are built by GitHub Actions when a `v*` tag matching `VERSION` is pushed.

## How it works

- [docs/how-it-works.md](docs/how-it-works.md): each problem found while porting, and the evidence for each fix.
- [docs/houji-camera.md](docs/houji-camera.md): how the Xiaomi 14's cameras, HAL and the camera app fit together, what each file in this repo does, where to get the camera APKs, and how to investigate.
- [docs/roadmap.md](docs/roadmap.md): what's still missing, why, and what to try next.
- [docs/miui-gallery.md](docs/miui-gallery.md): the MIUI Gallery module used alongside, and how the camera works with it.
- [docs/aosp-integration.md](docs/aosp-integration.md): how this could ship inside an AOSP ROM build without root, based on how existing houji ROM trees ship Xiaomi's camera.

## Legal

Not affiliated with Xiaomi or Leica. The camera app and its trademarks belong to Xiaomi. This repo contains only original scripts and hooks. The Leica port's code, libraries and models belong to their authors and to Xiaomi; the installer copies them from your own copy of the port and they are never part of a release.

### Release signing

Release APKs are signed with a dedicated key (certificate SHA-256 `9bdc6988c94df28a23c372d6670414588601061a3464ca7fb66d0637e9fa017a`). Every future version has to be signed with the same key, or it won't install over the previous one. The key lives outside the repo at `~/.config/xcam17/release.jks`, with its password in the macOS Keychain as `xcam17-release-keystore`. CI gets it from the repository secrets `XCAM17_KEYSTORE_B64`, `XCAM17_KEYSTORE_PASS` and `XCAM17_KEY_ALIAS`.

To release: bump `VERSION`, add a `CHANGELOG.md` entry, then push a matching `v*` tag. To check signing without publishing, run the release workflow by hand (Actions → release → Run workflow): it builds, signs and uploads the files as a workflow artifact.
