package dev.spundone.xcam17;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.BaseDexClassLoader;

/**
 * Makes the module's android.media.AudioParaManger stand-in visible to the camera. The
 * camera's Movie recorder (MiCinematicRecorder) refers to HyperOS's AudioParaManger and its
 * listener interfaces; on AOSP the camera's class loader can't find them, so Movie's recorder
 * failed to load. ART resolves classes for the app's own loader natively, so the module's APK
 * is added to that loader's dex path: the camera then finds the stand-in like its own classes.
 */
final class AudioParaStub {
    static final String MODULE = "dev.spundone.xcam17";
    static final Pattern MODULE_PATH = Pattern.compile("module=([^,\\]]+\\.apk)");

    private AudioParaStub() {}

    static void hook(ClassLoader camera) {
        try {
            if (!(camera instanceof BaseDexClassLoader)) {
                Entry.log("movie: camera class loader is " + camera + ", audio stand-in skipped");
                return;
            }
            String apk = modulePath();
            if (apk == null) {
                Entry.log("movie: module APK path not found, audio stand-in skipped");
                return;
            }
            Method add = BaseDexClassLoader.class.getDeclaredMethod("addDexPath", String.class);
            add.setAccessible(true);
            add.invoke(camera, apk);
            Entry.log("movie: HyperOS audio parameter manager stand-in available");
        } catch (Throwable t) {
            Entry.log("movie: audio stand-in not added: " + t);
        }
    }

    /** The module's installed APK: from the loader that loaded it, else from the package manager. */
    static String modulePath() throws Exception {
        Matcher m = MODULE_PATH.matcher(String.valueOf(AudioParaStub.class.getClassLoader()));
        if (m.find()) return m.group(1);
        Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        if (app == null) return null;
        ApplicationInfo info = ((Context) app).getPackageManager().getApplicationInfo(MODULE, 0);
        return info.sourceDir;
    }
}
