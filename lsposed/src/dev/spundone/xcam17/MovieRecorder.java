package dev.spundone.xcam17;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Movie mode (CinematicModule, mode 227) records through Xiaomi's MiCinematicRecorder, whose
 * audio side needs android.media.AudioParaManger$TuneListener, a HyperOS framework class
 * houji's ROM doesn't have. Creating the recorder throws NoClassDefFoundError, the camera
 * swallows it, and the preview never starts. While the recorder controller picks a recorder,
 * this answers "not Movie mode" so it builds the regular MiMediaCodecRecorder used by Video.
 */
final class MovieRecorder {
    private static final ThreadLocal<Boolean> CHOOSING = new ThreadLocal<>();

    private MovieRecorder() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.recorderControllerClass == null) return;
        try {
            XposedBridge.hookMethod(cl.loadClass(build.recorderControllerClass)
                    .getDeclaredMethod(build.recorderCreateMethod), new Choosing());
            XposedBridge.hookMethod(cl.loadClass(build.modeCheckClass)
                    .getDeclaredMethod(build.movieModeMethod), new NotMovieWhileChoosing());
            Entry.log("movie: records with the regular video recorder");
        } catch (Throwable t) {
            Entry.log("movie: not hooked: " + t);
        }
    }

    static final class Choosing extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            CHOOSING.set(Boolean.TRUE);
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            CHOOSING.remove();
        }
    }

    static final class NotMovieWhileChoosing extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            if (Boolean.TRUE.equals(CHOOSING.get())) p.setResult(Boolean.FALSE);
        }
    }
}
