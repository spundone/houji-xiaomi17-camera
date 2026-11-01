package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 50MP (Ultra HD) in Photo mode only used the main camera: the lens buttons were hidden and
 * the zoom reset to 1x. houji's ultra-wide and telephoto both deliver their full 8160x6144
 * sensor, and the camera's own Ultra HD lens picker already opens them by zoom ratio (below 1x
 * the ultra-wide, at the telephoto ratio the telephoto); only the buttons were missing. This
 * shows them at 50M and puts back the telephoto stop, which the 6.8 camera leaves out at 50M
 * because the 18 Pro shoots its telephoto Ultra HD through a separate 200MP option.
 *
 * Ultra HD also forced the 4:3 ratio: the ratio component answers "4x3" while it is on, and
 * picking another ratio turns Ultra HD off. The sensor stream stays 8192x6144 either way and
 * the camera crops 1:1 from it, but has no crop for 16:9 or wider, so 1:1 is let through at
 * 50M and the wider ratios keep turning Ultra HD off.
 */
final class UltraHdLenses {
    static final int PHOTO_MODE = 163;
    /** Zoom toggle states: hidden, and the state Photo mode shows at 12.5M. */
    static final int HIDDEN = -1;
    static final int SHOWN = 1;
    /** houji's telephoto, as the camera's own 12.5M lens list has it. */
    static final float TELE = 3.2f;
    /** Ratios the camera can produce from the 50M sensor stream. */
    static final String RATIO_4_3 = "4x3";
    static final String RATIO_1_1 = "1x1";
    static final String PHOTO_MODULE = "com.android.camera.module.Camera2Module";
    /**
     * houji's ultra-wide and telephoto opened on their own, as the Ultra HD lens picker does.
     * Photo mode otherwise uses the logical multi-camera, so these ids mean 50M is on, even
     * though the Ultra HD setting reads as off for a lens that has no entry of its own.
     */
    static final java.util.Set<Integer> AUX_CAMERAS = new java.util.HashSet<>(java.util.Arrays.asList(3, 4));

