package dev.spundone.xcam17;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import dalvik.system.DexClassLoader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Leica Essential (Legendary mode, module 256) with its M9, M3 and M10R looks. The camera hands
 * the chosen look to the HAL as com.xiaomi.sessionparams.legendMode, which houji's HAL doesn't
 * define, so on its own the mode saves unprocessed photos. A community port of the camera
 * (6.8.001960.0, package com.kobbin.leica) renders the looks in the app after capture, with the
 * Leica algorithms and filter tables from Xiaomi's Leica app for the 17 Ultra. That port is not
 * redistributed: the installer copies its code, libraries and models from the user's copy to
 * {@link #KIT}, and this runs its processor on Legendary photos as the camera saves them.
 *
 * The port's processor reads the photo task through two classes of its camera build (Sh.t and
 * Sh.h) and two fields the port added to Sh.t. It is loaded in a class loader that resolves those
 * two names to the stand-ins in this module's Sh package, which are filled in from the real task.
 * It finds its helper programs through the app's nativeLibraryDir; the camera loads its own
 * libraries from its APK and has no such folder, so that points at the kit's lib folder. Its
 * models are read as app assets, which are served from the kit.
 */
final class LeicaLegendary {
    static final String KIT = "/system/etc/xcam17/leica";
    static final String LIB = KIT + "/lib";
    static final String ASSETS = KIT + "/assets";
    static final String[] DEX = {KIT + "/classes11.dex", KIT + "/classes12.dex"};
    static final String PROCESSOR = "com.codex.leicaapp.LegendaryPostProcessor";
    static final String[] ASSET_PREFIXES = {"legend18", "leica-v4", "leica-v43-ui", "leica_filter_param_"};
    static final int MODULE = 256;

    static final DeviceFeatures.Flag[] FLAGS = {
            new DeviceFeatures.Flag("c3", Boolean.TRUE, "Leica Essential (Legendary mode): M9 and M3 looks"),
            new DeviceFeatures.Flag("b3", Boolean.TRUE, "Legendary mode's M10R look"),
            new DeviceFeatures.Flag("G", new int[] {MODULE}, "Legendary (module 256) in the mode list"),
    };

    /** Look and preview result per Legendary photo task, from the shutter until it is saved. */
    static final Map<Object, Shot> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private LeicaLegendary() {}

    static final class Shot {
        final String look;
        final CaptureResult preview;

        Shot(String look, CaptureResult preview) {
            this.look = look;
            this.preview = preview;
        }
    }

