package dev.spundone.xcam17;

import android.hardware.camera2.CaptureRequest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Runtime patches that let the Xiaomi 17 camera (MiuiCamera 6.7.000280.0, CN build)
 * run on an AOSP ROM for the Xiaomi 14 (houji).
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
        hookClientName(lp.classLoader);
        hookConsentRegion(lp.classLoader);
        hook4k60LensSwitching(lp.classLoader);
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
     * CN builds ask com.miui.securitycenter to show a privacy consent (CTA) dialog before
     * opening the camera. AOSP has no securitycenter, so the request fails and the camera
     * waits on its intro screen forever. ei.c.a() picks the consent region (CN, KR or
     * GLOBAL); GLOBAL needs no securitycenter dialog. The rest of the app keeps its CN
     * feature set because that comes from a separate region check.
     */
    static void hookConsentRegion(ClassLoader cl) {
        try {
            Class<?> regions = cl.loadClass("ei.c$c");
            Object global = regions.getField("c").get(null);
            if (!(global instanceof Enum) || !"GLOBAL".equals(((Enum<?>) global).name())) {
                log("consent: ei.c$c.c is not GLOBAL, skipping");
                return;
            }
            Method pick = cl.loadClass("ei.c").getDeclaredMethod("a");
            if (pick.getReturnType() != regions) {
                log("consent: ei.c.a() has an unexpected signature, skipping");
                return;
            }
            XposedBridge.hookMethod(pick, new ConstantResult(global));
            log("consent: region forced to GLOBAL");
        } catch (Throwable t) {
            log("consent: not hooked: " + t);
        }
    }

    /**
     * Multi-lens ("SAT") video is allowed per resolution and frame rate by the HAL tag
     * com.xiaomi.videosat.supportedRange. On houji it is 0x8F8F: 720p, 1080p30, 1080p60,
     * 4K24 and 4K30, but not 4K60, so the camera locks 4K60 recordings to one lens.
     * j.U1(mode, quality, hdr) decodes that mask; this reports 4K60 ("8,60") as supported.
     * Tested on houji: lens changes mid-recording, file stays 3840x2160 at ~58 fps.
     */
    static void hook4k60LensSwitching(ClassLoader cl) {
        try {
            Method check = cl.loadClass("com.android.camera.data.data.j")
                    .getDeclaredMethod("U1", int.class, String.class, boolean.class);
            if (check.getReturnType() != boolean.class) {
                log("4k60: j.U1 has an unexpected return type, skipping");
                return;
            }
            XposedBridge.hookMethod(check, new Allow4k60());
            log("4k60: multi-lens video allowed at 4K60");
        } catch (Throwable t) {
            log("4k60: not hooked: " + t);
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

    static final class Allow4k60 extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if ("8,60".equals(p.args[1]) && Boolean.FALSE.equals(p.getResult())) {
                p.setResult(Boolean.TRUE);
            }
        }
    }
}
