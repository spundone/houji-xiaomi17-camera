package dev.spundone.xcam17;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XposedBridge;

/**
 * The camera decides most per-phone features through a device profile: one class per
 * phone (com.mi.device.Houji for the Xiaomi 14) extending com.mi.device.common.Common, with
 * one no-argument method per feature. Names are obfuscated, so each flag here was identified
 * by comparing what newer phones' profiles answer with Houji's and by the strings around each
 * call site. Flag names differ between camera builds, so each build has its own list.
 *
 * Phone profiles override many Common methods, so a flag is hooked on every profile class
 * in the chain: a hook on Common alone misses any method the phone's class redefines.
 */
final class DeviceFeatures {
    /**
     * Flag method, the value it should answer, and what that does. An int[] value is appended
     * to the int[] the method already returns (mode lists), rather than replacing it; a List
     * value is answered as a fresh ArrayList on every call.
     */
    static final class Flag {
        final String method;
        final Object value;
        final String what;

        Flag(String method, Object value, String what) {
            this.method = method;
            this.value = value;
            this.what = what;
        }
    }

    // 6.7: com.mi.device.common.Common and com.mi.device.Houji, as stored in the APK.
    static final String[] PROFILES_67 = {
            "䏖䏚䏘䎛䏘䏜䎛䏑䏐䏃䏜䏖䏐"
                    + "䎛䏖䏚䏘䏘䏚䏛䎛䏶䏚䏘䏘䏚䏛",
            "䚵䚹䚻䛸䚻䚿䛸䚲䚳䚠䚿䚵䚳"
                    + "䛸䚞䚹䚣䚼䚿",
    };
    static final Flag[] FLAGS_67 = {
            new Flag("z6", Boolean.TRUE, "front/rear camera switch while recording"),
            new Flag("w5", Boolean.TRUE, "MiMediaCodecRecorder, which the recording camera switch needs"),
            new Flag("h3", Boolean.TRUE, "Dolby Vision toggle in the viewfinder instead of settings (17 Ultra layout)"),
            new Flag("G4", Boolean.TRUE, "Live moments option for photos taken while recording"),
            new Flag("K6", Boolean.TRUE, "Live moments (motion photos) while recording video"),
            new Flag("U5", Boolean.TRUE, "subject tracking focus in Pro photo and Pro video as well"),
    };

    // 6.8: com.mi.device.common.Common and com.mi.device.xiaomi.CommonFlagship (6.8 has no
    // Houji profile; Entry.hookMissingProfile substitutes CommonFlagship).
    static final String[] PROFILES_68 = {
            "蹈蹄蹆踅蹆蹂踅蹏蹎蹝蹂蹈蹎踅"
                    + "蹈蹄蹆蹆蹄蹅踅蹨蹄蹆蹆蹄蹅",
            "晱晽晿昼晿晻昼晶晷晤晻晱晷昼"
                    + "晪晻晳晽晿晻昼晑晽晿晿晽晼"
                    + "晔晾晳晵晡智晻晢",
    };
    static final Flag[] FLAGS_68 = {
            new Flag("q0", 2, "style version 2: Filter parameters panel with Noise (grain), as on the 18 Pro"),
            new Flag("W3", Boolean.TRUE, "front/rear camera switch while recording"),
            new Flag("P1", Boolean.TRUE, "Dolby Vision toggle in the viewfinder instead of settings"),
            new Flag("d4", Boolean.TRUE, "Take photos while recording: Live moments (motion photo) or regular photo"),
            new Flag("C3", Boolean.TRUE, "Motion tracking focus in Pro photo as well as Photo"),
            new Flag("f3", Boolean.TRUE, "Import LUT for Pro video Log"),
            new Flag("t1", Boolean.TRUE, "Ultra HD (full-resolution) toggle in Photo mode, not just Pro"),
            new Flag("W0", java.util.Arrays.asList(30, 60), "front fill light (screen) allowed at 60 fps video, as on the 18 Pro"),
            new Flag("w0", new int[] {0, 20, 21}, "50MP lens roles: main, telephoto, ultra-wide (houji's 6.7 profile)"),
            new Flag("v0", "4:8192x6144", "main camera 50MP size, as houji's 6.7 profile"),
            new Flag("y0", "4:8160x6144", "ultra-wide 50MP size, as houji's 6.7 profile"),
            new Flag("u0", "4:8160x6144", "telephoto 50MP size, as houji's 6.7 profile"),
            new Flag("c2", Boolean.TRUE, "Street (Fastshot) mode and its settings switch; houji's 6.7 profile has it (M3)"),
            new Flag("G2", Boolean.TRUE, "Dual video: front and rear recorded together (More > Dual video)"),
            new Flag("i3", Boolean.TRUE, "macro supported (Super macro toggle in Photo)"),
            new Flag("Z0", "tele", "macro on the telephoto (telemacro), as houji's 6.7 profile has it"),
            new Flag("e2", Boolean.TRUE, "label the macro toggle Super macro"),
            new Flag("j3", Boolean.TRUE, "macro in mode 172 as well"),
            new Flag("K2", Boolean.TRUE, "macro in the fast-mode config list"),
    };

