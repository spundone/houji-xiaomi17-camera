package dev.spundone.xcam17;

import android.util.SparseArray;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Profile answers that are tables rather than single values, set to what houji's own 6.7
 * profile (com.mi.device.Houji) had:
 * - Pro exposure ranges: 1/8000 s shortest shutter instead of 1/4000 s (6.7 E1, 6.8 Q0). The
 *   HAL's shortest exposure is 61 us on the main camera.
 * - Portrait zoom toggles 1x and 2.6x (6.7 K1 entry 171, 6.8 X0), with flag Z3 in
 *   DeviceFeatures. 2.6x is the telephoto's real field of view relative to the main camera.
 * - Fastshot focal presets 28, 35, 50 and 75 mm (6.7 t1, 6.8 J0): 35 mm crops the main camera
 *   (23 mm), 50 mm crops 2x main (46 mm), 75 mm crops the telephoto (60 mm).
 */
final class ProfileTables {
    static final String SHORTEST_EXPOSURE_68 = ":250000:";
    static final String SHORTEST_EXPOSURE_HOUJI = ":125000:";
    static final int PORTRAIT_MODE = 171;
    static final Float[] PORTRAIT_ZOOM = {1.0f, 2.6f};

    private ProfileTables() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.exposureRangeMethod == null) return;
        Class<?>[] profiles = new Class<?>[build.profiles.length];
        try {
            for (int i = 0; i < profiles.length; i++) profiles[i] = cl.loadClass(build.profiles[i]);
        } catch (Throwable t) {
            Entry.log("profile tables: profile classes not found: " + t);
            return;
        }
        hookAll(profiles, build.exposureRangeMethod, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (r instanceof String) p.setResult(((String) r).replace(SHORTEST_EXPOSURE_68, SHORTEST_EXPOSURE_HOUJI));
            }
        }, "Pro shortest shutter 1/8000 s");
        hookAll(profiles, build.zoomTogglesMethod, SparseArray.class, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam p) {
                SparseArray<Object> r = (SparseArray<Object>) p.getResult();
                if (r == null) {
                    r = new SparseArray<>();
                    p.setResult(r);
                }
                if (r.get(PORTRAIT_MODE) == null) r.put(PORTRAIT_MODE, PORTRAIT_ZOOM.clone());
            }
        }, "portrait 1x and 2.6x");
        try {
            Class<?> preset = cl.loadClass(build.focalPresetClass);
            Constructor<?> plain = preset.getDeclaredConstructor();
            Constructor<?> crop = preset.getDeclaredConstructor(int.class, float.class, float.class);
            hookAll(profiles, build.streetPresetsMethod, SparseArray.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (p.getResult() != null) return;
                    SparseArray<Object> table = new SparseArray<>();
                    table.put(28, plain.newInstance());
                    table.put(35, crop.newInstance(3, 35f, 23f));
                    table.put(50, crop.newInstance(2, 50f, 46f));
                    table.put(75, crop.newInstance(3, 75f, 60f));
                    p.setResult(table);
                }
            }, "Fastshot 28/35/50/75 mm presets");
        } catch (Throwable t) {
            Entry.log("profile tables: Fastshot presets not hooked: " + t);
        }
    }

    static void hookAll(Class<?>[] profiles, String name, Class<?> type, XC_MethodHook hook, String what) {
        int hooked = 0;
        for (Class<?> c : profiles) {
            try {
                Method m = c.getDeclaredMethod(name);
                if (m.getReturnType() != type || Modifier.isStatic(m.getModifiers())
                        || Modifier.isAbstract(m.getModifiers())) continue;
                XposedBridge.hookMethod(m, hook);
                hooked++;
            } catch (NoSuchMethodException ignored) {
                // Inherited from the parent profile, which is hooked instead.
            }
        }
        Entry.log("profile tables: " + what + (hooked > 0 ? "" : " not found"));
    }
}
