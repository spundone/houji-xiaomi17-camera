package dev.spundone.xcam17;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * The camera's haptics (shutter, zoom ticks, mode switches, Pro dials, filter strip...) go
 * through one vibrator context that only acts once it holds a "linear motor strategy", and it
 * creates that strategy only if HyperOS's miui.util.HapticFeedbackUtil exists. On AOSP it
 * doesn't, so the context stays empty and nothing vibrates, although houji's vibrator HAL
 * supports the standard effects. This gives the context a strategy that plays Android's
 * predefined effects, with the same per-action choices HyperOS's version-2 strategy makes
 * (heavier for the shutter and mode switches, ticks for zoom and dials, nothing for EV).
 *
 * The miuix widgets (sliders, scroll views) send HyperOS haptic ids (0x10000000 and up) that
 * only HyperOS providers handle; those are mapped to the nearest standard feedback constants.
 */
final class CameraHaptics {
    /** HyperOS haptic ids start here; lower ids are standard View feedback constants. */
    static final int MIUI_BASE = 0x10000000;

    /** Strategy method (same letter as the context's trigger) to a predefined effect, or -1 for none. */
    static final Map<String, Integer> EFFECTS = new HashMap<>();
    static {
        EFFECTS.put("m", VibrationEffect.EFFECT_HEAVY_CLICK); // shutter, record, countdown
        EFFECTS.put("f", VibrationEffect.EFFECT_CLICK);       // burst frame
        EFFECTS.put("q", VibrationEffect.EFFECT_TICK);        // zoom ring and lens buttons
        EFFECTS.put("n", VibrationEffect.EFFECT_HEAVY_CLICK); // zoom panel pop-up
        EFFECTS.put("a", VibrationEffect.EFFECT_CLICK);       // mode switch, seek bars
        EFFECTS.put("d", VibrationEffect.EFFECT_CLICK);       // editing the mode list
        EFFECTS.put("r", VibrationEffect.EFFECT_TICK);        // filter strip, makeup, level
        EFFECTS.put("c", VibrationEffect.EFFECT_TICK);        // aperture / bokeh dial
        EFFECTS.put("j", VibrationEffect.EFFECT_TICK);        // colour picker
        EFFECTS.put("h", VibrationEffect.EFFECT_CLICK);       // front/rear switch
        EFFECTS.put("o", VibrationEffect.EFFECT_CLICK);       // image print
        EFFECTS.put("e", VibrationEffect.EFFECT_TICK);        // zoom scale
        EFFECTS.put("g", VibrationEffect.EFFECT_TICK);        // zoom scale
        EFFECTS.put("i", VibrationEffect.EFFECT_TICK);        // Pro dial, light tick
        EFFECTS.put("p", VibrationEffect.EFFECT_TICK);        // Pro dial tick
        EFFECTS.put("k", VibrationEffect.EFFECT_TICK);        // hardware ring
    }

