package dev.spundone.xcam17;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * With a photo watermark on and the format set to HEIF, the camera saves the watermarked
 * HEIF with "watermark remover" metadata: the part of the photo the watermark covers, so the
 * Gallery can restore it. Frame watermarks (a band below the photo, such as the Leica ones)
 * cover nothing, so there is no sub-image, yet the remover info is still set, and the HEIF
 * path then wraps the missing sub-image and builds its metadata from nothing: the saver thread
 * crashes and the photo is lost. The JPEG path ends up without remover info in this case
 * anyway, so the remover info is dropped whenever there is no sub-image, and HEIF takes its
 * working "no remover info" path. Watermarks that cover part of the photo are unchanged.
 */
final class WatermarkSubimage {
    private WatermarkSubimage() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.wmDrawClass == null) return;
        try {
            Class<?> job = cl.loadClass(build.wmJobClass);
            Field remover = job.getDeclaredField(build.wmRemoverField);
            Field subimage = job.getDeclaredField(build.wmSubimageField);
            remover.setAccessible(true);
            subimage.setAccessible(true);
            int hooked = 0;
            for (Method m : cl.loadClass(build.wmDrawClass).getDeclaredMethods()) {
                Class<?>[] args = m.getParameterTypes();
                if (m.getName().equals(build.wmDrawMethod) && args.length > 0 && args[0] == job) {
                    XposedBridge.hookMethod(m, new NoRemoverWithoutSubimage(remover, subimage));
                    hooked++;
                }
            }
            if (hooked == 0) throw new NoSuchMethodException(build.wmDrawClass + "." + build.wmDrawMethod);
            Entry.log("watermark: frame watermarks saved in HEIF");
        } catch (Throwable t) {
            Entry.log("watermark: not hooked: " + t);
        }
    }

    static final class NoRemoverWithoutSubimage extends XC_MethodHook {
        private final Field remover;
        private final Field subimage;

        NoRemoverWithoutSubimage(Field remover, Field subimage) {
            this.remover = remover;
            this.subimage = subimage;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) throws Throwable {
            Object job = p.args[0];
            if (job != null && subimage.get(job) == null) remover.set(job, null);
        }
    }
}
