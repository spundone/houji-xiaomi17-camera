package dev.spundone.xcam17.aosp;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Constant answers the patches can't write as a single smali constant: int arrays and lists,
 * given as comma-separated text. Port of DeviceFeatures' Ints, AppendInts and ListResult.
 */
public final class Values {
    private Values() {}

    /** A fresh int[] on every call, e.g. "16". */
    public static int[] ints(String csv) {
        String[] parts = csv.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
        return out;
    }

    /** The method's own int[] with the listed values added once each, e.g. mode or lens lists. */
    public static int[] appendInts(int[] have, String csv) {
        if (have == null) return null;
        int[] extra = ints(csv);
        int[] out = Arrays.copyOf(have, have.length + extra.length);
        int n = have.length;
        outer:
        for (int e : extra) {
            for (int h : have) if (h == e) continue outer;
            out[n++] = e;
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** A fresh ArrayList of Integer on every call, e.g. "30,60". */
    public static ArrayList<Integer> intList(String csv) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int v : ints(csv)) out.add(v);
        return out;
    }

    /** For "before" patches that force a boolean argument off. */
    public static boolean no(boolean ignored) {
        return false;
    }
}