    /** HyperOS haptic id (low byte) to a standard HapticFeedbackConstants value. */
    static final int[] MIUI_TO_AOSP = {
            HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,        // 00 VIRTUAL_RELEASE
            HapticFeedbackConstants.VIRTUAL_KEY,                // 01 TAP_NORMAL
            HapticFeedbackConstants.KEYBOARD_TAP,               // 02 TAP_LIGHT
            HapticFeedbackConstants.CONTEXT_CLICK,              // 03 FLICK
            HapticFeedbackConstants.TOGGLE_ON,                  // 04 SWITCH
            HapticFeedbackConstants.SEGMENT_TICK,               // 05 MESH_HEAVY
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,      // 06 MESH_NORMAL
            HapticFeedbackConstants.TEXT_HANDLE_MOVE,           // 07 MESH_LIGHT
            HapticFeedbackConstants.LONG_PRESS,                 // 08 LONG_PRESS
            HapticFeedbackConstants.CONTEXT_CLICK,              // 09 POPUP_NORMAL
            HapticFeedbackConstants.CONTEXT_CLICK,              // 0a POPUP_LIGHT
            HapticFeedbackConstants.DRAG_START,                 // 0b PICK_UP
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, // 0c SCROLL_EDGE
            HapticFeedbackConstants.GESTURE_START,              // 0d TRIGGER_DRAWER
            HapticFeedbackConstants.CLOCK_TICK,                 // 0e FLICK_LIGHT
            HapticFeedbackConstants.LONG_PRESS,                 // 0f HOLD
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, // 10 BOUNDARY_SPATIAL
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, // 11 BOUNDARY_TIME
            HapticFeedbackConstants.CONFIRM,                    // 12 BUTTON_LARGE
            HapticFeedbackConstants.VIRTUAL_KEY,                // 13 BUTTON_MIDDLE
            HapticFeedbackConstants.VIRTUAL_KEY,                // 14 BUTTON_SMALL
            HapticFeedbackConstants.CLOCK_TICK,                 // 15 GEAR_LIGHT
            HapticFeedbackConstants.SEGMENT_TICK,               // 16 GEAR_HEAVY
            HapticFeedbackConstants.KEYBOARD_TAP,               // 17 KEYBOARD
            HapticFeedbackConstants.REJECT,                     // 18 ALERT
            HapticFeedbackConstants.TOGGLE_ON,                  // 19 ZAXIS_SWITCH
    };

    private CameraHaptics() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.vibratorContextClass == null) return;
        try {
            Class<?> context = cl.loadClass(build.vibratorContextClass);
            Class<?> strategy = cl.loadClass(build.hapticStrategyClass);
            Field slot = context.getDeclaredField(build.hapticStrategyField);
            if (!strategy.isInterface() || slot.getType() != strategy) {
                Entry.log("haptics: unexpected shape, skipping");
                return;
            }
            slot.setAccessible(true);
            XposedBridge.hookMethod(context.getDeclaredMethod(build.vibratorContextInstance),
                    new FillStrategy(slot, strategy));
            Entry.log("haptics: camera haptics played with Android's standard effects");
        } catch (Throwable t) {
            Entry.log("haptics: not hooked: " + t);
        }
        try {
            XposedBridge.hookMethod(cl.loadClass("miuix.view.HapticCompat")
                    .getDeclaredMethod("performHapticFeedback", View.class, int.class), new MiuixIds());
        } catch (Throwable t) {
            Entry.log("haptics: miuix widgets not hooked: " + t);
        }
    }

    static final class FillStrategy extends XC_MethodHook {
        private final Field slot;
        private final Class<?> strategy;

        FillStrategy(Field slot, Class<?> strategy) {
            this.slot = slot;
            this.strategy = strategy;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Object context = p.getResult();
            if (context == null || slot.get(context) != null) return;
            Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app == null) return;
            Vibrator vibrator = ((Context) app).getSystemService(Vibrator.class);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            slot.set(context, Proxy.newProxyInstance(strategy.getClassLoader(), new Class<?>[] {strategy},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "hashCode": return System.identityHashCode(proxy);
                                case "equals": return proxy == args[0];
                                default: return "xcam17 haptics";
                            }
                        }
                        Integer effect = EFFECTS.get(method.getName());
                        if (effect != null) {
                            vibrator.vibrate(VibrationEffect.createPredefined(effect),
                                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
                        }
                        Class<?> r = method.getReturnType();
                        return r == boolean.class ? Boolean.FALSE : null;
                    }));
        }
    }

    static final class MiuixIds extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            View view = (View) p.args[0];
            int id = (Integer) p.args[1];
            if (view == null || id < MIUI_BASE) return;
            int index = id - MIUI_BASE;
            if (index >= MIUI_TO_AOSP.length) return;
            p.setResult(view.performHapticFeedback(MIUI_TO_AOSP[index]));
        }
    }
}
