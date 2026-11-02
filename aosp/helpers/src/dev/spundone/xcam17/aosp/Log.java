package dev.spundone.xcam17.aosp;

import java.util.HashSet;
import java.util.Set;

/** Logs each message once under the tag xcam17, so a helper on a hot path stays quiet. */
final class Log {
    private static final Set<String> SEEN = new HashSet<>();

    private Log() {}

    static void once(String msg) {
        synchronized (SEEN) {
            if (!SEEN.add(msg)) return;
        }
        android.util.Log.i("xcam17", msg);
    }

    static void once(String msg, Throwable t) {
        once(msg + ": " + t);
    }
}
