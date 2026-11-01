package dev.spundone.xcam17;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.util.Size;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * RAW photos whose size isn't the declared sensor size, saved as DNG. houji's HAL declares only
 * the binned sensor size (4096x3072 main, 4080x3072 ultra-wide and telephoto) and no
 * maximum-resolution sizes, so DngCreator rejects a 50MP frame, and the telephoto's 12.5MP RAW
 * (a 3248x2448 crop for the 3.2x stop) too ("Height and width of image buffer did not match"),
 * and the camera saved an empty .dng. Such a frame is written by a DngCreator whose camera
 * description has the frame's size as pixel array and active areas, after replaying the
 * settings the camera made on its own creator.
 */
final class Dng50 {
    /** Constructor arguments and setter calls on each creator, to rebuild it for a 50MP frame. */
    static final Map<DngCreator, Recorded> RECORDED = Collections.synchronizedMap(new WeakHashMap<>());

    static final class Recorded {
        CameraCharacteristics characteristics;
        CaptureResult result;
        final List<Object[]> calls = new ArrayList<>();
    }

    private Dng50() {}

    static void hook() {
        try {
            XposedBridge.hookMethod(DngCreator.class.getConstructor(CameraCharacteristics.class, CaptureResult.class),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            Recorded r = new Recorded();
                            r.characteristics = (CameraCharacteristics) p.args[0];
                            r.result = (CaptureResult) p.args[1];
                            RECORDED.put((DngCreator) p.thisObject, r);
                        }
                    });
            XC_MethodHook record = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    Recorded r = RECORDED.get(p.thisObject);
                    if (r != null) r.calls.add(new Object[] {p.method, p.args.clone()});
                }
            };
            for (Method m : DngCreator.class.getDeclaredMethods()) {
                if (m.getName().startsWith("set") && m.getReturnType() == DngCreator.class) {
                    XposedBridge.hookMethod(m, record);
                }
            }
            XposedBridge.hookMethod(DngCreator.class.getMethod("writeInputStream",
                    OutputStream.class, Size.class, InputStream.class, long.class), new FullSize());
            Entry.log("dng: RAW frames of other sizes than the declared sensor (50MP, telephoto crop) saved");
        } catch (Throwable t) {
            Entry.log("dng: not hooked: " + t);
        }
    }

    static final class FullSize extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
            Recorded r = RECORDED.get(p.thisObject);
            Size size = (Size) p.args[1];
            if (r == null || size == null) return;
            if (matches(r.characteristics, size)) return;
            CameraCharacteristics full = sized(r.characteristics, size);
            DngCreator creator = new DngCreator(full, r.result);
            try {
                for (Object[] call : r.calls) ((Method) call[0]).invoke(creator, (Object[]) call[1]);
                creator.writeInputStream((OutputStream) p.args[0], size, (InputStream) p.args[2], (Long) p.args[3]);
            } finally {
                creator.close();
            }
            p.setResult(null);
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
