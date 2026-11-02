package dev.spundone.xcam17.aosp;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.util.Size;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Port of Dng50: RAW frames of other sizes than the declared sensor (50MP, the telephoto's
 * 3248x2448 crop) saved as DNG instead of an empty file. The camera's calls to DngCreator are
 * redirected here: the constructor's arguments and the orientation are noted, and a frame
 * DngCreator would reject is written by one whose camera description has the frame's size.
 * See the LSPosed hook.
 */
public final class Dng50 {
    static final class Recorded {
        CameraCharacteristics characteristics;
        CaptureResult result;
        Integer orientation;
    }

    static final Map<DngCreator, Recorded> RECORDED = Collections.synchronizedMap(new WeakHashMap<>());

    private Dng50() {}

    /** After new DngCreator(characteristics, result). */
    public static void created(DngCreator creator, CameraCharacteristics characteristics, CaptureResult result) {
        Recorded r = new Recorded();
        r.characteristics = characteristics;
        r.result = result;
        RECORDED.put(creator, r);
    }

    public static DngCreator setOrientation(DngCreator creator, int orientation) {
        Recorded r = RECORDED.get(creator);
        if (r != null) r.orientation = orientation;
        return creator.setOrientation(orientation);
    }

    public static void writeInputStream(DngCreator creator, OutputStream out, Size size, InputStream in, long offset)
            throws IOException {
        Recorded r = RECORDED.get(creator);
        if (r == null || size == null || r.characteristics == null || matches(r.characteristics, size)) {
            creator.writeInputStream(out, size, in, offset);
            return;
        }
        DngCreator sized;
        try {
            sized = new DngCreator(sized(r.characteristics, size), r.result);
        } catch (Throwable t) {
            Log.once("dng: no creator for " + size, t);
            creator.writeInputStream(out, size, in, offset);
            return;
        }
        try {
            if (r.orientation != null) sized.setOrientation(r.orientation);
            sized.writeInputStream(out, size, in, offset);
            Log.once("dng: " + size + " RAW saved");
        } finally {
            sized.close();
        }
    }

    /** The frame has the size DngCreator accepts: the pixel array or the pre-correction active area. */
    static boolean matches(CameraCharacteristics c, Size size) {
        Size array = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Rect pre = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
        return size.equals(array) || (pre != null && pre.width() == size.getWidth() && pre.height() == size.getHeight());
    }

    /** A copy of the characteristics whose pixel array and active areas are the frame's size. */
    static CameraCharacteristics sized(CameraCharacteristics c, Size size) throws Exception {
        Method nativeCopy = CameraCharacteristics.class.getDeclaredMethod("getNativeCopy");
        nativeCopy.setAccessible(true);
        Object metadata = nativeCopy.invoke(c);
        Method set = metadata.getClass().getMethod("set", CameraCharacteristics.Key.class, Object.class);
        set.invoke(metadata, CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, size);
        Rect whole = new Rect(0, 0, size.getWidth(), size.getHeight());
        set.invoke(metadata, CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE, whole);
        set.invoke(metadata, CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE, whole);
        Constructor<CameraCharacteristics> make =
                CameraCharacteristics.class.getDeclaredConstructor(metadata.getClass());
        make.setAccessible(true);
        return make.newInstance(metadata);
    }
}
