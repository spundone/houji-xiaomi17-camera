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
    /** Feature mutex manager, its find method, event class/id field and rule class/target field, for {@link MutexDrop}. */
    final String mutexManagerClass;
    final String mutexFindMethod;
    final String mutexEventClass;
    final String mutexEventIdField;
    final String mutexRuleClass;
    final String mutexRuleTargetField;
    /** Style component and its (mode, disabled) setter, kept enabled by {@link MutexDrop}. */
    final String styleDisableClass;
    final String styleDisableMethod;
    /**
     * Watermark drawing helper and its method returning the drawn image, and the watermark job
     * class with its remover-info and covered sub-image fields, for {@link WatermarkSubimage}.
     */
    final String wmDrawClass;
    final String wmDrawMethod;
    final String wmJobClass;
    final String wmRemoverField;
    final String wmSubimageField;
    /**
     * Zoom toggle state builder (mode, ...) and its state field, the static "Ultra HD is on"
     * check, and the per-mode zoom ratio list, for {@link UltraHdLenses}.
     */
    final String zoomStateClass;
    final String zoomStateMethod;
    final String zoomStateField;
    final String ultraOnClass;
    final String ultraOnMethod;
    final String zoomListClass;
    final String zoomListMethod;
    /** In the "Ultra HD is on" class: the per-mode check (supported and switched on). */
    final String ultraOnModeMethod;
    /** Ratio component, the ratio-change handler and the static "turn Ultra HD off", for {@link UltraHdLenses}. */
    final String ratioClass;
    final String ratioChangeClass;
    final String ratioChangeMethod;
    final String ultraOffMethod;
    /**
     * Zoom manager and its lens switch (old zoom, new zoom, module); the request builder's zoom step (builder, caps, config),
     * the caps' static camera id getter and the config's zoom field, for {@link UltraHdLenses}.
     */
    final String zoomManagerClass;
    final String lensSwitchMethod;
    final String zoomApplyClass;
    final String zoomApplyMethod;
    final String cameraIdClass;
    final String cameraIdMethod;
    final String zoomField;
    /**
     * In the zoom manager: its module reference, current zoom field and zoom setter; Photo's
     * zoom manager and its snap to the nearest stop, and its parent's end-of-slide handler.
     */
    final String zoomModuleField;
    final String zoomValueField;
    final String zoomSetMethod;
    final String snapClass;
    final String snapMethod;
    final String slideEndClass;
    final String slideEndMethod;
    /** Image saver and its new-image callback (uri, ?, title, mime type, is preview), for {@link UltraHdrJpeg}. */
    final String newImageClass;
    final String newImageMethod;
    /** Master (video) filter component, for {@link VideoFilterLuts}. */
    final String filterComponentClass;
    /** Camera capabilities class and its CameraCharacteristics field, and the Open Gate check, for {@link OpenGate}. */
    final String capsClass;
    final String capsCharacteristicsField;
    final String openGateClass;
    final String openGateMethod;
    /** Vibrator context, its instance accessor, and its strategy interface and field, for {@link CameraHaptics}. */
    final String vibratorContextClass;
    final String vibratorContextInstance;
    final String hapticStrategyClass;
    final String hapticStrategyField;
    /** Fastshot focus component, its depth-of-field table loader and table field, for {@link StreetFocus}. */
    final String streetFocusClass;
    final String streetDofLoadMethod;
    final String streetDofField;
    /** Profile table methods (Pro exposure ranges, zoom toggles, Fastshot presets) and the preset class, for {@link ProfileTables}. */
    final String exposureRangeMethod;
    final String zoomTogglesMethod;
    final String streetPresetsMethod;
    final String focalPresetClass;
    /**
     * The video quality component's quality check (limits, list, mode, caps, ?), and the static
     * capability checks class with its all-scenes Dolby check. For {@link VideoCaps}.
     */
    final String qualityCheckClass;
    /** The camera's settings store and its boolean getter (key, default), for {@link TeleFallback}. */
    final String settingsClass;
    final String settingsBooleanMethod;
    /** In the camera capabilities class: the "is this vendor tag available" check (tag name). */
    final String capsTagCheckMethod;
    /** In the static capability checks class: "telephoto fallback control supported" (caps), or null. */
    final String teleSupportedMethod;
    /**
     * For {@link NaturalStyle}, or null: the style component's list builder (data), the data's
     * mode field, and the static "Original style supported" check (caps) in the checks class.
     */
    final String styleListClass;
    final String styleListMethod;
    final String styleListModeField;
    final String originalStyleMethod;
    final String qualityCheckMethod;
    final String capsChecksClass;
    final String allScenesDolbyMethod;
    /**
     * For {@link LeicaLegendary}, or null: the save step (task), the photo task class and its
     * image holder, info, Exif, result holder and total-result members; the image holder's image
     * field, the info's module field, the result holder's result field; the image interface's
     * bytes getter and the image factory; the Legendary look component; the camera manager
     * interface's preview state getter and the state's latest-result getter.
     */
    final String[] leica;

    private CameraBuild(String label, String consent, String satCheckClass, boolean satCheckHasHdrArg,
            String hdr10, String hdr10Pro, String dolby, String dolbyConfigClass,
            String dolbyConfigMethod, String capabilitiesClass,
            String[] profiles, DeviceFeatures.Flag[] features,
            String profileLoaderClass, String profileLoaderMethod, String profileMissingField,
            String profileFallback, String profileFallbackStored, String profileNamesMethod,
            String fpsRangeClass, String[] fpsRangeMethods, String componentsClass, String componentsMethod,
            String flashComponentClass, String styleMaskMethod, String[] mutex, String[] watermark, String[] lenses, String[] newImage, String filterComponent, String[] openGate, String[] haptics, String[] streetFocus, String[] tables, String[] videoCaps, String[] settings, String[] leica, String[] portraitStyle) {
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
        this.mutexManagerClass = mutex == null ? null : mutex[0];
        this.mutexFindMethod = mutex == null ? null : mutex[1];
        this.mutexEventClass = mutex == null ? null : mutex[2];
        this.mutexEventIdField = mutex == null ? null : mutex[3];
        this.mutexRuleClass = mutex == null ? null : mutex[4];
        this.mutexRuleTargetField = mutex == null ? null : mutex[5];
        this.styleDisableClass = mutex == null ? null : mutex[6];
        this.styleDisableMethod = mutex == null ? null : mutex[7];
        this.wmDrawClass = watermark == null ? null : watermark[0];
        this.wmDrawMethod = watermark == null ? null : watermark[1];
        this.wmJobClass = watermark == null ? null : watermark[2];
        this.wmRemoverField = watermark == null ? null : watermark[3];
        this.wmSubimageField = watermark == null ? null : watermark[4];
        this.zoomStateClass = lenses == null ? null : lenses[0];
        this.zoomStateMethod = lenses == null ? null : lenses[1];
        this.zoomStateField = lenses == null ? null : lenses[2];
        this.ultraOnClass = lenses == null ? null : lenses[3];
        this.ultraOnMethod = lenses == null ? null : lenses[4];
        this.zoomListClass = lenses == null ? null : lenses[5];
        this.zoomListMethod = lenses == null ? null : lenses[6];
        this.ultraOnModeMethod = lenses == null ? null : lenses[11];
        this.ratioClass = lenses == null ? null : lenses[7];
        this.ratioChangeClass = lenses == null ? null : lenses[8];
        this.ratioChangeMethod = lenses == null ? null : lenses[9];
        this.ultraOffMethod = lenses == null ? null : lenses[10];
        boolean zoom = lenses != null && lenses.length > 12;
        this.zoomManagerClass = zoom ? lenses[12] : null;
        this.lensSwitchMethod = zoom ? lenses[13] : null;
        this.zoomApplyClass = zoom ? lenses[14] : null;
        this.zoomApplyMethod = zoom ? lenses[15] : null;
        this.cameraIdClass = zoom ? lenses[16] : null;
        this.cameraIdMethod = zoom ? lenses[17] : null;
        this.zoomField = zoom ? lenses[18] : null;
        boolean snap = lenses != null && lenses.length > 19;
        this.zoomModuleField = snap ? lenses[19] : null;
        this.zoomValueField = snap ? lenses[20] : null;
        this.zoomSetMethod = snap ? lenses[21] : null;
        this.snapClass = snap ? lenses[22] : null;
        this.snapMethod = snap ? lenses[23] : null;
        this.slideEndClass = snap ? lenses[24] : null;
        this.slideEndMethod = snap ? lenses[25] : null;
        this.newImageClass = newImage == null ? null : newImage[0];
        this.newImageMethod = newImage == null ? null : newImage[1];
        this.filterComponentClass = filterComponent;
        this.capsClass = openGate == null ? null : openGate[0];
        this.capsCharacteristicsField = openGate == null ? null : openGate[1];
        this.openGateClass = openGate == null ? null : openGate[2];
        this.openGateMethod = openGate == null ? null : openGate[3];
        this.vibratorContextClass = haptics == null ? null : haptics[0];
        this.vibratorContextInstance = haptics == null ? null : haptics[1];
        this.hapticStrategyClass = haptics == null ? null : haptics[2];
        this.hapticStrategyField = haptics == null ? null : haptics[3];
        this.streetFocusClass = streetFocus == null ? null : streetFocus[0];
        this.streetDofLoadMethod = streetFocus == null ? null : streetFocus[1];
        this.streetDofField = streetFocus == null ? null : streetFocus[2];
        this.exposureRangeMethod = tables == null ? null : tables[0];
        this.zoomTogglesMethod = tables == null ? null : tables[1];
        this.streetPresetsMethod = tables == null ? null : tables[2];
        this.focalPresetClass = tables == null ? null : tables[3];
        this.settingsClass = settings == null ? null : settings[0];
        this.settingsBooleanMethod = settings == null ? null : settings[1];
        this.capsTagCheckMethod = settings == null ? null : settings[2];
        this.teleSupportedMethod = settings == null || settings.length < 4 ? null : settings[3];
        this.styleListClass = portraitStyle == null ? null : portraitStyle[0];
        this.styleListMethod = portraitStyle == null ? null : portraitStyle[1];
        this.styleListModeField = portraitStyle == null ? null : portraitStyle[2];
        this.originalStyleMethod = portraitStyle == null ? null : portraitStyle[3];
        this.qualityCheckClass = videoCaps == null ? null : videoCaps[0];
        this.qualityCheckMethod = videoCaps == null ? null : videoCaps[1];
        this.capsChecksClass = videoCaps == null ? null : videoCaps[2];
        this.allScenesDolbyMethod = videoCaps == null ? null : videoCaps[3];
        this.leica = leica;
    }

    static final CameraBuild[] KNOWN = {
            new CameraBuild("6.7.000280.0 (Xiaomi 17)", "ei.c", "com.android.camera.data.data.j", true,
                    "t2.a", "t2.b", "t2.c", "n9.e", "h1", "n9.d",
                    DeviceFeatures.PROFILES_67, DeviceFeatures.FLAGS_67, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
            new CameraBuild("6.8.002090.2 (Xiaomi 18 Pro)", "Th.c", "com.android.camera.data.data.i", false,
                    "s2.a", "s2.b", "s2.c", "l9.h", "g1", "l9.g",
                    DeviceFeatures.PROFILES_68, DeviceFeatures.FLAGS_68, "Eu.f2", "l", "a",
                    "com.mi.device.xiaomi.CommonFlagship",
                    "\u6671\u667d\u667f\u663c\u667f\u667b\u663c\u6676\u6677\u6664\u667b\u6671\u6677\u663c"
                            + "\u666a\u667b\u6673\u667d\u667f\u667b\u663c\u6651\u667d\u667f\u667f\u667d\u667c"
                            + "\u6654\u667e\u6673\u6675\u6661\u667a\u667b\u6662",
                    "d",
                    "l9.i0", new String[] {"L", "f0"}, "g2.a", "a", "r2.w", "J0",
                    new String[] {"v3.g", "b", "v3.c", "a", "v3.h", "b", "r2.m", "v"},
                    new String[] {"U8.d", "f", "zi.a", "s", "t"},
                    new String[] {"H4.p0", "a", "a", "com.android.camera.data.data.p", "l0",
                            "com.android.camera.data.data.i", "S", "r2.T", "r6.f0", "U5", "X0", "m0",
                            "i9.g", "M7", "l9.n0", "l1", "l9.h", "j", "d0",
                            "b", "l", "setZoomRatio", "j9.e", "B7", "j9.t", "n0"},
                    new String[] {"l7.k", "w"}, "r2.a",
                    new String[] {"l9.g", "d", "l9.h", "L4"},
                    new String[] {"Rr.d", "b", "Rr.a", "e"},
                    new String[] {"r2.b0", "o", "c"},
                    new String[] {"Q0", "X0", "J0", "Re.b"},
                    new String[] {"r2.g0", "C", "l9.h", "f2"},
                    new String[] {"Xh.a", "h", "S0", "r5"},
                    new String[] {"t7.d", "a", "Sh.t", "a", "b", "e", "f", "Q", "i", "g", "c",
                            "Vh.f", "d", "Vh.g", "a", "r2.B", "k6.l", "T", "l9.a", "B"},
                    new String[] {"r2.m", "u", "a", "E2"}),
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
