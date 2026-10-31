package dev.spundone.xcam17;

import android.util.Range;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * At 60 fps the front camera records with a dynamic [30, 60] frame-rate range. With the
 * screen fill light on, auto exposure aims brighter and stretches frames to 30-50 ms, so the
 * file comes out at ~35 fps. While the fill light (flash value 104) is on, this asks for a
 * fixed [60, 60] range instead; with it off, the camera's own range is left alone.
 */
final class FillLight60 {
    /** Flash value of the front "screen halo" fill light, and the video mode index. */
    static final String SCREEN_LIGHT = "104";
    static final int VIDEO_MODE = 162;

    private FillLight60() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.fpsRangeClass == null) return;
        try {
            Class<?> config = cl.loadClass(build.fpsRangeClass);
            Method components = cl.loadClass(build.componentsClass).getDeclaredMethod(build.componentsMethod);
            Class<?> flash = cl.loadClass(build.flashComponentClass);
            Method lookup = components.getReturnType().getMethod("x", Class.class);
            Method value = flash.getMethod("getComponentValue", int.class);
            XC_MethodHook fixed = new FixedRange(components, lookup, flash, value);
            int hooked = 0;
            for (String name : build.fpsRangeMethods) {
                XposedBridge.hookMethod(config.getDeclaredMethod(name, Range.class), fixed);
                hooked++;
            }
            Entry.log("fill light: 60 fps held while the front fill light is on (" + hooked + " setters)");
        } catch (Throwable t) {
            Entry.log("fill light: not hooked: " + t);
        }
    }

    static final class FixedRange extends XC_MethodHook {
        private final Method components;
        private final Method lookup;
        private final Class<?> flash;
        private final Method value;
        private boolean warned;

        FixedRange(Method components, Method lookup, Class<?> flash, Method value) {
            this.components = components;
            this.lookup = lookup;
            this.flash = flash;
            this.value = value;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            Object arg = p.args[0];
            if (!(arg instanceof Range)) return;
            Range<?> range = (Range<?>) arg;
            if (!Integer.valueOf(60).equals(range.getUpper()) || Integer.valueOf(60).equals(range.getLower())) return;
            try {
                Object component = lookup.invoke(components.invoke(null), flash);
                if (component != null && SCREEN_LIGHT.equals(value.invoke(component, VIDEO_MODE))) {
                    p.args[0] = new Range<>(60, 60);
                }
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    Entry.log("fill light: flash state unreadable: " + t);
                }
            }
        }
    }
}
