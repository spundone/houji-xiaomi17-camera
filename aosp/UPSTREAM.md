# Draft: submitting the kit upstream

Nothing has been submitted. This is a draft PR description, plus where it could go and what each candidate would need. Pick a target first: the description changes with it.

## Candidate repos

| Repo | Fit | What it would need |
| --- | --- | --- |
| [Nissivardhan/android_device_xiaomi_houji-miuicamera](https://github.com/Nissivardhan/android_device_xiaomi_houji-miuicamera) (lineage-24.0) | Best. It already ships MiuiCamera on houji for Android 17, extracts and shims the JNI libraries 6.8 loads, and carries 4 of the same fixes (consent, NAL length, client name ×2) | A 6.8 APK source: they extract from houji's own HyperOS firmware, which has 6.4. 6.8 comes from the Xiaomi 18 Pro, which isn't a public houji dump, so it may not meet LineageOS's "blobs from a public stock image" rule. Either a separate branch/flavour or a `MiuiCamera68` option. Their patches are `git am` smali diffs applied by `apktool_patch`; ours are `patches.json`, so they'd need `xcampatch.py` called from `extract-files.py` (`blob_fixup().call(...)`), or the patches re-expressed as smali diffs (which puts decompiled code in the repo) |
| [Nissivardhan/android_vendor_xiaomi_houji-miuicamera](https://github.com/Nissivardhan/android_vendor_xiaomi_houji-miuicamera) | Goes with the device repo | The patched 6.8 APK as a blob. That's Xiaomi's binary; publishing it is their call, not this project's |
| Infinity X's houji device tree | Through the repo above | Infinity X 4.0 on the test phone ships Nissivardhan's houji-miuicamera tree: identical props and files, read over adb. A change there reaches Infinity X when its maintainer syncs; Infinity X itself would only need the `-include`/`inherit-product` lines if the 6.8 option is a separate tree |
| [Evolution-X-Devices/device_xiaomi_miuicamera](https://github.com/Evolution-X-Devices/device_xiaomi_miuicamera), [vherawidatama/vendor_xiaomi_camera](https://gitea.com/vherawidatama/vendor_xiaomi_camera) | Weaker | Shared trees for several Xiaomi phones, older camera builds (lineage-22.2). 6.8's houji fallbacks (CommonFlagship profile, houji's 6.7 flag values) are houji-specific |

The kit is self-contained, so it can also stay here: a ROM maintainer copies `aosp/device/` and runs `patch-apk.sh` themselves.

## Draft PR description (for the houji-miuicamera device repo)

**houji-miuicamera: optional MiuiCamera 6.8.002090.2 (Xiaomi 18 Pro)**

Adds an option to ship Xiaomi's camera 6.8.002090.2 instead of houji's 6.4. It keeps this tree's JNI libraries, shims and sepolicy, and swaps the APK.

6.8 has no houji profile and expects HyperOS pieces AOSP lacks, so it's patched:

- client name on every capture request, Annex-B video, consent region GLOBAL (as this tree already does for 6.4);
- the missing-profile check cleared and Xiaomi's CommonFlagship profile used instead, with houji's own 6.7 values for lenses, 50MP sizes, macro and filters;
- the native saliency checker off (it crashes against houji's `libmialgo_ai_vision.so`);
- lens switching while recording, front Dolby Vision 4K30, Open Gate in Pro video, video filters up to 4K, Fastshot, Short video, watermark/HEIF and fill-light fixes.

Patches are listed in `patches.json` and applied by `xcampatch.py`, which finds methods by class, name, prototype and fingerprint strings and refuses to patch anything that doesn't match exactly. The hook logic lives in a small helper dex added to the APK. No decompiled code is committed.

Each fix was tested on houji (Infinity X 4.0, Android 17) as an LSPosed hook on the same camera build, as part of [houji-xiaomi17-camera](https://github.com/spundone/houji-xiaomi17-camera) v1.7.0. The patched APK hasn't been tested platform-signed in a ROM build yet.

Not included: 50MP lens switching at the zoom stops, Ultra HDR JPEG, haptics.

## Open questions before submitting

- Does 6.8 behave differently platform-signed (Xiaomi account and cloud features, stickers)?
- Do the maintainers accept a camera build that isn't from houji's own firmware?
- Should the tree get its own SELinux domain (as PocoF3Releases does through `seapp_contexts`) instead of widening `platform_app`?
