# Xiaomi 17 camera for the Xiaomi 14 (houji)

Runs Xiaomi's camera app from the Xiaomi 17 series (MiuiCamera **6.7.000280.0**, CN build) on AOSP-based ROMs for the Xiaomi 14, in place of the older MiuiCamera the ROM ships. You get the newer interface, Leica looks and filters, lens switching while recording (including 4K60 and 4K30 Dolby Vision), and switching to the front camera mid-recording.

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
- MiuiCamera 6.7.000280.0, SHA-256 `1cec1ff2203d9d1c358b7ea24f6db5b62adeb5d3f6ceca48b0fa1df932d685d9`

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

## Install

1. Root with **KernelSU** (KernelSU Next works) and install an **LSPosed** framework (Vector or LSPosed).
2. Copy the Xiaomi 17 camera APK (6.7.000280.0) to the phone's `Download` folder.
3. In the KernelSU manager, install `xiaomi17_camera-<version>.zip`. It checks the device and the APK's SHA-256 before installing.
4. Install `xcam17-lsposed-<version>.apk`, then enable **Xiaomi 17 Camera for houji** in LSPosed. Its scope is set to the camera already.
5. Reboot.

From a computer with Vector, steps 4 and 5 can be scripted:

```bash
adb install xcam17-lsposed-1.0.0.apk
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

[docs/how-it-works.md](docs/how-it-works.md) covers each problem found while porting, and the evidence for each fix.

## Legal

Not affiliated with Xiaomi or Leica. The camera app and its trademarks belong to Xiaomi. This repo contains only original scripts and hooks.

### Release signing

Release APKs are signed with a dedicated key (certificate SHA-256 `9bdc6988c94df28a23c372d6670414588601061a3464ca7fb66d0637e9fa017a`). Every future version has to be signed with the same key, or it won't install over the previous one. The key lives outside the repo at `~/.config/xcam17/release.jks`, with its password in the macOS Keychain as `xcam17-release-keystore`. CI gets it from the repository secrets `XCAM17_KEYSTORE_B64`, `XCAM17_KEYSTORE_PASS` and `XCAM17_KEY_ALIAS`.

To release: bump `VERSION`, add a `CHANGELOG.md` entry, then push a matching `v*` tag. To check signing without publishing, run the release workflow by hand (Actions → release → Run workflow): it builds, signs and uploads the files as a workflow artifact.
