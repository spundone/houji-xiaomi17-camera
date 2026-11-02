package dev.spundone.xcam17.aosp;

import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Port of Entry.hookMissingProfile and hookMarketingName.
 *
 * 6.8 has no device profile for houji. The loader then falls back to the generic Common
 * profile and sets a static flag that Camera.onCreate treats as "wrong APK version" (an error,
 * then the activity finishes). After the loader runs, this clears that flag and swaps in the
 * CommonFlagship profile, whose modes, lenses and video sizes match a flagship.
 */
public final class Profile {
    private Profile() {}

    /**
     * After the profile loader: loader is its class, missingField its static Boolean
     * "no profile for this phone" flag, cacheField the static field holding the loaded
     * profile, fallback the profile class to use instead.
     */
    public static Object afterLoad(Object profile, Class<?> loader, String missingField, String cacheField,
            Class<?> fallback) {
        try {
            Field missing = loader.getDeclaredField(missingField);
            missing.setAccessible(true);
            if (!Boolean.TRUE.equals(missing.get(null))) return profile;
            missing.set(null, Boolean.FALSE);
            Object replacement = fallback.getDeclaredConstructor().newInstance();
            Field cache = loader.getDeclaredField(cacheField);
            if (!Modifier.isStatic(cache.getModifiers()) || !cache.getType().isInstance(replacement)) {
                Log.once("profile: " + fallback.getName() + " doesn't fit " + cacheField);
                return profile;
            }
            cache.setAccessible(true);
            cache.set(null, replacement);
            Log.once("profile: missing-profile error suppressed, using " + fallback.getName());
            return replacement;
        } catch (Throwable t) {
            Log.once("profile: not replaced", t);
            return profile;
        }
    }

    /**
     * Watermarks print the phone's name from the profile's {brand, model} table; CommonFlagship
     * has {XIAOMI, MI PHONE}. This answers with the market name ("Xiaomi 14" is {XIAOMI, 14}).
     */
    public static SparseArray<String[]> marketingName() {
        String market = null;
        try {
            market = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, "ro.product.marketname");
        } catch (Throwable ignored) {
            // Fall back to the model below.
        }
        if (market == null || market.trim().isEmpty()) market = android.os.Build.MODEL;
        String[] parts = market.trim().split("\\s+", 2);
        String[] pair = parts.length == 2 && parts[0].equalsIgnoreCase("xiaomi")
                ? new String[] {"XIAOMI", parts[1]}
                : new String[] {"XIAOMI", market.trim()};
        SparseArray<String[]> names = new SparseArray<>();
        names.put(0, pair);
        return names;
    }
}
