package dev.spundone.xcam17.aosp;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

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
public final class CameraHaptics {
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

    /**
     * After the vibrator context's instance accessor: gives a context without a strategy one that
     * plays Android's predefined effects. field names the strategy slot; strategy is its interface.
     */
    public static Object fillStrategy(Object context, String field, Class<?> strategy) {
        if (context == null) return null;
        try {
            Field slot = context.getClass().getDeclaredField(field);
            slot.setAccessible(true);
            if (slot.get(context) != null || slot.getType() != strategy || !strategy.isInterface()) return context;
            Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app == null) return context;
            Vibrator vibrator = ((Context) app).getSystemService(Vibrator.class);
            if (vibrator == null || !vibrator.hasVibrator()) return context;
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
            Log.once("haptics: camera haptics played with Android's standard effects");
        } catch (Throwable t) {
            Log.once("haptics: strategy not set", t);
        }
        return context;
    }

    /**
     * miuix's HapticCompat.performHapticFeedback(view, id): a HyperOS id becomes the nearest
     * standard feedback constant, which HapticCompat hands to View.performHapticFeedback.
     */
    public static int feedbackId(int id) {
        if (id < MIUI_BASE) return id;
        int index = id - MIUI_BASE;
        return index < MIUI_TO_AOSP.length ? MIUI_TO_AOSP[index] : id;
    }
}
