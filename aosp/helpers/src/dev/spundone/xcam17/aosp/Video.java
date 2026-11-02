package dev.spundone.xcam17.aosp;

import android.util.Range;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ports of VideoFilterLuts and FillLight60. */
public final class Video {
    static final String LUT_DIR = "/odm/etc/camera/videofilter";
    /** Values of video filter items: 0x700 plus the table id; 0x700 itself is Original. */
    static final int VIDEO_FILTER_BASE = 0x700;
    static final Pattern VALUE = Pattern.compile("mValue='(\\d+)'");
    /** Flash value of the front screen fill light, and the video mode index. */
    static final String SCREEN_LIGHT = "104";
    static final int VIDEO_MODE = 162;

    private static Set<Integer> tables;

    private Video() {}

    /**
     * Video filter items: the 6.8 list has filters whose colour table houji's firmware doesn't
     * ship (the preview goes black); those are dropped. An item's value carries the table id
     * in its low byte.
     */
    public static List<?> filterItems(List<?> items) {
        if (items == null) return null;
        Set<Integer> have = tables();
        if (have.isEmpty()) return items;
        ArrayList<Object> out = new ArrayList<>(items);
        for (int i = out.size() - 1; i >= 0; i--) {
            Object item = out.get(i);
            if (item == null) continue;
            Matcher m = VALUE.matcher(item.toString());
            if (!m.find()) continue;
            int value;
            try {
                value = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (value <= VIDEO_FILTER_BASE || value > VIDEO_FILTER_BASE + 0xFF) continue;
            if (!have.contains(value - VIDEO_FILTER_BASE)) out.remove(i);
        }
        return out.size() == items.size() ? items : out;
    }

    static synchronized Set<Integer> tables() {
        if (tables != null) return tables;
        Set<Integer> ids = new HashSet<>();
        String[] names = new File(LUT_DIR).list();
        if (names != null) {
            for (String n : names) {
                int cut = n.indexOf('_');
                if (cut <= 0 || !n.endsWith(".png")) continue;
                try {
                    ids.add(Integer.parseInt(n.substring(0, cut)));
                } catch (NumberFormatException ignored) {
                    // Not a table.
                }
            }
        }
        tables = ids;
        return ids;
    }

    /**
     * Video fps range setters: with the front screen fill light on, a dynamic [30, 60] range
     * becomes a fixed [60, 60], so the file doesn't drop to ~35 fps. components is the class
     * with the static settings-components accessor componentsMethod, flash the flash component.
     */
    public static Range<Integer> fillLightRange(Range<Integer> range, Class<?> components, String componentsMethod,
            Class<?> flash) {
        if (range == null || !Integer.valueOf(60).equals(range.getUpper())
                || Integer.valueOf(60).equals(range.getLower())) {
            return range;
        }
        try {
            Object all = components.getDeclaredMethod(componentsMethod).invoke(null);
            Method lookup = all.getClass().getMethod("x", Class.class);
            Object component = lookup.invoke(all, flash);
            Object value = component == null ? null
                    : flash.getMethod("getComponentValue", int.class).invoke(component, VIDEO_MODE);
            if (SCREEN_LIGHT.equals(value)) return new Range<>(60, 60);
        } catch (Throwable t) {
            Log.once("fill light: flash state unreadable", t);
        }
        return range;
    }
}
