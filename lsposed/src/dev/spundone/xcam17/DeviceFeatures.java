package dev.spundone.xcam17;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XposedBridge;

/**
 * The camera decides most per-phone features through a device profile: one class per
 * phone (com.mi.device.Houji for the Xiaomi 14) extending com.mi.device.common.Common, with
 * one no-argument boolean method per feature. Names are obfuscated, so each flag here was
 * identified by comparing what the Xiaomi 17 / 17 Ultra profiles answer with Houji's and
 * by the strings around each call site.
 *
 * Houji overrides many Common methods, so a flag is hooked on both classes: a hook on
 * Common alone misses any method Houji redefines.
 */
final class DeviceFeatures {
    // com.mi.device.common.Common and com.mi.device.Houji, as stored in the APK.
    static final String COMMON = "䏖䏚䏘䎛䏘䏜䎛䏑䏐䏃䏜䏖䏐"
            + "䎛䏖䏚䏘䏘䏚䏛䎛䏶䏚䏘䏘䏚䏛";
    static final String HOUJI = "䚵䚹䚻䛸䚻䚿䛸䚲䚳䚠䚿䚵䚳"
            + "䛸䚞䚹䚣䚼䚿";

    /** Flag method, and what turning it on does. */
    static final String[][] ENABLED = {
            {"z6", "front/rear camera switch while recording"},
            {"w5", "MiMediaCodecRecorder, which the recording camera switch needs"},
            {"h3", "Dolby Vision toggle in the viewfinder instead of settings (17 Ultra layout)"},
    };

    private DeviceFeatures() {}

    static void enable(ClassLoader cl) {
        Class<?> common;
        Class<?> houji;
        try {
            common = cl.loadClass(COMMON);
            houji = cl.loadClass(HOUJI);
        } catch (Throwable t) {
            Entry.log("features: device profile classes not found, skipping: " + t);
            return;
        }
        if (houji.getSuperclass() != common) {
            Entry.log("features: Houji no longer extends Common, skipping");
            return;
        }
        for (String[] flag : ENABLED) {
            int hooked = 0;
            for (Class<?> c : new Class<?>[] {common, houji}) {
                Method m = flagMethod(c, flag[0]);
                if (m != null) {
                    XposedBridge.hookMethod(m, new Entry.ConstantResult(Boolean.TRUE));
                    hooked++;
                }
            }
            Entry.log("features: " + flag[0] + " (" + flag[1] + ") " + (hooked > 0 ? "on" : "not found"));
        }
    }

    private static Method flagMethod(Class<?> c, String name) {
        try {
            Method m = c.getDeclaredMethod(name);
            if (m.getReturnType() == boolean.class && !Modifier.isStatic(m.getModifiers())
                    && !Modifier.isAbstract(m.getModifiers())) {
                return m;
            }
        } catch (NoSuchMethodException ignored) {
            // Houji doesn't override this flag; the Common hook covers it.
        }
        return null;
    }
}
