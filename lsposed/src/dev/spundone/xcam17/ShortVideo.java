package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Short video (MiLive, mode 183; its engine is a split the camera downloads on first use)
 * opened a session in operating mode 0x8004 with only a preview and a 1280x720 JPEG stream.
 * houji's HAL runs 0x8004 as a video use case that needs a recording stream, so it refused
 * ("Configuration failed for stream ... buffer counts (0,0,1)", -38) and the camera showed
 * "Can't connect to camera". Short video records from the preview through its own encoder,
 * so it gets the standard operating mode (0) instead.
 *
 * Short film (Vlog Pro, mode 219, offered with houji's Vlog flags) asked for 0x800c and was
 * refused the same way; it records from the preview too.
 *
 * Still broken in Short video: zoom (the slider moves but the main camera doesn't; applying the zoom
 * directly didn't help) and recording several clips in a row (the second shutter press stops
 * and opens the review instead of pausing).
 */
final class ShortVideo {
    static final String[] MODULES = {"com.xiaomi.microfilm.milive.mode.MiLiveModule",
            "com.xiaomi.microfilm.vlogpro.mode.VlogProModule"};
    static final String BASE_MODULE = "com.android.camera.module.t";
    static final String OPERATING_MODE_FIELD = "mOperatingMode";
    static final int STANDARD = 0;

    private ShortVideo() {}

    static void hook(ClassLoader cl) {
        try {
            java.util.List<Class<?>> modules = new java.util.ArrayList<>();
            for (String name : MODULES) {
                try {
                    modules.add(cl.loadClass(name));
                } catch (ClassNotFoundException ignored) {
                    // Not in this camera build.
                }
            }
            Class<?> base = cl.loadClass(BASE_MODULE);
            Field field = base.getDeclaredField(OPERATING_MODE_FIELD);
            field.setAccessible(true);
            Method get = base.getDeclaredMethod("getOperatingMode");
            XposedBridge.hookMethod(get, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    boolean recorder = false;
                    for (Class<?> m : modules) recorder |= m.isInstance(p.thisObject);
                    if (!recorder) return;
                    field.setInt(p.thisObject, STANDARD);
                    p.setResult(STANDARD);
                }
            });
            Entry.log("short video: standard session mode for Short video and Short film");
        } catch (Throwable t) {
            Entry.log("short video: not hooked: " + t);
        }
    }
}
