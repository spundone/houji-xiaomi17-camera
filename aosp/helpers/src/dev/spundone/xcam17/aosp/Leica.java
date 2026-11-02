package dev.spundone.xcam17.aosp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.loader.AssetsProvider;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.hardware.camera2.CaptureResult;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import dalvik.system.DexClassLoader;

/**
 * Port of LeicaLegendary: Leica Essential (Legendary mode, module 256) with its M9, M3 and M10R
 * looks, rendered after capture by the processor of a community port of the camera (package
 * com.kobbin.leica). The installer copies the port's code, libraries and models from the
 * user's copy to {@link #KIT}; nothing of it is in the camera or this kit. See the LSPosed hook.
 *
 * The port's processor reads the photo task (Sh.t) with two fields the port added to it; the
 * patch adds those to the camera's own task class, so the processor runs on the real task. Its
 * models are app assets: the kit folder is added to the app's resources as a loader. Its notices
 * (toasts) are the port's own, in Chinese.
 *
 * names lists the build's members, "#"-separated: saver class and method, task class, its
 * image holder, info, EXIF and result holder fields and total result method, the holder's
 * image field, the info's module field, the result holder's result field, image class and its
 * bytes method, image factory class and method, component class, camera manager class and its
 * preview state method, preview state class and its latest result method, components class
 * and method.
 */
public final class Leica {
    static final String KIT = "/system/etc/xcam17/leica";
    static final String LIB = KIT + "/lib";
    static final String ASSETS = KIT + "/assets";
    static final String[] DEX = {KIT + "/classes11.dex", KIT + "/classes12.dex"};
    static final String PROCESSOR = "com.codex.leicaapp.LegendaryPostProcessor";
    static final int MODULE = 256;

    /** Look and preview result per Legendary photo task, from the shutter until it is saved. */
    static final Map<Object, Object[]> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private static Camera camera;
    private static boolean cameraFailed;
    private static Method process;
    private static boolean processorFailed;

    private Leica() {}

    /** The port's files are installed; also the Leica Essential flags (c3, b3). */
    public static boolean installed() {
        return new File(DEX[0]).isFile();
    }

    /** Mode list (G): Legendary added when the port's files are installed. */
    public static int[] modes(int[] modes) {
        if (!installed()) {
            Log.once("leica: port files not found in " + KIT + ", Leica Essential stays off");
            return modes;
        }
        Log.once("leica: Leica Essential on, with the port's files in " + KIT);
        int[] base = modes == null ? new int[0] : modes;
        for (int m : base) if (m == MODULE) return base;
        int[] out = Arrays.copyOf(base, base.length + 1);
        out[base.length] = MODULE;
        return out;
    }

    static final class Camera {
        Field taskImageHolder, taskInfo, taskExif, taskResultHolder, holderImage, infoModule, cameraManager,
                legendaryStyle, legendaryPreview;
        Method taskTotal, imageBytes, imageFactory, imageRelease, componentValue, components, componentOf,
                previewState, latestResult, setXmp;
        Class<?> component;
    }

