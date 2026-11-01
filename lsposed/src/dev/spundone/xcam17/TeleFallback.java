package dev.spundone.xcam17;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Adaptive telephoto off by default. On the multi-lens camera the HAL picks the lens itself and,
 * with telephoto fallback allowed, shows a crop of the main camera instead of the telephoto
 * when it judges the scene too dark or too close ("SATFallbackDetected newMasterCameraId: 2").
 * Indoors that meant 3.2x in Photo and 2.6x in Portrait never reached the telephoto. The camera
 * sends the HAL's fallback-disable tag when Settings > Adaptive telephoto is off; its default is
 * on, so the default is answered off. Turning the setting on in Settings still works.
 *
 * The camera only sends that tag (com.xiaomi.teleFallback.isDisable) when the capabilities
 * list it, and houji's multi-lens camera doesn't, though the HAL defines the tag; so its
 * check is answered yes for this tag. 6.8 also asks whether fallback control is supported at
 * all (com.xiaomi.teleFallback.isSupported), which houji doesn't list either, so without that
 * answer the setting never reached the HAL; it is answered yes for rear cameras.
 */
final class TeleFallback {
    /** The master switch; the capture and video switches default to its value. */
    static final String KEY = "pref_camera_tele_fallback_key";
    static final String DISABLE_TAG = "com.xiaomi.teleFallback.isDisable";

    private TeleFallback() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.settingsClass == null) return;
        try {
            XposedBridge.hookMethod(cl.loadClass(build.settingsClass)
                    .getDeclaredMethod(build.settingsBooleanMethod, String.class, boolean.class), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (KEY.equals(p.args[0])) p.args[1] = Boolean.FALSE;
                }
            });
            XposedBridge.hookMethod(cl.loadClass(build.capsClass).getDeclaredMethod(build.capsTagCheckMethod, String.class),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (DISABLE_TAG.equals(p.args[0])) p.setResult(Boolean.TRUE);
                        }
                    });
            Entry.log("tele fallback: Adaptive telephoto off by default");
        } catch (Throwable t) {
            Entry.log("tele fallback: not hooked: " + t);
            return;
        }
        if (build.teleSupportedMethod == null) return;
        try {
            Class<?> caps = cl.loadClass(build.capsClass);
            java.lang.reflect.Method supported = cl.loadClass(build.capsChecksClass)
                    .getDeclaredMethod(build.teleSupportedMethod, caps);
            java.lang.reflect.Field characteristics = caps.getDeclaredField(build.capsCharacteristicsField);
            if (supported.getReturnType() != boolean.class
                    || characteristics.getType() != android.hardware.camera2.CameraCharacteristics.class) {
                Entry.log("tele fallback: supported check has an unexpected shape, skipping");
                return;
            }
            characteristics.setAccessible(true);
            XposedBridge.hookMethod(supported, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (Boolean.TRUE.equals(p.getResult()) || p.args[0] == null) return;
                    Object c = characteristics.get(p.args[0]);
                    Integer facing = c == null ? null : ((android.hardware.camera2.CameraCharacteristics) c)
                            .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                        p.setResult(Boolean.TRUE);
                    }
                }
            });
            Entry.log("tele fallback: fallback control reported supported on rear cameras");
        } catch (Throwable t) {
            Entry.log("tele fallback: supported check not hooked: " + t);
        }
    }
}
