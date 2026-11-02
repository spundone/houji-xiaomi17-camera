package dev.spundone.xcam17.aosp;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Port of UltraHdLenses: 50MP (Ultra HD) on the ultra-wide and telephoto as well as the main
 * camera, in Photo and Pro, and in 1:1; at 50M the lens only changes at each lens's stop and the
 * zoom snaps to the stop of the lens in use. See the LSPosed hook for the full story.
 *
 * Hooks that re-ran the original with "Ultra HD is on" answering false call the renamed
 * original (<name>$xcam) through reflection, with {@link #READING_RATIO} set.
 *
 * ultra names the "Ultra HD is on" class and its checks as "class#plain#perMode", e.g.
 * "com.android.camera.data.data.p#l0#m0".
 */
public final class UltraHdLenses {
    static final int PHOTO_MODE = 163;
    static final int PRO_MODE = 167;
    static final int HIDDEN = -1;
    static final String RATIO_4_3 = "4x3";
    static final String RATIO_1_1 = "1x1";
    static final int MAIN_CAMERA = 2;
    static final int UW_CAMERA = 3;
    static final int TELE_CAMERA = 4;
    static final float UW_STOP = 0.6f;
    static final float TELE_STOP = 3.2f;

    /** While set, "Ultra HD is on" answers false: inside the ratio getter, lens list and button state. */
    private static final ThreadLocal<Boolean> READING_RATIO = new ThreadLocal<>();
    /** Set while the ratio-change handler runs with a ratio 50M can produce. */
    private static final ThreadLocal<Boolean> KEEP_ULTRA = new ThreadLocal<>();
    /** Zoom value saved by {@link #zoomApplyBefore} for {@link #zoomApplyAfter}. */
    private static final ThreadLocal<Float> SAVED_ZOOM = new ThreadLocal<>();

    private static final Map<String, Method> METHODS = new HashMap<>();

    private UltraHdLenses() {}

    // --- "Ultra HD is on" ---

    /** After the plain "Ultra HD is on" check: off while a hook re-reads the 12.5M answer. */
    public static boolean ultraOn(boolean on) {
        return on && !Boolean.TRUE.equals(READING_RATIO.get());
    }

    /** Guard on "turn Ultra HD off": skipped while the ratio changes to one 50M can produce. */
    public static boolean allowUltraOff() {
        return !Boolean.TRUE.equals(KEEP_ULTRA.get());
    }

    static boolean lensMode(int mode) {
        return mode == PHOTO_MODE || mode == PRO_MODE;
    }

    /** Ultra HD supported and on in a mode; the plain check reads only the current one. */
    static boolean ultraHd(String ultra, int mode) throws Exception {
        if (!lensMode(mode) || Boolean.TRUE.equals(READING_RATIO.get())) return false;
        String[] n = ultra.split("#");
        if (Boolean.TRUE.equals(method(n[0], n[1]).invoke(null))) return true;
        return mode == PRO_MODE && Boolean.TRUE.equals(method(n[0], n[2]).invoke(null, mode));
    }

    static boolean ultraHdNow(String ultra) throws Exception {
        return ultraHd(ultra, PHOTO_MODE) || ultraHd(ultra, PRO_MODE);
    }

    static boolean lensCamera(Object camera) {
        return Integer.valueOf(MAIN_CAMERA).equals(camera) || Integer.valueOf(UW_CAMERA).equals(camera)
                || Integer.valueOf(TELE_CAMERA).equals(camera);
    }

    // --- Lens buttons and lists ---

    /** Zoom toggle state (mode, ?, ?): at 50M, the state the mode has at 12.5M unless that hides them. */
    public static Object zoomState(Object state, int mode, boolean a, boolean b, Class<?> owner, String stateField,
            String ultra) {
        try {
            if (state == null || !ultraHd(ultra, mode)) return state;
            Object normal = reread(owner, "a", null, mode, a, b);
            if (normal == null) return state;
            Field f = normal.getClass().getDeclaredField(stateField);
            f.setAccessible(true);
            return f.getInt(normal) != HIDDEN ? normal : state;
        } catch (Throwable t) {
            Log.once("ultra hd: zoom state", t);
            return state;
        }
    }

    /** Per-mode zoom list: at 50M, the list the mode has at 12.5M, without the 2x stop. */
    public static float[] zoomList(float[] list, int mode, Class<?> owner, String name, String ultra) {
        try {
            if (Boolean.TRUE.equals(READING_RATIO.get()) || !ultraHd(ultra, mode)) return list;
            Object full = reread(owner, name, null, mode);
            return full instanceof float[] && ((float[]) full).length > 0 ? lensStops((float[]) full) : list;
        } catch (Throwable t) {
            Log.once("ultra hd: zoom list", t);
            return list;
        }
    }

    /** The lens bar's ratio filter (ratios, ...): keeps every lens at 50M, without the 2x stop. */
    public static float[] keepLenses(float[] out, float[] in, String ultra) {
        try {
            if (in == null || out == null) return out;
            if (out.length < in.length && ultraHdNow(ultra)) return lensStops(in);
            if (ultraHd(ultra, PHOTO_MODE)) return lensStops(out);
        } catch (Throwable t) {
            Log.once("ultra hd: lens filter", t);
        }
        return out;
    }

    /** Photo module's "switch camera while zooming": allowed at 50M and on the ultra-wide and telephoto. */
    public static boolean allowLensSwitch(boolean allowed, Object module, String ultra) {
        if (allowed || module == null) return allowed;
        try {
            int mode = (Integer) module.getClass().getMethod("getModuleIndex").invoke(module);
            if (!lensMode(mode)) return false;
            Object camera = module.getClass().getMethod("getActualCameraId").invoke(module);
            return ultraHd(ultra, mode) || Integer.valueOf(UW_CAMERA).equals(camera)
                    || Integer.valueOf(TELE_CAMERA).equals(camera);
        } catch (Throwable t) {
            Log.once("ultra hd: lens switch check", t);
            return false;
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

    // --- Ratio ---

    /** Ratio component value (mode): at 50M in Photo, the saved ratio when it is 1:1 instead of the forced 4:3. */
    public static String ratio(String value, Object component, int mode, String ultra) {
        try {
            if (Boolean.TRUE.equals(READING_RATIO.get()) || !RATIO_4_3.equals(value) || !ultraHd(ultra, mode)) {
                return value;
            }
            Object saved = reread(component.getClass(), "getComponentValue", component, mode);
            return RATIO_1_1.equals(saved) ? (String) saved : value;
        } catch (Throwable t) {
            Log.once("ultra hd: ratio", t);
            return value;
        }
    }

    /** Before the ratio-change handler (ratio, ?): keeps Ultra HD on for a ratio 50M can produce. */
    public static void ratioChangeEnter(String ratio) {
        if (RATIO_1_1.equals(ratio) || RATIO_4_3.equals(ratio)) KEEP_ULTRA.set(Boolean.TRUE);
    }

    public static void ratioChangeExit() {
        KEEP_ULTRA.remove();
    }

    // --- Zoom at 50M ---

    static Object actualCamera(Object module) throws Exception {
        return module == null ? null : module.getClass().getMethod("getActualCameraId").invoke(module);
    }

    /**
     * Guard on the zoom manager's lens switch (old zoom, new zoom, module): at 50M the lens
     * stays put until the slide passes the next lens's stop.
     */
    public static boolean lensSwitchGo(float to, Object module, String ultra) {
        try {
            if (to >= TELE_STOP) return true;
            Object camera = actualCamera(module);
            if (!lensCamera(camera) || !ultraHdNow(ultra)) return true;
            if (to <= UW_STOP + 0.01f) return true;
            if (Integer.valueOf(TELE_CAMERA).equals(camera) && to <= 1.0f) return true;
            return Integer.valueOf(UW_CAMERA).equals(camera) && to >= 1.0f;
        } catch (Throwable t) {
            Log.once("ultra hd: lens switch", t);
            return true;
        }
    }

    /** The lens switch's old zoom: the crossed stop, so the camera's own checks switch lens there. */
    public static float lensSwitchFrom(float from, float to, Object module, String ultra) {
        try {
            if (to >= TELE_STOP) return from;
            Object camera = actualCamera(module);
            if (!lensCamera(camera) || !ultraHdNow(ultra)) return from;
            if (to <= UW_STOP + 0.01f) return Integer.valueOf(UW_CAMERA).equals(camera) ? from : TELE_STOP;
            if (Integer.valueOf(TELE_CAMERA).equals(camera) && to <= 1.0f) return TELE_STOP;
        } catch (Throwable t) {
            Log.once("ultra hd: lens switch", t);
        }
        return from;
    }

    /**
     * After the zoom manager's snap and end of slide: at 50M in Photo, moves the zoom to the stop
     * of the lens in use. fields names the manager's module reference and zoom value, "module#zoom".
     */
    public static void snapToLens(Object manager, String fields, String ultra) {
        try {
            if (!ultraHd(ultra, PHOTO_MODE)) return;
            String[] n = fields.split("#");
            Object ref = field(manager.getClass(), n[0]).get(manager);
            Object camera = actualCamera(ref == null ? null : ((Reference<?>) ref).get());
            if (!lensCamera(camera)) return;
            float now = field(manager.getClass(), n[1]).getFloat(manager);
            float stop = Integer.valueOf(UW_CAMERA).equals(camera) ? UW_STOP
                    : Integer.valueOf(TELE_CAMERA).equals(camera) ? Math.max(now, TELE_STOP) : 1.0f;
            if (Math.abs(now - stop) > 0.001f) {
                manager.getClass().getMethod("setZoomRatio", float.class).invoke(manager, stop);
            }
        } catch (Throwable t) {
            Log.once("ultra hd: snap", t);
        }
    }

    /**
     * Before the request builder's zoom step (builder, caps, config): at 50M the main camera's and
     * ultra-wide's requests go out at 1x (each held at its own field of view between stops), the
     * telephoto's at no less than 1x. cameraId is the static camera id getter, "class#method".
     */
    public static void zoomApplyBefore(Object caps, Object config, String cameraId, String zoomField, String ultra) {
        try {
            if (config == null || caps == null) return;
            Field zoom = field(config.getClass(), zoomField);
            float value = zoom.getFloat(config);
            if (value == 1.0f) return;
            String[] n = cameraId.split("#");
            Object camera = method(n[0], n[1]).invoke(null, caps);
            if (!lensCamera(camera) || (value > 1.0f && Integer.valueOf(TELE_CAMERA).equals(camera))
                    || !ultraHdNow(ultra)) return;
            SAVED_ZOOM.set(value);
            zoom.setFloat(config, 1.0f);
        } catch (Throwable t) {
            Log.once("ultra hd: zoom step", t);
        }
    }

    public static void zoomApplyAfter(Object config, String zoomField) {
        Float value = SAVED_ZOOM.get();
        if (value == null) return;
        SAVED_ZOOM.remove();
        try {
            field(config.getClass(), zoomField).setFloat(config, value);
        } catch (Throwable t) {
            Log.once("ultra hd: zoom restore", t);
        }
    }

    // --- Reflection ---

    /** Runs the renamed original of a wrapped method with "Ultra HD is on" answering false. */
    static Object reread(Class<?> owner, String name, Object self, Object... args) throws Exception {
        Method m = null;
        for (Method c : owner.getDeclaredMethods()) {
            if (c.getName().equals(name + "$xcam") && c.getParameterTypes().length == args.length) m = c;
        }
        if (m == null) throw new NoSuchMethodException(owner.getName() + "." + name + "$xcam");
        m.setAccessible(true);
        READING_RATIO.set(Boolean.TRUE);
        try {
            return m.invoke(self, args);
        } finally {
            READING_RATIO.remove();
        }
    }

    static Method method(String cls, String name) throws Exception {
        String key = cls + "#" + name;
        synchronized (METHODS) {
            Method m = METHODS.get(key);
            if (m == null) {
                for (Method c : Class.forName(cls, false, UltraHdLenses.class.getClassLoader()).getDeclaredMethods()) {
                    if (c.getName().equals(name)) m = c;
                }
                if (m == null) throw new NoSuchMethodException(key);
                m.setAccessible(true);
                METHODS.put(key, m);
            }
            return m;
        }
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // Declared further up.
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }
}
