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
 *
 * At 50M the main camera only takes its full sensor: any digital zoom on it makes the HAL
 * reject the crop ("Off-centered zoom is not supported in DualIFE mode"), retry 11 times and
 * abort. Sliding the zoom down from the telephoto opened the main camera at 3.2x and crashed.
 * At 50M the lens now only changes when the slide passes the next lens's stop: from the
 * telephoto down to 1x, or from the main camera up to the telephoto. In between, the lens it
 * is on stays at its own field of view (the main camera at 1x, the telephoto at its native
 * framing), so the slider itself moves as usual. When the slide ends, and when a lens opens,
 * the zoom snaps to the stop of the lens in use (0.6x, 1x, or the telephoto's 3.2x and up), as
 * in Pro mode; the camera's own snap picked the nearest stop and left the ultra-wide labelled 1x.
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
    static final int MAIN_CAMERA = 2;
    static final int TELE_CAMERA = 4;
    static final int UW_CAMERA = 3;
    static final float UW_STOP = 0.6f;
    /** The telephoto's stop on the zoom bar (LensSwitchZoomBounds). */
    static final float TELE_STOP = 3.2f;
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
            hookZoom(cl, build, ultraOn);
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

    static void hookZoom(ClassLoader cl, CameraBuild build, Method ultraOn) throws Exception {
        if (build.zoomManagerClass == null) return;
        for (Method m : cl.loadClass(build.zoomManagerClass).getDeclaredMethods()) {
            Class<?>[] args = m.getParameterTypes();
            if (m.getName().equals(build.lensSwitchMethod) && args.length == 3 && args[0] == float.class
                    && args[1] == float.class && m.getReturnType() == boolean.class) {
                XposedBridge.hookMethod(m, new HoldLens(ultraOn));
            }
        }
        if (build.snapClass != null) {
            Class<?> manager = cl.loadClass(build.zoomManagerClass);
            Field module = manager.getDeclaredField(build.zoomModuleField);
            Field value = manager.getDeclaredField(build.zoomValueField);
            module.setAccessible(true);
            value.setAccessible(true);
            Method setZoom = manager.getDeclaredMethod(build.zoomSetMethod, float.class);
            SnapToLens snap = new SnapToLens(ultraOn, module, value, setZoom);
            XposedBridge.hookMethod(cl.loadClass(build.snapClass).getDeclaredMethod(build.snapMethod), snap);
            XposedBridge.hookMethod(cl.loadClass(build.slideEndClass).getDeclaredMethod(build.slideEndMethod, int.class), snap);
        }
        Method cameraId = null;
        for (Method m : cl.loadClass(build.cameraIdClass).getDeclaredMethods()) {
            if (m.getName().equals(build.cameraIdMethod) && m.getReturnType() == int.class
                    && m.getParameterTypes().length == 1 && m.getParameterTypes()[0].getName().equals(build.capsClass)) {
                cameraId = m;
            }
        }
        if (cameraId == null) throw new NoSuchMethodException(build.cameraIdClass + "." + build.cameraIdMethod);
        int hooked = 0;
        for (Method m : cl.loadClass(build.zoomApplyClass).getDeclaredMethods()) {
            Class<?>[] args = m.getParameterTypes();
            if (m.getName().equals(build.zoomApplyMethod) && args.length == 3
                    && args[1].getName().equals(build.capsClass)) {
                Field zoom = args[2].getDeclaredField(build.zoomField);
                zoom.setAccessible(true);
                XposedBridge.hookMethod(m, new MainAtOneX(ultraOn, cameraId, zoom));
                hooked++;
            }
        }
        if (hooked == 0) throw new NoSuchMethodException(build.zoomApplyClass + "." + build.zoomApplyMethod);
    }

    static boolean ultraHdNow(Method ultraOn) throws Exception {
        return ultraHd(ultraOn, PHOTO_MODE) || ultraHd(ultraOn, PRO_MODE);
    }

    /**
     * The cameras 50M opens on its own: main, ultra-wide, telephoto. The checks above can't tell
     * the current mode (Pro's 50M setting reads on from Photo too), so the zoom hooks act only
     * on these; 12.5M Photo runs on the multi-lens camera (5) and is left alone.
     */
    static boolean lensCamera(Object camera) {
        return Integer.valueOf(MAIN_CAMERA).equals(camera) || AUX_CAMERAS.contains(camera);
    }

    /**
     * At 50M, switches lens only when the zoom passes the next lens's stop. The camera's own
     * checks switch on crossing 1x (to the ultra-wide) or the telephoto stop (telephoto to main),
     * so a slide held on a lens is handed the crossed stop as its old zoom once it gets there.
     */
    static final class HoldLens extends XC_MethodHook {
        private final Method ultraOn;

        HoldLens(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
            float to = (Float) p.args[1];
            if (to >= TELE_STOP) return;
            Object camera = actualCamera(p.args[2]);
            if (!lensCamera(camera) || !ultraHdNow(ultraOn)) return;
            if (to <= UW_STOP + 0.01f) {
                if (!Integer.valueOf(UW_CAMERA).equals(camera)) p.args[0] = TELE_STOP;
            } else if (Integer.valueOf(TELE_CAMERA).equals(camera) && to <= 1.0f) {
                p.args[0] = TELE_STOP;
            } else if (!(Integer.valueOf(UW_CAMERA).equals(camera) && to >= 1.0f)) {
                p.setResult(Boolean.FALSE);
            }
        }
    }

    static Object actualCamera(Object module) throws Exception {
        return module == null ? null : module.getClass().getMethod("getActualCameraId").invoke(module);
    }

    /** At 50M, moves the zoom to the stop of the lens in use; on the telephoto, only up to its stop. */
    static final class SnapToLens extends XC_MethodHook {
        private final Method ultraOn;
        private final Field module;
        private final Field zoom;
        private final Method setZoom;

        SnapToLens(Method ultraOn, Field module, Field zoom, Method setZoom) {
            this.ultraOn = ultraOn;
            this.module = module;
            this.zoom = zoom;
            this.setZoom = setZoom;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (!ultraHd(ultraOn, PHOTO_MODE)) return;
            Object ref = module.get(p.thisObject);
            Object camera = actualCamera(ref == null ? null : ((java.lang.ref.Reference<?>) ref).get());
            if (!lensCamera(camera)) return;
            float now = zoom.getFloat(p.thisObject);
            float stop = Integer.valueOf(UW_CAMERA).equals(camera) ? UW_STOP
                    : Integer.valueOf(TELE_CAMERA).equals(camera) ? Math.max(now, TELE_STOP) : 1.0f;
            if (Math.abs(now - stop) > 0.001f) setZoom.invoke(p.thisObject, stop);
        }
    }

    /**
     * At 50M, sends the main camera's and ultra-wide's requests at 1x whatever the zoom bar says
     * (each held at its own field of view between stops), and the telephoto's at no less than 1x.
     */
    static final class MainAtOneX extends XC_MethodHook {
        private final Method ultraOn;
        private final Method cameraId;
        private final Field zoom;
        private final ThreadLocal<Float> saved = new ThreadLocal<>();

        MainAtOneX(Method ultraOn, Method cameraId, Field zoom) {
            this.ultraOn = ultraOn;
            this.cameraId = cameraId;
            this.zoom = zoom;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
            Object config = p.args[2];
            if (config == null || p.args[1] == null) return;
            float value = zoom.getFloat(config);
            if (value == 1.0f) return;
            Object camera = cameraId.invoke(null, p.args[1]);
            if (!lensCamera(camera) || (value > 1.0f && Integer.valueOf(TELE_CAMERA).equals(camera))
                    || !ultraHdNow(ultraOn)) return;
            saved.set(zoom.getFloat(config));
            zoom.setFloat(config, 1.0f);
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Float value = saved.get();
            if (value == null) return;
            saved.remove();
            zoom.setFloat(p.args[2], value);
        }
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

    /** Keeps every lens when the bar filters its ratios at 50M, without the 2x stop. */
    static final class KeepLenses extends XC_MethodHook {
        private final Method ultraOn;

        KeepLenses(Method ultraOn) {
            this.ultraOn = ultraOn;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            float[] in = (float[]) p.args[0];
            float[] out = (float[]) p.getResult();
            if (in == null || out == null) return;
            if (out.length < in.length && ultraHdNow(ultraOn)) {
                p.setResult(lensStops(in));
            } else if (ultraHd(ultraOn, PHOTO_MODE)) {
                p.setResult(lensStops(out));
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

    /** The lens stops of a zoom list: drops the main camera's digital zoom stops (2x), which 50M can't take. */
    static float[] lensStops(float[] zooms) {
        int n = 0;
        for (float z : zooms) if (z <= 1.0f || z >= TELE_STOP) n++;
        if (n == zooms.length) return zooms;
        float[] out = new float[n];
        n = 0;
        for (float z : zooms) if (z <= 1.0f || z >= TELE_STOP) out[n++] = z;
        return out;
    }

    /** At 50M, the lens list the mode has at 12.5M (the 50M one drops lenses), without the 2x stop. */
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
                if (full instanceof float[] && ((float[]) full).length > 0) p.setResult(lensStops((float[]) full));
            } finally {
                READING_RATIO.remove();
            }
        }
    }
}
