package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Fastshot's AF/MF control works out depth of field from the HAL's
 * com.xiaomi.sensor.info.depthOfField table, which houji's HAL doesn't provide. The table is
 * then left null and opening the control crashed reading its length. The code already uses
 * built-in values when the table isn't three entries long, so a missing table is made empty.
 */
final class StreetFocus {
    private StreetFocus() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.streetFocusClass == null) return;
        try {
            Class<?> focus = cl.loadClass(build.streetFocusClass);
            Field table = focus.getDeclaredField(build.streetDofField);
            if (table.getType() != float[].class) {
                Entry.log("street: unexpected depth-of-field field, skipping");
                return;
            }
            table.setAccessible(true);
            Method load = focus.getDeclaredMethod(build.streetDofLoadMethod);
            XposedBridge.hookMethod(load, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (table.get(p.thisObject) == null) table.set(p.thisObject, new float[0]);
                }
            });
            Entry.log("street: AF/MF uses default depth of field");
        } catch (Throwable t) {
            Entry.log("street: depth of field not hooked: " + t);
        }
    }
}
