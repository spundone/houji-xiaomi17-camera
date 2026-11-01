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
 * check is answered yes for this tag.
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
        }
    }
}
