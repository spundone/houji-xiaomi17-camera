package dev.spundone.xcam17;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * The camera's saliency checker (subject detection used when the focus area changes) runs
 * libmialgo_saliency.so from the APK, which links the phone's own
 * /odm/lib64/libmialgo_ai_vision.so. That library is houji's HyperOS build, older than the one
 * the Xiaomi 17 camera was built against, and the camera process segfaults in its init thread
 * (p_aivs_init). With init and process reporting "unavailable", the native library is never
 * started and the camera uses its normal focus-area handling.
 */
final class SaliencyOff {
    private SaliencyOff() {}

    static void hook(ClassLoader cl) {
        try {
            Class<?> checker = cl.loadClass("com.android.camera.saliencychecker.SaliencyChecker");
            int hooked = 0;
            for (Method m : checker.getDeclaredMethods()) {
                String n = m.getName();
                if ((n.equals("init") || n.equals("process") || n.equals("hasInit"))
                        && m.getReturnType() == boolean.class) {
                    XposedBridge.hookMethod(m, new Unavailable());
                    hooked++;
                }
            }
            Entry.log("saliency: " + (hooked == 3 ? "native checker disabled" : "only " + hooked + "/3 methods found"));
        } catch (Throwable t) {
            Entry.log("saliency: not hooked: " + t);
        }
    }

    static final class Unavailable extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            p.setResult(Boolean.FALSE);
        }
    }
}
