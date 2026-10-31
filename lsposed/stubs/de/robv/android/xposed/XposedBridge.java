package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Compile-only stub of the Xposed API (api 82); the real class is provided at runtime. */
public final class XposedBridge {
    public static void log(String text) {}

    public static void log(Throwable t) {}

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }
}
