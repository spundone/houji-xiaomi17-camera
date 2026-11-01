package dev.spundone.xcam17;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Video filters are colour lookup tables the phone's firmware ships as
 * /odm/etc/camera/videofilter/<id>_<Name>.png. The 6.8 camera lists the 18 Pro's filters,
 * and several (the front camera's Poetry, Nocturne, Amber and others) have ids houji's firmware
 * has no table for: the filter engine finds no image and the viewfinder and recording go black.
 * This drops filters without a table from the list. A filter item's value carries the table id
 * in its low byte (Poetry 1930 is table 138, VIV 1921 is 129_LeicaVivid).
 */
final class VideoFilterLuts {
    static final String DIR = "/odm/etc/camera/videofilter";
    /** Values of video filter items: 0x700 plus the table id; 0x700 itself is Original. */
    static final int VIDEO_FILTER_BASE = 0x700;
    static final Pattern VALUE = Pattern.compile("mValue='(\\d+)'");

    private VideoFilterLuts() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.filterComponentClass == null) return;
        try {
            Set<Integer> tables = tables();
            if (tables.isEmpty()) {
                Entry.log("video filters: no tables in " + DIR + ", list left alone");
                return;
            }
            XposedBridge.hookMethod(cl.loadClass(build.filterComponentClass).getMethod("getItems"),
                    new DropMissing(tables));
            Entry.log("video filters: " + tables.size() + " tables found, others hidden");
        } catch (Throwable t) {
            Entry.log("video filters: not hooked: " + t);
        }
    }

    static Set<Integer> tables() {
        Set<Integer> ids = new HashSet<>();
        String[] names = new File(DIR).list();
        if (names == null) return ids;
        for (String n : names) {
            int cut = n.indexOf('_');
            if (cut <= 0 || !n.endsWith(".png")) continue;
            try {
                ids.add(Integer.parseInt(n.substring(0, cut)));
            } catch (NumberFormatException ignored) {
                // Not a table.
            }
        }
        return ids;
    }

    static final class DropMissing extends XC_MethodHook {
        private final Set<Integer> tables;

        DropMissing(Set<Integer> tables) {
            this.tables = tables;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if (!(p.getResult() instanceof List)) return;
            java.util.ArrayList<Object> items = new java.util.ArrayList<>((List<?>) p.getResult());
            int before = items.size();
            for (int i = items.size() - 1; i >= 0; i--) {
                Object item = items.get(i);
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
                if (!tables.contains(value - VIDEO_FILTER_BASE)) items.remove(i);
            }
            if (items.size() != before) p.setResult(items);
        }
    }
}