    /** The build's members, resolved and shape-checked. */
    static final class Camera {
        Field taskImageHolder, taskInfo, taskExif, taskResultHolder, holderImage, infoModule, resultField;
        Method taskTotal, imageBytes, imageFactory, imageRelease, componentValue, components, componentOf,
                previewState, latestResult;
        Class<?> task, component;
        Field cameraManager;
    }

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.leica == null) return;
        if (!new File(DEX[0]).isFile()) {
            Entry.log("leica: kit not installed (" + KIT + "), Leica Essential stays off");
            return;
        }
        try {
            String[] n = build.leica;
            Camera c = new Camera();
            c.task = cl.loadClass(n[2]);
            c.taskImageHolder = c.task.getDeclaredField(n[3]);
            c.taskInfo = c.task.getDeclaredField(n[4]);
            c.taskExif = c.task.getDeclaredField(n[5]);
            c.taskResultHolder = c.task.getDeclaredField(n[6]);
            c.taskTotal = c.task.getDeclaredMethod(n[7]);
            c.holderImage = c.taskImageHolder.getType().getDeclaredField(n[8]);
            c.infoModule = c.taskInfo.getType().getDeclaredField(n[9]);
            c.resultField = c.taskResultHolder.getType().getDeclaredField(n[10]);
            Class<?> image = cl.loadClass(n[11]);
            c.imageBytes = image.getDeclaredMethod(n[12]);
            c.imageRelease = image.getDeclaredMethod("release");
            c.imageFactory = cl.loadClass(n[13]).getDeclaredMethod(n[14], Object.class);
            c.component = cl.loadClass(n[15]);
            c.componentValue = c.component.getMethod("getComponentValue", int.class);
            c.components = cl.loadClass(build.componentsClass).getDeclaredMethod(build.componentsMethod);
            c.componentOf = c.components.getReturnType().getMethod("x", Class.class);
            c.previewState = cl.loadClass(n[16]).getDeclaredMethod(n[17]);
            c.latestResult = cl.loadClass(n[18]).getDeclaredMethod(n[19]);
            Method setXmp = null;
            for (Method m : c.taskExif.getType().getDeclaredMethods()) {
                if (m.getName().equals("setXmpMetaUtil") && m.getParameterTypes().length == 1) setXmp = m;
            }
            if (setXmp == null || c.holderImage.getType() != image || c.infoModule.getType() != int.class
                    || c.resultField.getType() != CaptureResult.class
                    || c.taskTotal.getReturnType() != TotalCaptureResult.class
                    || c.imageBytes.getReturnType() != byte[].class
                    || c.imageFactory.getReturnType() != image
                    || c.previewState.getReturnType() != c.latestResult.getDeclaringClass()
                    || c.latestResult.getReturnType() != CaptureResult.class) {
                Entry.log("leica: camera members have an unexpected shape, skipping");
                return;
            }
            Class<?> module = cl.loadClass("com.android.camera.module.Camera2Module");
            for (Class<?> k = module; k != null && c.cameraManager == null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (f.getName().equals("mCameraManager") && f.getType() == c.previewState.getDeclaringClass()) {
                        c.cameraManager = f;
                    }
                }
            }
            if (c.cameraManager == null) {
                Entry.log("leica: Camera2Module.mCameraManager not found, skipping");
                return;
            }
            for (Field f : new Field[] {c.taskImageHolder, c.taskInfo, c.taskExif, c.taskResultHolder,
                    c.holderImage, c.infoModule, c.resultField, c.cameraManager}) {
                f.setAccessible(true);
            }
            c.taskTotal.setAccessible(true);

            int snapshots = 0;
            for (Method m : module.getDeclaredMethods()) {
                if (m.getName().equals("updateParallelTaskData") && m.getParameterTypes().length == 2
                        && m.getParameterTypes()[0] == c.task) {
                    XposedBridge.hookMethod(m, new Snapshot(c));
                    snapshots++;
                }
            }
            Method save = cl.loadClass(n[0]).getDeclaredMethod(n[1], c.task);
            if (snapshots != 1) {
                Entry.log("leica: Camera2Module.updateParallelTaskData not found, skipping");
                return;
            }
            XposedBridge.hookMethod(save, new Render(c, setXmp, cl));
            hookAssets();
            DeviceFeatures.enable(cl, build.profiles, FLAGS);
            Entry.log("leica: Leica Essential looks rendered by the Leica port's processor from " + KIT);
        } catch (Throwable t) {
            Entry.log("leica: not hooked: " + t);
        }
    }

    /** At the shutter: note the look and the preview's latest result for a Legendary photo. */
    static final class Snapshot extends XC_MethodHook {
        private final Camera c;

        Snapshot(Camera c) {
            this.c = c;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            Object task = p.args[0];
            try {
                if (task == null || c.infoModule.getInt(c.taskInfo.get(task)) != MODULE) return;
                Object component = c.componentOf.invoke(c.components.invoke(null), c.component);
                String look = component == null ? null : (String) c.componentValue.invoke(component, MODULE);
                CaptureResult preview = null;
                Object manager = c.cameraManager.get(p.thisObject);
                Object state = manager == null ? null : c.previewState.invoke(manager);
                if (state != null) preview = (CaptureResult) c.latestResult.invoke(state);
                PENDING.put(task, new Shot(look == null ? "M9" : look, preview));
            } catch (Throwable t) {
                Entry.log("leica: snapshot: " + t);
            }
        }
    }

    /** As the photo is saved: replace the JPEG with the port's rendering of the look. */
    static final class Render extends XC_MethodHook {
        private final Camera c;
        private final Method setXmp;
        private final ClassLoader camera;
        private Method process;
        private boolean failed;

        Render(Camera c, Method setXmp, ClassLoader camera) {
            this.c = c;
            this.setXmp = setXmp;
            this.camera = camera;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            Object task = p.args[0];
            Shot shot = task == null ? null : PENDING.remove(task);
            if (shot == null) return;
            try {
                Method proc = processor();
                if (proc == null) return;
                Object holder = c.taskImageHolder.get(task);
                Object image = holder == null ? null : c.holderImage.get(holder);
                byte[] jpeg = image == null ? null : (byte[]) c.imageBytes.invoke(image);
                if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
                    Entry.log("leica: photo is not a JPEG (Settings > Photo format), saved without the look");
                    return;
                }
                Sh.t stand = new Sh.t();
                stand.legendaryStyle = shot.look;
                stand.legendaryPreviewResult = shot.preview;
                stand.total = (TotalCaptureResult) c.taskTotal.invoke(task);
                Object results = c.taskResultHolder.get(task);
                if (results != null) {
                    stand.f = new Sh.h();
                    stand.f.c = (CaptureResult) c.resultField.get(results);
                }
                byte[] out = (byte[]) proc.invoke(null, jpeg, stand);
                if (out == null || out == jpeg) return;
                c.holderImage.set(holder, c.imageFactory.invoke(null, (Object) out));
                c.imageRelease.invoke(image);
                Object exif = c.taskExif.get(task);
                if (exif != null) setXmp.invoke(exif, (Object) null);
                Entry.log("leica: " + shot.look + " rendered, " + jpeg.length + " -> " + out.length + " bytes");
            } catch (Throwable t) {
                Entry.log("leica: render: " + t);
            }
        }

        private synchronized Method processor() {
            if (process != null || failed) return process;
            try {
                Context app = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                app.getApplicationInfo().nativeLibraryDir = LIB;
                ClassLoader kit = new KitLoader(String.join(File.pathSeparator, DEX), LIB, camera);
                Class<?> processor = kit.loadClass(PROCESSOR);
                process = processor.getMethod("process", byte[].class, Object.class);
                hookNotices(processor, kit);
            } catch (Throwable t) {
                failed = true;
                Entry.log("leica: processor not loaded: " + t);
            }
            return process;
        }
    }

    /**
     * Loads the port's classes from the kit. Sh.t and Sh.h, the port camera build's task classes,
     * resolve to this module's stand-ins; everything else comes from the camera as usual.
     */
    static final class KitLoader extends DexClassLoader {
        KitLoader(String dexPath, String libraryPath, ClassLoader parent) {
            super(dexPath, null, libraryPath, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("Sh.t") || name.equals("Sh.h")) {
                return LeicaLegendary.class.getClassLoader().loadClass(name);
            }
            if (name.startsWith("com.codex.leicaapp.")) {
                synchronized (this) {
                    Class<?> c = findLoadedClass(name);
                    return c != null ? c : findClass(name);
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * The port's notices (toasts) are in Chinese. Those that come with every photo on houji are
     * logged instead: the AI style model and the person mask need the 8 Elite Gen 5's DSP, the
     * port's RAW path isn't used here, and its watermark step reads task fields the stand-in
     * doesn't have. The rest are shown in English.
     */
    static final String[][] NOTICES = {
            {"本张已使用备用影调，完整模型未完成", null},
            {"本帧 RAW 不可用，已使用相机 JPEG 处理", null},
            {"水印添加失败，照片已保存", null},
            {"影调未完成，本张已保留原片", "The Leica look didn't finish; the original photo was kept"},
            {"影调处理失败，本张已保留原片", "The Leica look failed; the original photo was kept"},
            {"徕卡算法处理失败，已保留原始照片", "Leica processing failed; the original photo was kept"},
            {"处理已中断，本张已保留原片", "Leica processing was interrupted; the original photo was kept"},
            {"处理队列已满，本张已保留原片", "Leica processing is busy; the original photo was kept"},
    };

    static void hookNotices(Class<?> processor, ClassLoader kit) {
        try {
            XposedBridge.hookMethod(processor.getDeclaredMethod("queueNotice", String.class), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    String english = english((String) p.args[0]);
                    if (english == null) p.setResult(null);
                    else p.args[0] = english;
                }
            });
            XC_MethodHook skip = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    Entry.log("leica: notice skipped: " + p.method.getName());
                    p.setResult(null);
                }
            };
            XposedBridge.hookMethod(processor.getDeclaredMethod("lambda$process$1"), skip);
            XposedBridge.hookMethod(kit.loadClass("com.codex.leicaapp.OriginalWatermark")
                    .getDeclaredMethod("lambda$apply$0", Context.class), skip);
            XposedBridge.hookMethod(processor.getDeclaredMethod("lambda$process$2"), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    p.setResult(null);
                    processor.getDeclaredMethod("queueNotice", String.class).invoke(null, NOTICES[5][0]);
                }
            });
        } catch (Throwable t) {
            Entry.log("leica: notices left as they are: " + t);
        }
    }

    /** The English notice for one of the port's, null to log it instead, or the text unchanged. */
    static String english(String notice) {
        for (String[] n : NOTICES) {
            if (n[0].equals(notice)) {
                if (n[1] == null) Entry.log("leica: notice: " + notice);
                return n[1];
            }
        }
        return notice;
    }

    /** The port reads its models as app assets; the camera's APK doesn't have them. */
    static void hookAssets() throws NoSuchMethodException {
        XposedBridge.hookMethod(AssetManager.class.getDeclaredMethod("open", String.class, int.class),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        File f = kitAsset((String) p.args[0]);
                        if (f == null || !f.isFile()) return;
                        try {
                            p.setResult(new FileInputStream(f));
                        } catch (Throwable t) {
                            Entry.log("leica: asset " + f + ": " + t);
                        }
                    }
                });
        XposedBridge.hookMethod(AssetManager.class.getDeclaredMethod("list", String.class),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        File f = kitAsset((String) p.args[0]);
                        if (f != null && f.isDirectory()) p.setResult(f.list());
                    }
                });
    }

    static File kitAsset(String name) {
        if (name == null) return null;
        for (String prefix : ASSET_PREFIXES) {
            if (name.startsWith(prefix)) return new File(ASSETS, name);
        }
        return null;
    }
}
