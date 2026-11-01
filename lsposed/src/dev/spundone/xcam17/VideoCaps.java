package dev.spundone.xcam17;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Video filters at 4K. With a filter on, the camera's quality check limits video to the HAL's
 * MasterFilter.quality list; houji's HAL has none, so it fell back to at most 1080p 30 fps,
 * or 4K 60 fps when the HAL declares all-scenes Dolby support. The filters are colour tables
 * the HAL applies to any stream size, so that check alone is answered yes, inside the quality
 * check only: elsewhere the flag also changes Dolby, tracking focus and audio settings. The
 * default stays 1080p 30 fps. (A MasterFilter.quality list of its own replaced that default,
 * and the camera picked 720p.)
 *
 * Tried and left out: Video HDR at 4K 60 and on the front camera, and Movie mode at 4K. The
 * HAL lists neither and the sessions crashed.
 */
final class VideoCaps {
    private static final ThreadLocal<Boolean> IN_QUALITY_CHECK = new ThreadLocal<>();

    private VideoCaps() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.qualityCheckClass == null) return;
        try {
            Class<?> caps = cl.loadClass(build.capsClass);
            int hooked = 0;
            for (Method m : cl.loadClass(build.qualityCheckClass).getDeclaredMethods()) {
                if (m.getName().equals(build.qualityCheckMethod) && m.getParameterTypes().length == 5) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            IN_QUALITY_CHECK.set(Boolean.TRUE);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            IN_QUALITY_CHECK.remove();
                        }
                    });
                    hooked++;
                }
            }
            if (hooked == 0) throw new NoSuchMethodException(build.qualityCheckClass + "." + build.qualityCheckMethod);
            XposedBridge.hookMethod(cl.loadClass(build.capsChecksClass).getDeclaredMethod(build.allScenesDolbyMethod, caps),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (Boolean.TRUE.equals(IN_QUALITY_CHECK.get())) p.setResult(Boolean.TRUE);
                        }
                    });
            Entry.log("video: filters up to 4K");
        } catch (Throwable t) {
            Entry.log("video: filter quality not hooked: " + t);
        }
    }
}
