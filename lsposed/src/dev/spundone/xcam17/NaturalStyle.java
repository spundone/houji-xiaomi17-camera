package dev.spundone.xcam17;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * The photo style ("cvtype") choices come from the HAL mask xiaomi.capabilities.
 * stylizationTypeMask, read bit by bit by the capabilities class. houji reports 6 (bits 1
 * and 2: Leica Authentic and Leica Vibrant). Bit 3 adds a third choice, Natural, which in
 * Photo mode asks for Xiaomi's look without the Leica tone.
 */
final class NaturalStyle {
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
        }
    }
}
