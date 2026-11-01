package dev.spundone.xcam17.aosp;

import java.lang.reflect.Field;

/** Ports of StreetFocus and ShortVideo. */
public final class Modes {
    static final String MILIVE_MODULE = "com.xiaomi.microfilm.milive.mode.MiLiveModule";

    private Modes() {}

    /**
     * Fastshot's depth-of-field table loader: houji's HAL has no
     * com.xiaomi.sensor.info.depthOfField, the table stays null and the AF/MF control crashed.
     * An empty table makes the code use its built-in values.
     */
    public static void afterDepthOfField(Object focus, String field) {
        try {
            Field f = focus.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.get(focus) == null) f.set(focus, new float[0]);
        } catch (Throwable t) {
            Log.once("street: depth of field table unreadable", t);
        }
    }

    /**
     * Short video (MiLive) asked for operating mode 0x8004, which houji's HAL runs as a video use
     * case needing a recording stream; it gets the standard mode (0). field names the module's
     * operating mode field.
     */
    public static int operatingMode(int mode, Object module, String field) {
        if (module == null || !MILIVE_MODULE.equals(module.getClass().getName())) return mode;
        try {
            Class<?> c = module.getClass();
            Field f = null;
            while (c != null && f == null) {
                try {
                    f = c.getDeclaredField(field);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f != null) {
                f.setAccessible(true);
                f.setInt(module, 0);
            }
        } catch (Throwable t) {
            Log.once("short video: operating mode field unreadable", t);
        }
        return 0;
    }
}