    static synchronized Camera camera(String names, Object module) {
        if (camera != null || cameraFailed) return camera;
        try {
            String[] n = names.split("#");
            ClassLoader cl = Leica.class.getClassLoader();
            Camera c = new Camera();
            Class<?> task = Class.forName(n[2], false, cl);
            c.taskImageHolder = field(task, n[3]);
            c.taskInfo = field(task, n[4]);
            c.taskExif = field(task, n[5]);
            c.taskResultHolder = field(task, n[6]);
            c.taskTotal = task.getDeclaredMethod(n[7]);
            c.holderImage = field(c.taskImageHolder.getType(), n[8]);
            c.infoModule = field(c.taskInfo.getType(), n[9]);
            c.legendaryStyle = field(task, "legendaryStyle");
            c.legendaryPreview = field(task, "legendaryPreviewResult");
            Class<?> image = Class.forName(n[11], false, cl);
            c.imageBytes = image.getDeclaredMethod(n[12]);
            c.imageRelease = image.getDeclaredMethod("release");
            c.imageFactory = Class.forName(n[13], false, cl).getDeclaredMethod(n[14], Object.class);
            c.component = Class.forName(n[15], false, cl);
            c.componentValue = c.component.getMethod("getComponentValue", int.class);
            c.components = Class.forName(n[20], false, cl).getDeclaredMethod(n[21]);
            c.componentOf = c.components.getReturnType().getMethod("x", Class.class);
            c.previewState = Class.forName(n[16], false, cl).getDeclaredMethod(n[17]);
            c.latestResult = Class.forName(n[18], false, cl).getDeclaredMethod(n[19]);
            for (Method m : c.taskExif.getType().getDeclaredMethods()) {
                if (m.getName().equals("setXmpMetaUtil") && m.getParameterTypes().length == 1) c.setXmp = m;
            }
            for (Class<?> k = module.getClass(); k != null && c.cameraManager == null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (f.getName().equals("mCameraManager") && f.getType() == c.previewState.getDeclaringClass()) {
                        f.setAccessible(true);
                        c.cameraManager = f;
                    }
                }
            }
            if (c.setXmp == null || c.cameraManager == null || c.holderImage.getType() != image
                    || c.infoModule.getType() != int.class || c.imageBytes.getReturnType() != byte[].class
                    || c.latestResult.getReturnType() != CaptureResult.class) {
                throw new IllegalStateException("camera members have an unexpected shape");
            }
            for (Method m : new Method[] {c.taskTotal, c.imageFactory, c.components, c.previewState, c.latestResult}) {
                m.setAccessible(true);
            }
            camera = c;
        } catch (Throwable t) {
            cameraFailed = true;
            Log.once("leica: not set up", t);
        }
        return camera;
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** After Camera2Module.updateParallelTaskData: note the look and preview result at the shutter. */
    public static void snapshot(Object module, Object task, String names) {
        if (task == null || !installed()) return;
        Camera c = camera(names, module);
        if (c == null) return;
        try {
            if (c.infoModule.getInt(c.taskInfo.get(task)) != MODULE) return;
            Object component = c.componentOf.invoke(c.components.invoke(null), c.component);
            String look = component == null ? null : (String) c.componentValue.invoke(component, MODULE);
            CaptureResult preview = null;
            Object manager = c.cameraManager.get(module);
            Object state = manager == null ? null : c.previewState.invoke(manager);
            if (state != null) preview = (CaptureResult) c.latestResult.invoke(state);
            PENDING.put(task, new Object[] {look == null ? "M9" : look, preview});
        } catch (Throwable t) {
            Log.once("leica: snapshot", t);
        }
    }

    /** Before the photo is saved: replace the JPEG with the port's rendering of the look. */
    public static void render(Object task) {
        Object[] shot = task == null ? null : PENDING.remove(task);
        Camera c = camera;
        if (shot == null || c == null) return;
        try {
            Method proc = processor();
            if (proc == null) return;
            Object holder = c.taskImageHolder.get(task);
            Object image = holder == null ? null : c.holderImage.get(holder);
            byte[] jpeg = image == null ? null : (byte[]) c.imageBytes.invoke(image);
            if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
                Log.once("leica: photo is not a JPEG (Settings > Photo format), saved without the look");
                return;
            }
            c.legendaryStyle.set(task, shot[0]);
            c.legendaryPreview.set(task, shot[1]);
            byte[] out = (byte[]) proc.invoke(null, jpeg, task);
            if (out == null || out == jpeg) return;
            c.holderImage.set(holder, c.imageFactory.invoke(null, (Object) out));
            c.imageRelease.invoke(image);
            Object exif = c.taskExif.get(task);
            if (exif != null) c.setXmp.invoke(exif, (Object) null);
            android.util.Log.i("xcam17", "leica: " + shot[0] + " rendered, " + jpeg.length + " -> " + out.length + " bytes");
        } catch (Throwable t) {
            android.util.Log.w("xcam17", "leica: render", t);
        }
    }

    private static synchronized Method processor() {
        if (process != null || processorFailed) return process;
        try {
            Context app = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            app.getApplicationInfo().nativeLibraryDir = LIB;
            addAssets(app);
            ClassLoader kit = new DexClassLoader(String.join(File.pathSeparator, DEX), null, LIB,
                    Leica.class.getClassLoader());
            process = kit.loadClass(PROCESSOR).getMethod("process", byte[].class, Object.class);
        } catch (Throwable t) {
            processorFailed = true;
            Log.once("leica: processor not loaded", t);
        }
        return process;
    }

    /** The port reads its models as app assets; the kit folder is laid out like an APK. */
    static void addAssets(Context app) throws Exception {
        ResourcesProvider provider;
        try {
            provider = ResourcesProvider.loadFromDirectory(KIT, null);
        } catch (Throwable t) {
            Log.once("leica: kit folder as resources", t);
            provider = ResourcesProvider.empty(new KitAssets());
        }
        ResourcesLoader loader = new ResourcesLoader();
        loader.addProvider(provider);
        app.getResources().addLoaders(loader);
    }

    static final class KitAssets implements AssetsProvider {
        @Override
        public AssetFileDescriptor loadAssetFd(String path, int accessMode) {
            String name = path.startsWith("assets/") ? path.substring(7) : path;
            File f = new File(ASSETS, name);
            try {
                if (!f.isFile()) return null;
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                return new AssetFileDescriptor(fd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
