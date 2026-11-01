package dev.spundone.xcam17;

import android.hardware.camera2.CaptureRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Runtime patches that let Xiaomi's newer camera (MiuiCamera 6.7 from the Xiaomi 17, or
 * 6.8 from the Xiaomi 18 Pro, CN builds) run on an AOSP ROM for the Xiaomi 14 (houji).
 *
 * The camera is obfuscated, so every hook first checks that the class and method it
 * targets still have the shape it was written against. On any other camera build the
 * hook is skipped and logged rather than guessed at.
 */
public class Entry implements IXposedHookLoadPackage {
    static final String CAMERA = "com.android.camera";
    private static final String TAG = "xcam17: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!CAMERA.equals(lp.packageName) || !lp.isFirstApplication) return;
        // These don't depend on the camera's obfuscated names.
        hookClientName(lp.classLoader);
        AnnexBVideo.hook();
        SaliencyOff.hook(lp.classLoader);

        CameraBuild build = CameraBuild.detect(lp.classLoader);
        if (build == null) {
            log("camera build not recognised: only build-independent hooks applied");
            return;
        }
        log("camera build: " + build.label);
        hookMissingProfile(lp.classLoader, build);
        hookConsentRegion(lp.classLoader, build);
        hookVideoLensSwitching(lp.classLoader, build);
        hookFrontDolby4k(lp.classLoader, build);
        DeviceFeatures.enable(lp.classLoader, build.profiles, build.features);
        FillLight60.hook(lp.classLoader, build);
        NaturalStyle.hook(lp.classLoader, build);
        MutexDrop.hook(lp.classLoader, build);
        WatermarkSubimage.hook(lp.classLoader, build);
        MovieRecorder.hook(lp.classLoader, build);
        UltraHdLenses.hook(lp.classLoader, build);
        UltraHdrJpeg.hook(lp.classLoader, build);
        VideoFilterLuts.hook(lp.classLoader, build);
    }

    /**
     * Qualcomm's camera HAL on houji reads the vendor tag com.xiaomi.sessionparams.clientName
     * from capture requests and passes it straight to strcmp(). HyperOS fills it in; on AOSP
     * nothing does, and the HAL crashes with a null dereference in
     * CamX::SensorNode::ExecuteProcessRequest (reliably at 2x in photo mode). The ROM's own
     * camera port fixes this by stamping its package name on every builder it creates; this
     * does the same from CameraDeviceImpl.createCaptureRequest, so it doesn't depend on the
     * camera's obfuscated names.
     */
    static void hookClientName(ClassLoader cl) {
        try {
            Class<?> device = cl.loadClass("android.hardware.camera2.impl.CameraDeviceImpl");
            XposedBridge.hookAllMethods(device, "createCaptureRequest", new StampClientName());
            log("client name: stamped on every capture request");
        } catch (Throwable t) {
            log("client name: not hooked: " + t);
        }
    }

    static final class StampClientName extends XC_MethodHook {
        static final CaptureRequest.Key<byte[]> CLIENT_NAME =
                new CaptureRequest.Key<>("com.xiaomi.sessionparams.clientName", byte[].class);
        // NUL-terminated, as the HAL reads it as a C string.
        static final byte[] VALUE = (CAMERA + "\0").getBytes(StandardCharsets.US_ASCII);
        private boolean warned;

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            Object builder = p.getResult();
            if (!(builder instanceof CaptureRequest.Builder)) return;
            try {
                ((CaptureRequest.Builder) builder).set(CLIENT_NAME, VALUE);
            } catch (IllegalArgumentException e) {
                // The HAL doesn't define the tag (another device or ROM): nothing to fix.
                if (!warned) {
                    warned = true;
                    log("client name: tag not defined by this HAL: " + e.getMessage());
                }
            }
        }
    }

    static void log(String s) {
        XposedBridge.log(TAG + s);
    }

    /**
     * The camera loads its device profile by name (com.mi.device.<Device>). When the build has
     * no profile for this phone (6.8 has none for houji), the loader falls back to the generic
     * Common profile and sets a static flag that Camera.onCreate treats as "wrong APK version":
     * it shows an error and finishes the activity three seconds later. The hook clears that
     * flag and, when the build names a fallback, swaps the generic profile for that one: the
     * bare Common profile turns off most modes, lens buttons and every video size above 1080p30.
     */
    static void hookMissingProfile(ClassLoader cl, CameraBuild build) {
        if (build.profileLoaderClass == null) return;
        try {
            Class<?> loader = cl.loadClass(build.profileLoaderClass);
            Method load = loader.getDeclaredMethod(build.profileLoaderMethod);
            Field missing = loader.getDeclaredField(build.profileMissingField);
            Field cache = null;
            for (Field f : loader.getDeclaredFields()) {
                if (f.getType() == load.getReturnType() && Modifier.isStatic(f.getModifiers())) cache = f;
            }
            if (missing.getType() != Boolean.class || !Modifier.isStatic(missing.getModifiers()) || cache == null) {
                log("profile: " + build.profileLoaderClass + " has an unexpected shape, skipping");
                return;
            }
            missing.setAccessible(true);
            cache.setAccessible(true);
            Class<?> fallback = null;
            if (build.profileFallback != null) {
                Class<?> c = cl.loadClass(build.profileFallbackStored);
                if (load.getReturnType().isAssignableFrom(c)) fallback = c;
            }
            XposedBridge.hookMethod(load, new ReplaceMissingProfile(missing, cache, fallback));
            log("profile: missing-profile version error suppressed"
                    + (fallback != null ? ", using " + build.profileFallback : ""));
            if (fallback != null) hookMarketingName(fallback, build.profileNamesMethod);
        } catch (Throwable t) {
            log("profile: not hooked: " + t);
        }
    }

    /**
     * Watermarks print the phone's name from the profile's table of {brand, model} pairs; a
     * shared profile such as CommonFlagship has the placeholder {XIAOMI, MI PHONE}. This
     * answers with the phone's own market name instead ("Xiaomi 14" becomes {XIAOMI, 14}).
     */
    static void hookMarketingName(Class<?> profile, String method) {
        if (method == null) return;
        try {
            Method names = profile.getDeclaredMethod(method);
            if (names.getReturnType() != android.util.SparseArray.class) {
                log("name: " + method + "() has an unexpected signature, skipping");
                return;
            }
            String market = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, "ro.product.marketname");
            if (market == null || market.trim().isEmpty()) market = android.os.Build.MODEL;
            String[] parts = market.trim().split("\\s+", 2);
            final String[] pair = parts.length == 2 && parts[0].equalsIgnoreCase("xiaomi")
                    ? new String[] {"XIAOMI", parts[1]}
                    : new String[] {"XIAOMI", market.trim()};
            XposedBridge.hookMethod(names, new MarketingName(pair));
            log("name: watermark model set to " + pair[0] + " " + pair[1]);
        } catch (Throwable t) {
            log("name: not hooked: " + t);
        }
    }

    static final class MarketingName extends XC_MethodHook {
        private final String[] pair;

        MarketingName(String[] pair) {
            this.pair = pair;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            android.util.SparseArray<String[]> names = new android.util.SparseArray<>();
            names.put(0, pair.clone());
            p.setResult(names);
        }
    }

    static final class ReplaceMissingProfile extends XC_MethodHook {
        private final Field missing;
        private final Field cache;
        private final Class<?> fallback;

        ReplaceMissingProfile(Field missing, Field cache, Class<?> fallback) {
            this.missing = missing;
            this.cache = cache;
            this.fallback = fallback;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (!Boolean.TRUE.equals(missing.get(null))) return;
            missing.set(null, Boolean.FALSE);
            if (fallback == null) return;
            Object profile = fallback.getDeclaredConstructor().newInstance();
            cache.set(null, profile);
            p.setResult(profile);
        }
    }

    /**
     * CN builds ask com.miui.securitycenter to show a privacy consent (CTA) dialog before
     * opening the camera. AOSP has no securitycenter, so the request fails and the camera
     * waits on its intro screen forever. The consent class's a() (ei.c in 6.7) picks the consent region (CN, KR or
     * GLOBAL); GLOBAL needs no securitycenter dialog. The rest of the app keeps its CN
     * feature set because that comes from a separate region check.
     */
    static void hookConsentRegion(ClassLoader cl, CameraBuild build) {
        try {
            Class<?> regions = cl.loadClass(build.consent + "$c");
            Object global = regions.getField("c").get(null);
            Method pick = cl.loadClass(build.consent).getDeclaredMethod("a");
            if (pick.getReturnType() != regions) {
                log("consent: " + build.consent + ".a() has an unexpected signature, skipping");
                return;
            }
            XposedBridge.hookMethod(pick, new ConstantResult(global));
            log("consent: region forced to GLOBAL");
        } catch (Throwable t) {
            log("consent: not hooked: " + t);
        }
    }

    /**
     * Multi-lens ("SAT") video is allowed per resolution, frame rate and HDR state by the
     * HAL tag com.xiaomi.videosat.supportedRange, decoded by j.U1(mode, quality, hdr). On
     * houji it is 0x8F8F: SDR and HDR bits for 720p, 1080p30, 1080p60, 4K24 and 4K30, none
     * for 4K60 or 8K. Two changes:
     *
     * - With Dolby Vision on, U1 asks the "True Colour" component (t2.c.n(2, quality)) whether
     *   the HAL's Dolby config allows SAT at that quality; houji's config doesn't list 4K30, so
     *   the lens locks. HDR10 (t2.a.o, t2.b.n) bails out the same way. While U1 runs, those
     *   checks are answered so U1 reaches the mask, which already has the HDR bit for 4K30.
     *   Tested: 4K30 Dolby Vision keeps all four lenses while recording, file stays DV 8.4.
     * - 4K60 ("8,60") is reported as supported. Tested on houji: the lens changes
     *   mid-recording and the file stays 3840x2160 at ~58 fps.
     */
    static void hookVideoLensSwitching(ClassLoader cl, CameraBuild build) {
        try {
            Class<?> satClass = cl.loadClass(build.satCheckClass);
            Method check = build.satCheckHasHdrArg
                    ? satClass.getDeclaredMethod("U1", int.class, String.class, boolean.class)
                    : satClass.getDeclaredMethod("U1", int.class, String.class);
            Method hdr10 = cl.loadClass(build.hdr10).getDeclaredMethod("o");
            Method hdr10Pro = cl.loadClass(build.hdr10Pro).getDeclaredMethod("n");
            Method dolbyAllows = cl.loadClass(build.dolby).getDeclaredMethod("n", int.class, String.class);
            if (check.getReturnType() != boolean.class || hdr10.getReturnType() != boolean.class
                    || hdr10Pro.getReturnType() != boolean.class || dolbyAllows.getReturnType() != boolean.class) {
                log("lens: unexpected signatures, skipping");
                return;
            }
            XposedBridge.hookMethod(check, new VideoSatCheck());
            XposedBridge.hookMethod(hdr10, new HdrOffInsideSatCheck());
            XposedBridge.hookMethod(hdr10Pro, new HdrOffInsideSatCheck());
            XposedBridge.hookMethod(dolbyAllows, new DolbyAllowsInsideSatCheck());
            log("lens: switching allowed with HDR per the HAL mask, and at 4K60");
        } catch (Throwable t) {
            log("lens: not hooked: " + t);
        }
    }

    /**
     * Dolby Vision is offered per (type 3, quality, fps) entry in the HAL tag
     * com.xiaomi.HDRVideoDisplayMode.availableconfigurations, checked by
     * n9.e.h1(type, quality, fps, caps). houji's rear camera lists 1080p30/60 and 4K24/30/60;
     * the front only 1080p30, though it advertises 10-bit HLG at every size. This adds front
     * 4K30 (quality 8, 30 fps); the rear already has it, so nothing changes there.
     */
    static void hookFrontDolby4k(ClassLoader cl, CameraBuild build) {
        try {
            Class<?> caps = cl.loadClass(build.capabilitiesClass);
            Method dolbyConfig = cl.loadClass(build.dolbyConfigClass)
                    .getDeclaredMethod(build.dolbyConfigMethod, int.class, int.class, int.class, caps);
            if (dolbyConfig.getReturnType() != boolean.class) {
                log("dolby 4k: unexpected signature, skipping");
                return;
            }
            XposedBridge.hookMethod(dolbyConfig, new Allow4k30Dolby());
            log("dolby 4k: front camera Dolby Vision allowed at 4K30");
        } catch (Throwable t) {
            log("dolby 4k: not hooked: " + t);
        }
    }

    static final class Allow4k30Dolby extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if (Integer.valueOf(3).equals(p.args[0]) && Integer.valueOf(8).equals(p.args[1])
                    && Integer.valueOf(30).equals(p.args[2]) && Boolean.FALSE.equals(p.getResult())) {
                p.setResult(Boolean.TRUE);
            }
        }
    }

    static final class ConstantResult extends XC_MethodHook {
        private final Object value;

        ConstantResult(Object value) {
            this.value = value;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            p.setResult(value);
        }
    }

    /** Depth of j.U1 calls on this thread, so the HDR hooks only act inside it. */
    static final ThreadLocal<int[]> IN_SAT_CHECK = ThreadLocal.withInitial(() -> new int[1]);

    static final class VideoSatCheck extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            IN_SAT_CHECK.get()[0]++;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            IN_SAT_CHECK.get()[0]--;
            if ("8,60".equals(p.args[1]) && Boolean.FALSE.equals(p.getResult())) {
                p.setResult(Boolean.TRUE);
            }
        }
    }

    static final class DolbyAllowsInsideSatCheck extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if (IN_SAT_CHECK.get()[0] > 0 && Integer.valueOf(2).equals(p.args[0])
                    && Boolean.FALSE.equals(p.getResult())) {
                p.setResult(Boolean.TRUE);
            }
        }
    }

    static final class HdrOffInsideSatCheck extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if (IN_SAT_CHECK.get()[0] > 0 && Boolean.TRUE.equals(p.getResult())) {
                p.setResult(Boolean.FALSE);
            }
        }
    }
}
