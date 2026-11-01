package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * The photo style ("cvtype") choices come from the HAL mask xiaomi.capabilities.
 * stylizationTypeMask, read bit by bit by the capabilities class. houji reports 6 (bits 1
 * and 2: Leica Authentic and Leica Vibrant). Bit 3 adds a third choice, Natural, which in
 * Photo mode asks for Xiaomi's look without the Leica tone.
 *
 * In Portrait the same bit offers Original portrait, which also needs bit 0. houji's HAL
 * renders it from the main camera, so Portrait's 2.6x became a main-camera crop; it is left
 * out of Portrait's style list, keeping Master and Leica portrait.
 */
final class NaturalStyle {
    static final int PORTRAIT = 171;
    /** Mode the style component is building its list for, on this thread. */
    static final ThreadLocal<Integer> LIST_MODE = new ThreadLocal<>();

    private NaturalStyle() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.styleMaskMethod == null) return;
        try {
            Method bit = cl.loadClass(build.capabilitiesClass).getDeclaredMethod(build.styleMaskMethod, int.class);
            if (bit.getReturnType() != boolean.class) {
                Entry.log("style: unexpected signature, skipping");
                return;
            }
            XposedBridge.hookMethod(bit, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (Integer.valueOf(3).equals(p.args[0])) p.setResult(Boolean.TRUE);
                }
            });
            Entry.log("style: natural (no Leica look) offered");
        } catch (Throwable t) {
            Entry.log("style: not hooked: " + t);
            return;
        }
        hookPortrait(cl, build);
    }

    static void hookPortrait(ClassLoader cl, CameraBuild build) {
        if (build.styleListClass == null) return;
        try {
            Class<?> caps = cl.loadClass(build.capabilitiesClass);
            Method original = cl.loadClass(build.capsChecksClass).getDeclaredMethod(build.originalStyleMethod, caps);
            Method list = null;
            for (Method m : cl.loadClass(build.styleListClass).getDeclaredMethods()) {
                if (m.getName().equals(build.styleListMethod) && m.getParameterTypes().length == 1) list = m;
            }
            Field mode = list == null ? null : list.getParameterTypes()[0].getDeclaredField(build.styleListModeField);
            if (original.getReturnType() != boolean.class || list == null || mode.getType() != int.class) {
                Entry.log("style: portrait list has an unexpected shape, skipping");
                return;
            }
            mode.setAccessible(true);
            XposedBridge.hookMethod(list, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    LIST_MODE.set(p.args[0] == null ? null : mode.getInt(p.args[0]));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    LIST_MODE.remove();
                }
            });
            XposedBridge.hookMethod(original, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (Integer.valueOf(PORTRAIT).equals(LIST_MODE.get())) p.setResult(Boolean.FALSE);
                }
            });
            hookSavedOriginal(list.getDeclaringClass());
            Entry.log("style: Original portrait left out (houji's HAL renders it from the main camera)");
        } catch (Throwable t) {
            Entry.log("style: portrait not hooked: " + t);
        }
    }

    /** Portrait's style value for Original portrait, and the one used instead (Master portrait). */
    static final String ORIGINAL_PORTRAIT = "2";
    static final String MASTER_PORTRAIT = "0";

    /**
     * The style component returns its saved value without checking it against the list, so a
     * Portrait saved on Original portrait would keep it. It is read as Master portrait and saved
     * that way the first time it is seen.
     */
    static void hookSavedOriginal(Class<?> style) throws Exception {
        Field current = null;
        for (Class<?> k = style; k != null && current == null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getName().equals("mCurrentMode") && f.getType() == int.class) current = f;
            }
        }
        Method value = style.getDeclaredMethod("getComponentValue", int.class);
        Method persisted = style.getDeclaredMethod("getPersistValue", int.class);
        Method save = style.getMethod("setComponentValue", int.class, String.class);
        if (current == null) throw new NoSuchFieldException("mCurrentMode");
        current.setAccessible(true);
        final Field mode = current;
        final ThreadLocal<Boolean> saving = new ThreadLocal<>();
        XC_MethodHook replace = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                int m = p.method == value ? mode.getInt(p.thisObject) : (Integer) p.args[0];
                if (m != PORTRAIT || !ORIGINAL_PORTRAIT.equals(p.getResult())) return;
                p.setResult(MASTER_PORTRAIT);
                if (Boolean.TRUE.equals(saving.get())) return;
                saving.set(Boolean.TRUE);
                try {
                    save.invoke(p.thisObject, PORTRAIT, MASTER_PORTRAIT);
                    Entry.log("style: saved Original portrait changed to Master portrait");
                } finally {
                    saving.remove();
                }
            }
        };
        XposedBridge.hookMethod(value, replace);
        XposedBridge.hookMethod(persisted, replace);
    }
}
