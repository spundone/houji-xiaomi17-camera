# What's missing, and what to try next

What's known not to work on the Xiaomi 14 with camera 6.8, why, and the next step for each. [houji-camera.md](houji-camera.md) explains the names used here.

## Could be done

| Item | Why it's missing | Next step |
| --- | --- | --- |
| **Front-camera video filters** | The 18 Pro's front filters (Poetry, Nocturne, Amber and others) need colour tables the Xiaomi 14's firmware doesn't have. They're hidden for now. | Get the tables from an 18 Pro (`/odm/etc/camera/videofilter/<id>_<Name>.png`, ids such as 136 and 138) and ship them in the KernelSU module as an overlay of that folder. Then `VideoFilterLuts` shows them again on its own. |
| **Ultra HDR for HEIF photos** | `UltraHdrJpeg` only handles JPEG, because Android's bitmap encoder can't write a HEIF gain map. | Android 15+ can write HEIC with a gain map through its HEIF Ultra HDR support. Try re-wrapping the HEIF with a gain map item, keeping Xiaomi's metadata. |
| **Remove-watermark data for frame watermarks in HEIF** | `WatermarkSubimage` drops the remover data when the watermark covers nothing (the HEIF path crashed without it), so the Gallery can't strip frame watermarks later. JPEG behaves the same. | Low value. Fill the missing source of the XMP helper (`Qg.r`) instead of dropping the data. |
| **Fastshot / Street mode** | Flag `c2` didn't add the mode in an earlier test. | The HAL does define `com.xiaomi.streetshoot.enabled` and `com.xiaomi.street.mode`. Look for further gates on mode 225 (another flag, or a top-config list). |
| **Adaptive macro** | Settings' "near range fallback" (`pref_camera_near_range_fallback_key`) switches to the telephoto at close range. It's gated by a HAL capability, not a profile flag, and the tag wasn't identified. | Resolve the tag behind `l9.h.p5` / `l9.h.r5` (field `ia.v0.j1`) and check whether houji declares it. |
| **Movie mode audio effects** | Movie uses the regular recorder, because MiCinematicRecorder needs `android.media.AudioParaManger`, a HyperOS-only class. Its zoom and focus audio effects are lost. | Provide a stub `AudioParaManger` (and its listener interfaces) on the camera's class path, then let Movie use its own recorder. The effects themselves would still need HyperOS's audio HAL. |

## Needs the phone's camera HAL

These depend on support the Xiaomi 14's 2024 camera HAL doesn't have. A module can't add it.

| Item | What's missing |
| --- | --- |
| **Real Ultra HDR / HDR display photos** | The HAL never produces Xiaomi's XDR gain map, even with `xiaomi.snapshot.XDREnable` requested. The module's gain map is an estimate. |
| **Leica Legendary / Leica Essential** (M9 CCD, M3 film, M10R looks) | The looks are rendered by the HAL through `com.xiaomi.sessionparams.legendMode`, which houji doesn't define; photos come out unprocessed. |
| **Clone mode** | The QTI camera provider aborts (`Usecase::DumpSystemEvent`), then `configureStreams` fails with -38. |
| **LOFIC Ultra HDR video** | Needs the 17 Ultra's LOFIC sensor. |
| **200MP telephoto Ultra HD** (`REARx7`, flags `n3` / `o3`) | The 18 Pro's 200MP periscope; houji's telephoto is 50MP. |
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

## Not yet looked at

- 6.8 features behind HAL sections houji does have:
  - `cinematicIntellFocus`
  - `ciqlutconfig`
  - `objectTracking*`
  - `videoMiTrackAF`
  - `xiaomi.video.cinelook`
  - `supermoon`
  - `pro.video.log`
- A Global (non-CN) 6.8 build, which would avoid the CN-only extras and the consent redirect.
- Newer camera builds: each needs a `CameraBuild` entry and a fresh check of the flags above.
