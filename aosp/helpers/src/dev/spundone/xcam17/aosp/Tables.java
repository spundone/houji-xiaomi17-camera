package dev.spundone.xcam17.aosp;

import android.util.SparseArray;

import java.lang.reflect.Constructor;

/**
 * Port of ProfileTables: profile answers that are tables, set to what houji's own 6.7 profile had.
 */
public final class Tables {
    static final String SHORTEST_EXPOSURE_68 = ":250000:";
    static final String SHORTEST_EXPOSURE_HOUJI = ":125000:";
    static final int PORTRAIT_MODE = 171;

    private Tables() {}

    /** Pro exposure ranges: 1/8000 s shortest shutter instead of 1/4000 s. */
    public static String exposureRanges(String ranges) {
        return ranges == null ? null : ranges.replace(SHORTEST_EXPOSURE_68, SHORTEST_EXPOSURE_HOUJI);
    }

    /** Zoom toggles per mode: Portrait gets 1x and 2.6x (the telephoto's real field of view). */
    @SuppressWarnings("unchecked")
    public static SparseArray<Object> zoomToggles(SparseArray<Object> toggles) {
        if (toggles == null) toggles = new SparseArray<>();
        if (toggles.get(PORTRAIT_MODE) == null) toggles.put(PORTRAIT_MODE, new Float[] {1.0f, 2.6f});
        return toggles;
    }

    /**
     * Fastshot focal presets 28, 35, 50 and 75 mm when the profile has none. preset is the
     * preset class: a no-argument constructor for the native 28 mm and (camera, mm, crop-from mm).
     */
    public static SparseArray<Object> streetPresets(SparseArray<Object> presets, Class<?> preset) {
        if (presets != null) return presets;
        try {
            Constructor<?> plain = preset.getDeclaredConstructor();
            Constructor<?> crop = preset.getDeclaredConstructor(int.class, float.class, float.class);
            plain.setAccessible(true);
            crop.setAccessible(true);
            SparseArray<Object> table = new SparseArray<>();
            table.put(28, plain.newInstance());
            table.put(35, crop.newInstance(3, 35f, 23f));
            table.put(50, crop.newInstance(2, 50f, 46f));
            table.put(75, crop.newInstance(3, 75f, 60f));
            return table;
        } catch (Throwable t) {
            Log.once("profile tables: Fastshot presets not built", t);
            return null;
        }
    }
}