    private DeviceFeatures() {}

    static void enable(ClassLoader cl, String[] profiles, Flag[] flags) {
        Class<?>[] classes = new Class<?>[profiles.length];
        try {
            for (int i = 0; i < profiles.length; i++) classes[i] = cl.loadClass(profiles[i]);
        } catch (Throwable t) {
            Entry.log("features: device profile classes not found, skipping: " + t);
            return;
        }
        for (int i = 1; i < classes.length; i++) {
            if (!classes[0].isAssignableFrom(classes[i])) {
                Entry.log("features: profile no longer extends Common, skipping");
                return;
            }
        }
        for (Flag flag : flags) {
            Class<?> type = flag.value instanceof Boolean ? boolean.class
                    : flag.value instanceof String ? String.class
                    : flag.value instanceof int[] ? int[].class
                    : flag.value instanceof java.util.List ? java.util.ArrayList.class : int.class;
            int hooked = 0;
            for (Class<?> c : classes) {
                Method m = flagMethod(c, flag.method, type);
                if (m != null) {
                    XposedBridge.hookMethod(m, flag.value instanceof int[] ? new AppendInts((int[]) flag.value)
                            : flag.value instanceof java.util.List ? new ListResult((java.util.List<?>) flag.value)
                            : new Entry.ConstantResult(flag.value));
                    hooked++;
                }
            }
            Entry.log("features: " + flag.method + " (" + flag.what + ") " + (hooked > 0 ? "on" : "not found"));
        }
    }

    static final class ListResult extends de.robv.android.xposed.XC_MethodHook {
        private final java.util.List<?> value;

        ListResult(java.util.List<?> value) {
            this.value = value;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            p.setResult(new java.util.ArrayList<>(value));
        }
    }

    static final class AppendInts extends de.robv.android.xposed.XC_MethodHook {
        private final int[] extra;

        AppendInts(int[] extra) {
            this.extra = extra;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            int[] have = (int[]) p.getResult();
            if (have == null) return;
            int[] out = java.util.Arrays.copyOf(have, have.length + extra.length);
            int n = have.length;
            outer:
            for (int e : extra) {
                for (int h : have) if (h == e) continue outer;
                out[n++] = e;
            }
            p.setResult(n == out.length ? out : java.util.Arrays.copyOf(out, n));
        }
    }

    private static Method flagMethod(Class<?> c, String name, Class<?> type) {
        try {
            Method m = c.getDeclaredMethod(name);
            if (m.getReturnType() == type && !Modifier.isStatic(m.getModifiers())
                    && !Modifier.isAbstract(m.getModifiers())) {
                return m;
            }
        } catch (NoSuchMethodException ignored) {
            // This class doesn't override the flag; the hook on its parent covers it.
        }
        return null;
    }
}