    private UltraHdLenses() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.zoomStateClass == null) return;
        try {
            Method ultraOn = cl.loadClass(build.ultraOnClass).getDeclaredMethod(build.ultraOnMethod);
            int hooked = 0;
            for (Method m : cl.loadClass(build.zoomStateClass).getDeclaredMethods()) {
                if (m.getName().equals(build.zoomStateMethod) && m.getParameterTypes().length == 3
                        && m.getParameterTypes()[0] == int.class) {
                    Field state = m.getReturnType().getDeclaredField(build.zoomStateField);
                    state.setAccessible(true);
                    XposedBridge.hookMethod(m, new ShowButtons(ultraOn, state));
                    hooked++;
                }
            }
            if (hooked == 0) throw new NoSuchMethodException(build.zoomStateClass + "." + build.zoomStateMethod);
            XposedBridge.hookMethod(cl.loadClass(build.zoomListClass).getDeclaredMethod(build.zoomListMethod, int.class),
                    new AddTele(ultraOn));
            XposedBridge.hookMethod(cl.loadClass(PHOTO_MODULE).getDeclaredMethod("isCameraSwitchingDuringZoomingAllowed"),
                    new AllowLensSwitch(ultraOn));
            hookRatio(cl, build, ultraOn);
            Entry.log("ultra hd: 50MP on the ultra-wide and telephoto as well, and in 1:1");
        } catch (Throwable t) {
            Entry.log("ultra hd: not hooked: " + t);
        }
    }

    /** While set, "Ultra HD is on" answers false; set only inside the Photo ratio getter. */
    private static final ThreadLocal<Boolean> READING_RATIO = new ThreadLocal<>();
    /** Set while the ratio-change handler runs with a ratio 50M can produce. */
    private static final ThreadLocal<Boolean> KEEP_ULTRA = new ThreadLocal<>();

    static void hookRatio(ClassLoader cl, CameraBuild build, Method ultraOn) throws Exception {
        XposedBridge.hookMethod(ultraOn, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (Boolean.TRUE.equals(READING_RATIO.get())) p.setResult(Boolean.FALSE);
            }
        });
        XposedBridge.hookMethod(cl.loadClass(build.ratioClass).getDeclaredMethod("getComponentValue", int.class),
                new SquareRatio(ultraOn));
        int hooked = 0;
        for (Method m : cl.loadClass(build.ratioChangeClass).getDeclaredMethods()) {
            Class<?>[] args = m.getParameterTypes();
            if (m.getName().equals(build.ratioChangeMethod) && args.length == 2 && args[0] == String.class) {
                XposedBridge.hookMethod(m, new RatioChange());
                hooked++;
            }
        }
        if (hooked == 0) throw new NoSuchMethodException(build.ratioChangeClass + "." + build.ratioChangeMethod);
        XposedBridge.hookMethod(ultraOn.getDeclaringClass().getDeclaredMethod(build.ultraOffMethod), new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (Boolean.TRUE.equals(KEEP_ULTRA.get())) p.setResult(null);
            }
        });
    }

    /** At 50M in Photo mode, answers the saved ratio when it is 1:1 instead of the forced 4:3. */
    static final class SquareRatio extends XC_MethodHook {
        private final Method ultraOn;

        SquareRatio(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (!RATIO_4_3.equals(p.getResult()) || !ultraHd(ultraOn, p.args[0])) return;
            READING_RATIO.set(Boolean.TRUE);
            try {
                Object saved = ((Method) p.method).invoke(p.thisObject, p.args[0]);
                if (RATIO_1_1.equals(saved)) p.setResult(saved);
            } finally {
                READING_RATIO.remove();
            }
        }
    }

    /** Keeps Ultra HD on when the ratio is changed to one 50M can produce. */
    static final class RatioChange extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            if (RATIO_1_1.equals(p.args[0]) || RATIO_4_3.equals(p.args[0])) KEEP_ULTRA.set(Boolean.TRUE);
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            KEEP_ULTRA.remove();
        }
    }

    static boolean ultraHd(Method ultraOn, Object mode) throws Exception {
        return Integer.valueOf(PHOTO_MODE).equals(mode) && Boolean.TRUE.equals(ultraOn.invoke(null));
    }

    static final class ShowButtons extends XC_MethodHook {
        private final Method ultraOn;
        private final Field state;

        ShowButtons(Method ultraOn, Field state) {
            this.ultraOn = ultraOn;
            this.state = state;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Object toggle = p.getResult();
            if (toggle != null && ultraHd(ultraOn, p.args[0]) && state.getInt(toggle) == HIDDEN) {
                state.setInt(toggle, SHOWN);
            }
        }
    }

    static final class AllowLensSwitch extends XC_MethodHook {
        private final Method ultraOn;

        AllowLensSwitch(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (!Boolean.FALSE.equals(p.getResult())) return;
            Object module = p.thisObject;
            if (!Integer.valueOf(PHOTO_MODE).equals(module.getClass().getMethod("getModuleIndex").invoke(module))) return;
            Object camera = module.getClass().getMethod("getActualCameraId").invoke(module);
            if (Boolean.TRUE.equals(ultraOn.invoke(null)) || AUX_CAMERAS.contains(camera)) p.setResult(Boolean.TRUE);
        }
    }

    static final class AddTele extends XC_MethodHook {
        private final Method ultraOn;

        AddTele(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (!ultraHd(ultraOn, p.args[0])) return;
            float[] ratios = (float[]) p.getResult();
            if (ratios == null || ratios.length == 0 || ratios[ratios.length - 1] >= TELE) return;
            float[] out = java.util.Arrays.copyOf(ratios, ratios.length + 1);
            out[ratios.length] = TELE;
            p.setResult(out);
        }
    }
}
