package dev.spundone.xcam17;

import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 50MP RAW on the ultra-wide and telephoto in Pro. Pro offers RAW at 50MP on a lens when the
 * lens's 50MP size is among the RAW16 sizes of the 50MP session ("isUltraPixelRawPhotography-
 * Supported"), and takes the RAW stream size from the same list. Those lists come from the HAL's
 * per-session stream maps, which give the main camera 8192x6144 but leave RAW16 out for the
 * ultra-wide and telephoto, though both list 8160x6144 RAW16 in xiaomi.scaler.
 * availableStreamConfigurations. For those two cameras that size is added to the RAW16 list of
 * the 50MP session modes, so RAW stays on at 50MP and the RAW stream is the full sensor.
 */
final class UltraHdRaw {
    static final int RAW16 = 32;
    /**
     * Session modes: the one RAW support is checked against, and Pro's 50MP sessions (the main
     * camera takes its 8192x6144 RAW in 0x9007, the JPEG one), RAW-only and JPEG + RAW.
     */
    static final int[] SESSIONS = {0x80f3, 0x9007, 0x900c, 0x900e};
    static final int[] CAMERAS = {3, 4};
    static final CameraCharacteristics.Key<int[]> XIAOMI_STREAMS =
            new CameraCharacteristics.Key<>("xiaomi.scaler.availableStreamConfigurations", int[].class);

    private UltraHdRaw() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.rawSizesMethod == null) return;
        try {
            Class<?> caps = cl.loadClass(build.capsClass);
            Method sizes = caps.getDeclaredMethod(build.rawSizesMethod, int.class, int.class);
            Method cameraId = cl.loadClass(build.cameraIdClass).getDeclaredMethod(build.cameraIdMethod, caps);
            Field characteristics = caps.getDeclaredField(build.capsCharacteristicsField);
            if (sizes.getReturnType() != List.class || cameraId.getReturnType() != int.class
                    || characteristics.getType() != CameraCharacteristics.class) {
                Entry.log("ultra raw: unexpected shape, skipping");
                return;
            }
            cameraId.setAccessible(true);
            characteristics.setAccessible(true);
            XposedBridge.hookMethod(sizes, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if ((Integer) p.args[0] != RAW16 || !contains(SESSIONS, (Integer) p.args[1])) return;
                    if (!contains(CAMERAS, (Integer) cameraId.invoke(null, p.thisObject))) return;
                    Size full = fullRaw((CameraCharacteristics) characteristics.get(p.thisObject));
                    if (full == null) return;
                    @SuppressWarnings("unchecked")
                    List<Size> have = (List<Size>) p.getResult();
                    if (have != null && have.contains(full)) return;
                    List<Size> out = new ArrayList<>();
                    out.add(full);
                    if (have != null) out.addAll(have);
                    p.setResult(out);
                }
            });
            hookProRawSize(cl, build, caps, cameraId, characteristics);
            Entry.log("ultra raw: 50MP RAW offered on the ultra-wide and telephoto");
        } catch (Throwable t) {
            Entry.log("ultra raw: not hooked: " + t);
        }
    }

    /**
     * Pro asks the capabilities for its RAW size first (the first RAW16 entry of a Xiaomi list),
     * which on the telephoto is a 3248x2448 crop; that stream then never delivers a frame in the
     * 50MP session. While 50MP is on, the full sensor size is answered for the two cameras.
     */
    static void hookProRawSize(ClassLoader cl, CameraBuild build, Class<?> caps, Method cameraId, Field characteristics)
            throws Exception {
        if (build.proRawSizeMethod == null) return;
        Method rawSize = caps.getDeclaredMethod(build.proRawSizeMethod);
        Method ultraOn = cl.loadClass(build.ultraOnClass).getDeclaredMethod(build.ultraOnMethod);
        if (rawSize.getReturnType() != Size.class || ultraOn.getReturnType() != boolean.class) {
            Entry.log("ultra raw: Pro RAW size has an unexpected shape, skipping");
            return;
        }
        ultraOn.setAccessible(true);
        XposedBridge.hookMethod(rawSize, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if (!Boolean.TRUE.equals(ultraOn.invoke(null))) return;
                if (!contains(CAMERAS, (Integer) cameraId.invoke(null, p.thisObject))) return;
                Size full = fullRaw((CameraCharacteristics) characteristics.get(p.thisObject));
                if (full != null) p.setResult(full);
            }
        });
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
