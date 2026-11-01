package dev.spundone.xcam17;

import android.hardware.camera2.CameraCharacteristics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Open Gate (recording the whole 4:3 sensor, 4096x3072) is a ratio option in Pro video, offered
 * only when the HAL declares com.xiaomi.camera.supportedfeatures.videoOpenGate. houji's HAL
 * doesn't, but its rear cameras do stream 4096x3072 (the binned full sensor) at 30 fps in
 * PRIVATE, YUV and P010, so the check is answered yes for rear cameras. The front camera has no
 * such stream and keeps the HAL's answer.
 */
final class OpenGate {
    private OpenGate() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.openGateClass == null) return;
        try {
            Class<?> caps = cl.loadClass(build.capsClass);
            Field characteristics = caps.getDeclaredField(build.capsCharacteristicsField);
            characteristics.setAccessible(true);
            Method check = cl.loadClass(build.openGateClass).getDeclaredMethod(build.openGateMethod, caps);
            XposedBridge.hookMethod(check, new RearOnly(characteristics));
            Entry.log("open gate: offered in Pro video on the rear cameras");
        } catch (Throwable t) {
            Entry.log("open gate: not hooked: " + t);
        }
    }

    static final class RearOnly extends XC_MethodHook {
        private final Field characteristics;

        RearOnly(Field characteristics) {
            this.characteristics = characteristics;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            if (Boolean.TRUE.equals(p.getResult()) || p.args[0] == null) return;
            Object c = characteristics.get(p.args[0]);
            if (!(c instanceof CameraCharacteristics)) return;
            Integer facing = ((CameraCharacteristics) c).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) p.setResult(Boolean.TRUE);
        }
    }
}
