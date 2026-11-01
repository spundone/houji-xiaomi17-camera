package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 50MP (Ultra HD) in Photo and Pro mode only used the main camera: the lens buttons were hidden and
 * the zoom reset to 1x. houji's ultra-wide and telephoto both deliver their full 8160x6144
 * sensor, and the camera's own Ultra HD lens picker already opens them by zoom ratio (below 1x
 * the ultra-wide, at the telephoto ratio the telephoto); only the buttons were missing. This
 * shows them at 50M with the lens list the mode has at 12.5M; the 6.8 camera drops the
 * telephoto stop at 50M because the 18 Pro shoots its telephoto Ultra HD through a separate
 * 200MP option. Pro mode
 * reloads the Ultra HD options from the new camera on every lens switch; that keeps 50M on the
 * ultra-wide and telephoto once the profile lists them as 50MP lenses (DeviceFeatures w0/y0/u0).
 *
 * Ultra HD also forced the 4:3 ratio: the ratio component answers "4x3" while it is on, and
 * picking another ratio turns Ultra HD off. The sensor stream stays 8192x6144 either way and
 * the camera crops 1:1 from it, but has no crop for 16:9 or wider, so 1:1 is let through at
 * 50M and the wider ratios keep turning Ultra HD off.
 */
final class UltraHdLenses {
    static final int PHOTO_MODE = 163;
    static final int PRO_MODE = 167;
    /** Zoom toggle state for hidden buttons. */
    static final int HIDDEN = -1;
    /** Ratios the camera can produce from the 50M sensor stream. */
    static final String RATIO_4_3 = "4x3";
    static final String RATIO_1_1 = "1x1";
    static final String PHOTO_MODULE = "com.android.camera.module.Camera2Module";
    /** The lens button bar; its (ratios, ...) filter drops all but 1x at Ultra HD in Pro. */
    static final String ZOOM_TOGGLE = "com.android.camera.ui.zoom.ZoomRatioToggleView";
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
            ultraOnInMode = cl.loadClass(build.ultraOnClass).getDeclaredMethod(build.ultraOnModeMethod, int.class);
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
                    new FullLensList(ultraOn));
            XposedBridge.hookMethod(cl.loadClass(PHOTO_MODULE).getDeclaredMethod("isCameraSwitchingDuringZoomingAllowed"),
                    new AllowLensSwitch(ultraOn));
            int filters = 0;
            for (Method m : cl.loadClass(ZOOM_TOGGLE).getDeclaredMethods()) {
                Class<?>[] args = m.getParameterTypes();
                if (m.getReturnType() == float[].class && args.length == 4 && args[0] == float[].class) {
                    XposedBridge.hookMethod(m, new KeepLenses(ultraOn));
                    filters++;
                }
            }
            if (filters != 1) Entry.log("ultra hd: " + filters + " lens filters found in " + ZOOM_TOGGLE);
            hookRatio(cl, build, ultraOn);
            Entry.log("ultra hd: 50MP on the ultra-wide and telephoto as well (Photo and Pro), and in 1:1");
        } catch (Throwable t) {
            Entry.log("ultra hd: not hooked: " + t);
        }
    }

    /** While set, "Ultra HD is on" answers false: inside the ratio getter, lens list and button state. */
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
            if (Boolean.TRUE.equals(READING_RATIO.get()) || !RATIO_4_3.equals(p.getResult())
                    || !ultraHd(ultraOn, p.args[0])) return;
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

    static boolean lensMode(Object mode) {
        return Integer.valueOf(PHOTO_MODE).equals(mode) || Integer.valueOf(PRO_MODE).equals(mode);
    }

    /** Ultra HD supported and on in a given mode; the plain check reads only the current one. */
    private static Method ultraOnInMode;

    static boolean ultraHd(Method ultraOn, Object mode) throws Exception {
        if (!lensMode(mode) || Boolean.TRUE.equals(READING_RATIO.get())) return false;
        if (Boolean.TRUE.equals(ultraOn.invoke(null))) return true;
        return Integer.valueOf(PRO_MODE).equals(mode) && Boolean.TRUE.equals(ultraOnInMode.invoke(null, mode));
    }

    /** Keeps every lens when the bar filters its ratios at 50M. */
    static final class KeepLenses extends XC_MethodHook {
        private final Method ultraOn;

        KeepLenses(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            float[] in = (float[]) p.args[0];
            float[] out = (float[]) p.getResult();
            if (in != null && out != null && out.length < in.length
                    && (ultraHd(ultraOn, PHOTO_MODE) || ultraHd(ultraOn, PRO_MODE))) {
                p.setResult(in);
            }
        }
    }


    /** At 50M, the lens button state the mode has at 12.5M (50M hides them or shows only 1x). */
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
            if (toggle == null || !ultraHd(ultraOn, p.args[0])) return;
            READING_RATIO.set(Boolean.TRUE);
            try {
                Object normal = ((Method) p.method).invoke(p.thisObject, p.args);
                if (normal != null && state.getInt(normal) != HIDDEN) p.setResult(normal);
            } finally {
                READING_RATIO.remove();
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
            Object mode = module.getClass().getMethod("getModuleIndex").invoke(module);
            if (!lensMode(mode)) return;
            Object camera = module.getClass().getMethod("getActualCameraId").invoke(module);
            if (ultraHd(ultraOn, mode) || AUX_CAMERAS.contains(camera)) p.setResult(Boolean.TRUE);
        }
    }

    /** At 50M, the lens list the mode has at 12.5M (the 50M one drops lenses). */
    static final class FullLensList extends XC_MethodHook {
        private final Method ultraOn;

        FullLensList(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (Boolean.TRUE.equals(READING_RATIO.get()) || !ultraHd(ultraOn, p.args[0])) return;
            READING_RATIO.set(Boolean.TRUE);
            try {
                Object full = ((Method) p.method).invoke(p.thisObject, p.args[0]);
                if (full instanceof float[] && ((float[]) full).length > 0) p.setResult(full);
            } finally {
                READING_RATIO.remove();
            }
        }
    }
}
