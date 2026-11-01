# What's missing, and what to try next

What's known not to work on the Xiaomi 14 with camera 6.8, why, and the next step for each. [houji-camera.md](houji-camera.md) explains the names used here.

## Could be done

| Item | Why it's missing | Next step |
| --- | --- | --- |
| **Short video zoom and multiple clips** | Short video (MiLive, mode 183) now opens and records, on the main camera in a standard session (the HAL refused its 0x8004 video session). Its zoom slider moves but the camera doesn't follow, and the second shutter press stops and opens the review instead of pausing for another clip. | Compare with 6.7's MiLive on houji (which camera and session it opened, how zoom reached the request), and check whether the module expects the multi-lens camera (5) for both. |
| **Leica styles and photo filters on the front camera** | `r2.m.u` builds the Style list only for camera index 0, and `y3.a.r` sends `stylizationType` only for back-facing cameras. The front HAL reports the same style mask (6). | Answer camera index 0 inside `r2.m.u` and the facing check inside `y3.a.r` for the front, then check whether the front pipeline applies the look. |
| **Front pinch zoom** | The front zoom comes from Smart FOV (`v2.o0`, profile `j5`), which gives Photo a fixed toggle and Video nothing. The HAL allows up to 10x digital zoom on the front. | Answer `j5` with plain zoom for 162/163 on the front and test. |
| **50MP RAW on the ultra-wide and telephoto** | Pro picks the RAW size from `l9.g.s()`, which gives the telephoto a 3248x2448 crop; the HAL lists 8160x6144 RAW16 for both. | Return the full size in the 50MP session; check the DNG metadata. |
| **Lens switching while recording in Pro video** | Not looked at yet. | |
| **Remove-watermark data for frame watermarks in HEIF** | `WatermarkSubimage` drops the remover data when the watermark covers nothing (the HEIF path crashed without it), so the Gallery can't strip frame watermarks later. JPEG behaves the same. | Low value. Fill the missing source of the XMP helper (`Qg.r`) instead of dropping the data. |
| **Movie mode audio effects** | Movie now uses its own recorder with a stand-in for HyperOS's `AudioParaManger`, which does nothing; the zoom- and focus-following audio needs HyperOS's audio stack. | Low priority; would need Xiaomi's audio HAL and framework. |
| **Leica watermark in Essential Moment** | The port's processor draws the Leica watermark through reflection on its own camera build's task (fields `l`, `e`, `w` and others of `Sh.t`); the stand-in `LeicaLegendary` passes doesn't have them, so the step is skipped. | Fill those fields on the stand-in from 6.8.002090.2's task (the watermark classes `U8.d`, `zi.a` have the same names in both builds), or let the camera's own watermark run on the processed JPEG. |
| **Leica styles in software in Photo, Pro and Portrait** | The port also renders the Leica Vibrant / Authentic choice and a saved Pro palette in software (`PalettePostProcessor`). houji's HAL already renders Vibrant and Authentic, so it wasn't ported. | Only worth it for looks the HAL lacks. |
| **Haptic fine-tuning** | `CameraHaptics` uses Android's predefined click/tick effects, chosen per action; HyperOS uses Xiaomi's own effect ids. | Tune the per-action effects on the device, or use composed primitives (the HAL supports COMPOSE_EFFECTS) for closer matches. |

## Needs the phone's camera HAL

These depend on support the Xiaomi 14's 2024 camera HAL doesn't have. A module can't add it.

| Item | What's missing |
| --- | --- |
| **Real Ultra HDR / HDR display photos** | The HAL never produces Xiaomi's XDR gain map, even with `xiaomi.snapshot.XDREnable` requested. The module's gain map is an estimate. |
| **Leica Legendary / Leica Essential without the Leica port** (M9 CCD, M3 film, M10R looks) | The looks are rendered by the HAL through `com.xiaomi.sessionparams.legendMode`, which houji doesn't define. With the community Leica port in Download they are rendered in the app instead (`LeicaLegendary`). |
| **Open Gate in Video mode** | Video mode's recording pipeline aborts the camera service at 4096x3072, on the multi-lens camera and on the main camera alone. Pro video's Open Gate works. |
| **Movie at 4K, video HDR at 4K60 or on the front** | The HAL lists none of them (video bokeh stops at 1080p30, HDR at 4K30, front HDR unsupported); forcing them crashed. |
| **Telephoto in dim light (12.5MP)** | The HAL falls back to a main-camera crop on its own. The app's disable request (`com.xiaomi.teleFallback.isDisable`) isn't listed for the multi-lens camera; letting it through hasn't been seen to change anything. |
| **MasterCinema (cinelook)** | The HAL reports `supportedfeatures.cinelook` = 0 on every camera. |
| **Clone mode** | The QTI camera provider aborts (`Usecase::DumpSystemEvent`), then `configureStreams` fails with -38. |
| **LOFIC Ultra HDR video** | Needs the 17 Ultra's LOFIC sensor. |
| **200MP telephoto Ultra HD** (`REARx7`, flags `n3` / `o3`) | The 18 Pro's 200MP periscope; houji's telephoto is 50MP. |
| **Adaptive macro** (near-range fallback) | The switch to the close-focus lens happens inside the HAL's multi-lens camera; the app only shows a setting and reads the result. houji's HAL reports `xiaomi.smoothTransition.nearRangeMode` as 0 on every camera, so the setting would do nothing. Telemacro (Super Macro) covers close-ups instead. |
| **16:9, full screen and widescreen at 50MP** | In the Ultra HD session the HAL offers only 4:3 sizes (8192x6144, 8160x6144, 8160x6120); the 8192x4608 it lists elsewhere isn't available there. The camera crops 1:1 from the 4:3 frame after capture, but has no wider crop. Cropping and re-encoding after capture would work but lose quality, so wider ratios still switch 50M off. |

## Needs Xiaomi's data or services

| Item | What's missing |
| --- | --- |
| **Live cinematography** (`l3`, mode 231) | Crashes on entry with a null configuration map; this phone has none of its configuration data (6.7's Master Live mode failed the same way). |
| **Sticker avatars** (`P3`) | Downloads its content from Xiaomi's cloud. |

## Tested and fine as is

These came up as worries and checked out:
- Log LUTs (Rec.709, Classic, Cool, Import LUT): render and record normally.
- Open Gate at 60 fps: every frame 16.7 ms apart.
- Dual video doesn't affect normal 4K Dolby Vision recording.
- The telephoto is already part of Photo's auto-switching logical camera ("Adaptive telephoto" in Settings controls it).

## Building it into ROMs

Shipping the camera in a ROM without KernelSU or LSPosed means turning each hook into a patch of the APK, re-signed with the platform key, plus a device-tree folder. [aosp-integration.md](aosp-integration.md) has the research and a proposed `aosp/` kit (about one to two weeks of work).

## Not yet looked at

- Pocket mirror (mode 224, flag `e5`): a one-flag change, untested.
- A Global (non-CN) 6.8 build, which would avoid the CN-only extras and the consent redirect.
- Newer camera builds: each needs a `CameraBuild` entry and a fresh check of the flags above.

Checked and already working without changes: tap-to-focus subject lock in Movie (`cinematicIntellFocus`), motion tracking focus in Video (`videoMiTrackAF`, greyed out at 4K60 and with Dolby Vision), and custom photographic styles (`ciqlutconfig`).
