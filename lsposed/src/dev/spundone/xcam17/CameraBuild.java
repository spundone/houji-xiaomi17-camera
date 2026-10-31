package dev.spundone.xcam17;

/**
 * Obfuscated names differ between camera builds, and the same short name can point at
 * unrelated code in another build. Each supported build gets its own table, and the build
 * is identified by the one class every table starts from: the consent-region enum, which
 * must hold CN/KR/GLOBAL constants. An unknown build gets no name-based hooks at all.
 */
final class CameraBuild {
    final String label;
    /** Consent region class; its "$c" inner enum holds CN, KR, GLOBAL. */
    final String consent;
    /** Multi-lens video check, and whether it takes a trailing HDR boolean. */
    final String satCheckClass;
    final boolean satCheckHasHdrArg;
    /** HDR10, HDR10 Pro and Dolby ("True Colour") components. */
    final String hdr10;
    final String hdr10Pro;
    final String dolby;
    /** Static helper (type, quality, fps, caps) over the HAL's Dolby config, and caps class. */
    final String dolbyConfigClass;
    final String dolbyConfigMethod;
    final String capabilitiesClass;
    /** Device profile classes (Common first) and the flags {@link DeviceFeatures} sets on them. */
    final String[] profiles;
    final DeviceFeatures.Flag[] features;
    /**
     * Device-profile loader and its static "no profile for this phone" flag, or null. Builds
     * without a houji profile set the flag and close the camera as the wrong APK version.
     */
    final String profileLoaderClass;
    final String profileLoaderMethod;
    final String profileMissingField;
    /**
     * Profile to use instead of the generic one, or null: its readable name, and the name as
     * stored in the APK (XOR-scrambled with a key per class, so it can't be derived).
     */
    final String profileFallback;
    final String profileFallbackStored;
    /** Profile method returning the {brand, model} name table that watermarks print, or null. */
    final String profileNamesMethod;
    /**
     * For {@link FillLight60}, or null: the camera config class and its fps-range setters,
     * the static accessor of the settings components, and the flash component class.
     */
    final String fpsRangeClass;
    final String[] fpsRangeMethods;
    final String componentsClass;
    final String componentsMethod;
    final String flashComponentClass;
    /** Capabilities method reading one bit of the HAL style mask, for {@link NaturalStyle}, or null. */
    final String styleMaskMethod;

    private CameraBuild(String label, String consent, String satCheckClass, boolean satCheckHasHdrArg,
            String hdr10, String hdr10Pro, String dolby, String dolbyConfigClass,
            String dolbyConfigMethod, String capabilitiesClass,
            String[] profiles, DeviceFeatures.Flag[] features,
            String profileLoaderClass, String profileLoaderMethod, String profileMissingField,
            String profileFallback, String profileFallbackStored, String profileNamesMethod,
            String fpsRangeClass, String[] fpsRangeMethods, String componentsClass, String componentsMethod,
            String flashComponentClass, String styleMaskMethod) {
        this.label = label;
        this.consent = consent;
        this.satCheckClass = satCheckClass;
        this.satCheckHasHdrArg = satCheckHasHdrArg;
        this.hdr10 = hdr10;
        this.hdr10Pro = hdr10Pro;
        this.dolby = dolby;
        this.dolbyConfigClass = dolbyConfigClass;
        this.dolbyConfigMethod = dolbyConfigMethod;
        this.capabilitiesClass = capabilitiesClass;
        this.profiles = profiles;
        this.features = features;
        this.profileLoaderClass = profileLoaderClass;
        this.profileLoaderMethod = profileLoaderMethod;
        this.profileMissingField = profileMissingField;
        this.profileFallback = profileFallback;
        this.profileFallbackStored = profileFallbackStored;
        this.profileNamesMethod = profileNamesMethod;
        this.fpsRangeClass = fpsRangeClass;
        this.fpsRangeMethods = fpsRangeMethods;
        this.componentsClass = componentsClass;
        this.componentsMethod = componentsMethod;
        this.flashComponentClass = flashComponentClass;
        this.styleMaskMethod = styleMaskMethod;
    }

    static final CameraBuild[] KNOWN = {
            new CameraBuild("6.7.000280.0 (Xiaomi 17)", "ei.c", "com.android.camera.data.data.j", true,
                    "t2.a", "t2.b", "t2.c", "n9.e", "h1", "n9.d",
                    DeviceFeatures.PROFILES_67, DeviceFeatures.FLAGS_67, null, null, null, null, null, null,
                    null, null, null, null, null, null),
            new CameraBuild("6.8.002090.2 (Xiaomi 18 Pro)", "Th.c", "com.android.camera.data.data.i", false,
                    "s2.a", "s2.b", "s2.c", "l9.h", "g1", "l9.g",
                    DeviceFeatures.PROFILES_68, DeviceFeatures.FLAGS_68, "Eu.f2", "l", "a",
                    "com.mi.device.xiaomi.CommonFlagship",
                    "\u6671\u667d\u667f\u663c\u667f\u667b\u663c\u6676\u6677\u6664\u667b\u6671\u6677\u663c"
                            + "\u666a\u667b\u6673\u667d\u667f\u667b\u663c\u6651\u667d\u667f\u667f\u667d\u667c"
                            + "\u6654\u667e\u6673\u6675\u6661\u667a\u667b\u6662",
                    "d",
                    "l9.i0", new String[] {"L", "f0"}, "g2.a", "a", "r2.w", "J0"),
    };

    static CameraBuild detect(ClassLoader cl) {
        for (CameraBuild b : KNOWN) {
            try {
                Object global = cl.loadClass(b.consent + "$c").getField("c").get(null);
                if (global instanceof Enum && "GLOBAL".equals(((Enum<?>) global).name())) return b;
            } catch (Throwable ignored) {
                // Not this build.
            }
        }
        return null;
    }
}
