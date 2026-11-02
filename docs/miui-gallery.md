# MIUI Gallery alongside the camera

The camera hands photos to Xiaomi's Gallery: the thumbnail next to the shutter opens Gallery's photo view, and Gallery's editor and watermark tools expect Xiaomi's metadata. On the test phone this runs with a separate KernelSU module that installs MIUI Gallery on the AOSP ROM. The camera doesn't depend on it, and releases don't ship it; a locally built KernelSU-only zip can install it alongside (`scripts/build-kit-zip.sh --gallery`).

## The module

**Miui Gallery Global** by Hamido, from https://t.me/AMRGAMAL_STORE/10120. Its description reads "Run MIUI Gallery 4.3.1.15-global, MIUI Gallery Editor LION-2.3.0.5-global & HyperOs Account R-25.10.23.03 on Android 16/17 AOSP custom ROMs".

Installed on the test phone (Infinity X 4.0, Android 17):

| App | Package | Version | Path |
| --- | --- | --- | --- |
| MIUI Gallery | `com.miui.gallery` | 4.3.1.15-global | `/system/priv-app/MIUIGallery` |
| Gallery Editor | `com.miui.mediaeditor` | LION-2.3.0.5 | `/system/priv-app/MiMediaEditor` |
| HyperOS Account | `com.xiaomi.account` | R-25.10.23.03 | `/system/priv-app/HyperOSAccount` (with `libmicontinuity_sdk.so`) |

What the module does:
- Adds the three apps under `system/priv-app` through the module mount. It also has empty `MiuiGallery` folders under `system/priv-app` and `system_ext/priv-app`.
- Grants Gallery and the Editor privileged permissions (`WRITE_SECURE_SETTINGS`, `INTERACT_ACROSS_USERS`, `WRITE_MEDIA_STORAGE`, and for Gallery `START_FOREGROUND_SERVICES_FROM_BACKGROUND`) in `system/etc/permissions/privapp-permissions-miuicamera.xml`.
- Sets `ro.control_privapp_permissions=log`, so a missing privileged-permission entry is logged instead of stopping boot.
- Clears `/data/system/package_cache` in `post-fs-data.sh`, so the package manager picks the apps up.

## Making it visible to the apps that use it

KernelSU's default app profile unmounts module files for apps that aren't granted root, the same problem the camera had (see how-it-works.md, section 2). The Gallery module doesn't handle this, so on the test phone the profile's `umount_modules` was set to `false` by hand, with `ksud profile`, for these apps:
- `com.miui.gallery`
- `com.miui.mediaeditor`
- `com.xiaomi.account`
- `com.android.systemui`
- `com.android.launcher3`
- `com.android.settings`
- `com.android.permissioncontroller`
- `com.android.intentresolver`
- `com.android.documentsui`

The camera module sets this on every boot for the camera and the launcher, and, when the MIUI Gallery module is installed, for all the apps above. Without it, MIUI Gallery closes on start ("keeps stopping": `Resources` is null in `GraphicsEnvironment.queryAngleChoice`, because the process can't open its own APK). KernelSU forgets a profile when its app is uninstalled, which happens while the Gallery module is disabled, so a profile set once by hand doesn't last.

## How the camera uses Gallery

- **Thumbnail:** tapping the most-recent-photo button opens `com.miui.gallery/.activity.ExternalPhotoPageActivity`. The camera also binds Gallery's service (`com.miui.gallery.permission.BIND_SERVICE`, logged as `MCAM_GalleryHelper`) and lists Gallery and the Editor among the packages it queries.
- **Saving** doesn't need Gallery. Photos go through MediaStore and Xiaomi's background save service in the camera HAL.
- **Watermarks:** Gallery can remove or change a watermark later using the remover data the camera writes. For frame watermarks this module drops that data (see `WatermarkSubimage`), so Gallery can't strip those afterwards. Other watermarks keep it.
- **Ultra HDR:** the estimated gain map `UltraHdrJpeg` adds to JPEGs is standard Android Ultra HDR. Whether MIUI Gallery 4.3.1.15-global shows it brighter on the HDR screen hasn't been checked; Google Photos does read the format.

## Notes

- The Gallery APKs are Xiaomi's, so this repo doesn't redistribute them, just as it doesn't redistribute the camera.
- Without MIUI Gallery the camera still works. The thumbnail then opens whatever app handles the review intent, if one does; that hasn't been tested.
