package dev.spundone.xcam17.aosp;

/**
 * Port of TeleFallback's default. Adaptive telephoto (the HAL showing a main-camera crop instead
 * of the telephoto in dim or close scenes) defaults to off; the setting still turns it on.
 */
public final class Settings {
    static final String TELE_FALLBACK_KEY = "pref_camera_tele_fallback_key";

    private Settings() {}

    /** The settings store's getBoolean(key, default): the default argument for that key. */
    public static boolean booleanDefault(String key, boolean def) {
        return TELE_FALLBACK_KEY.equals(key) ? false : def;
    }
}
