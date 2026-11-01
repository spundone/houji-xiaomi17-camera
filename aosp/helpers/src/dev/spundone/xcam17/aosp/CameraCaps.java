package dev.spundone.xcam17.aosp;

import android.hardware.camera2.CameraCharacteristics;

import java.lang.reflect.Field;

/**
 * Answers on the camera capabilities class: ports of NaturalStyle, OpenGate, VideoCaps and
 * TeleFallback's tag check.
 */
public final class CameraCaps {
    static final String TELE_FALLBACK_DISABLE_TAG = "com.xiaomi.teleFallback.isDisable";
    private static final ThreadLocal<Boolean> IN_QUALITY_CHECK = new ThreadLocal<>();

    private CameraCaps() {}

    /**
     * Style mask bit (xiaomi.capabilities.stylizationTypeMask, 6 on houji): bit 3 offers the
     * Original photo style. houji's HAL doesn't list it; the camera sends value 2 for it.
     */
    public static boolean styleBit(boolean supported, int bit) {
        return supported || bit == 3;
    }

    /**
     * Open Gate (4096x3072 in Pro video) on rear cameras: houji's HAL doesn't declare the
     * feature but its rear cameras stream 4096x3072 at 30 fps. field names the capabilities'
     * CameraCharacteristics field.
     */
    public static boolean openGate(boolean supported, Object caps, String field) {
        if (supported || caps == null) return supported;
        try {
            Field f = caps.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object c = f.get(caps);
            if (!(c instanceof CameraCharacteristics)) return false;
            Integer facing = ((CameraCharacteristics) c).get(CameraCharacteristics.LENS_FACING);
            return facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
        } catch (Throwable t) {
            Log.once("open gate: capabilities unreadable", t);
            return false;
        }
    }

    /** Video quality check (filters on): video filters allowed up to 4K, inside the check only. */
    public static void qualityEnter() {
        IN_QUALITY_CHECK.set(Boolean.TRUE);
    }

    public static void qualityExit() {
        IN_QUALITY_CHECK.remove();
    }

    public static boolean allScenesDolby(boolean supported) {
        return supported || Boolean.TRUE.equals(IN_QUALITY_CHECK.get());
    }

    /** "Is this vendor tag available": yes for the telephoto fallback disable tag. */
    public static boolean tagAvailable(boolean available, String tag) {
        return available || TELE_FALLBACK_DISABLE_TAG.equals(tag);
    }
}
