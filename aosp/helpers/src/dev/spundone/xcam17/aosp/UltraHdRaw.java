package dev.spundone.xcam17.aosp;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of UltraHdRaw: 50MP RAW on the ultra-wide and telephoto in Pro. The HAL's per-session
 * stream maps leave RAW16 out for those two cameras, though both list 8160x6144 RAW16 in
 * xiaomi.scaler.availableStreamConfigurations; that size is added to the RAW16 list of the 50MP
 * session modes, and answered as Pro's RAW size while 50MP is on. See the LSPosed hook.
 *
 * cameraId is the static camera id getter taking the capabilities, "class#method"; ultra is
 * the "Ultra HD is on" check, "class#method".
 */
public final class UltraHdRaw {
    static final int RAW16 = 32;
    static final int[] SESSIONS = {0x80f3, 0x9007, 0x900c, 0x900e};
    static final int[] CAMERAS = {3, 4};
    static final CameraCharacteristics.Key<int[]> XIAOMI_STREAMS =
            new CameraCharacteristics.Key<>("xiaomi.scaler.availableStreamConfigurations", int[].class);

    private UltraHdRaw() {}

    /** After the capabilities' sizes for (format, session): the full sensor RAW16 added. */
    public static List<Size> rawSizes(List<Size> sizes, Object caps, int format, int session,
            String characteristicsField, String cameraId) {
        if (format != RAW16 || !contains(SESSIONS, session)) return sizes;
        try {
            if (!lensCamera(caps, cameraId)) return sizes;
            Size full = fullRaw(characteristics(caps, characteristicsField));
            if (full == null || (sizes != null && sizes.contains(full))) return sizes;
            List<Size> out = new ArrayList<>();
            out.add(full);
            if (sizes != null) out.addAll(sizes);
            Log.once("ultra raw: 50MP RAW offered on the ultra-wide and telephoto");
            return out;
        } catch (Throwable t) {
            Log.once("ultra raw: sizes", t);
            return sizes;
        }
    }

    /** After Pro's RAW size: the full sensor on the two cameras while 50MP is on. */
    public static Size proRawSize(Size size, Object caps, String characteristicsField, String cameraId,
            String ultra) {
        try {
            String[] u = ultra.split("#");
            if (!Boolean.TRUE.equals(UltraHdLenses.method(u[0], u[1]).invoke(null))) return size;
            if (!lensCamera(caps, cameraId)) return size;
            Size full = fullRaw(characteristics(caps, characteristicsField));
            return full != null ? full : size;
        } catch (Throwable t) {
            Log.once("ultra raw: Pro RAW size", t);
            return size;
        }
    }

    static boolean lensCamera(Object caps, String cameraId) throws Exception {
        String[] n = cameraId.split("#");
        Object id = UltraHdLenses.method(n[0], n[1]).invoke(null, caps);
        return id instanceof Integer && contains(CAMERAS, (Integer) id);
    }

    static CameraCharacteristics characteristics(Object caps, String field) throws Exception {
        Field f = caps.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (CameraCharacteristics) f.get(caps);
    }

    /** The largest RAW16 size in the HAL's Xiaomi stream list, or null. */
    static Size fullRaw(CameraCharacteristics c) {
        int[] streams;
        try {
            streams = c == null ? null : c.get(XIAOMI_STREAMS);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (streams == null) return null;
        Size best = null;
        for (int i = 0; i + 3 < streams.length; i += 4) {
            if (streams[i] != RAW16) continue;
            Size s = new Size(streams[i + 1], streams[i + 2]);
            if (best == null || (long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) best = s;
        }
        return best;
    }

    static boolean contains(int[] values, int v) {
        for (int x : values) if (x == v) return true;
        return false;
    }
}
