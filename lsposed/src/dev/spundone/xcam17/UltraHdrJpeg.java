package dev.spundone.xcam17;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * houji's HAL never produces the HDR gain map that Xiaomi's "HDR display" photos carry, so
 * this adds an estimated one to JPEG photos, making them Ultra HDR (Android's gain-map JPEG):
 * HDR screens show the highlights brighter, everything else shows the photo unchanged. The
 * gain map is estimated from the photo itself, brightening its highlights by up to
 * {@link #MAX_STOPS} stops; it holds no detail beyond what the photo already has.
 *
 * The photo itself is not re-encoded. Once the camera reports a final JPEG, a quarter-size
 * gain map is computed and encoded as a small JPEG with the hdrgm XMP, and appended to the
 * photo with the Container XMP and MPF index the Ultra HDR format uses. HEIF photos get one
 * too when switched on ({@link HeifGainMap}); motion photos and files that already have a gain
 * map are left alone.
 */
final class UltraHdrJpeg {
    /** Highest highlight boost, in stops (log2 of the gain). */
    static final float MAX_STOPS = 2f;
    /** sRGB luminance where the boost starts, and where it reaches its full value. */
    static final float RAMP_START = 0.72f;
    static final float RAMP_END = 0.98f;
    static final int GAIN_MAP_SAMPLE = 4;
    static final int GAIN_MAP_QUALITY = 85;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private UltraHdrJpeg() {}

    static void hook(ClassLoader cl, CameraBuild build) {
        if (build.newImageClass == null) return;
        try {
            Method callNewUri = cl.loadClass(build.newImageClass).getDeclaredMethod(build.newImageMethod,
                    Uri.class, boolean.class, String.class, int.class, boolean.class);
            XposedBridge.hookMethod(callNewUri, new FinalImage());
            Entry.log("ultra hdr: estimated gain map added to JPEG photos, and to HEIF photos when "
                    + HeifGainMap.PROPERTY + " is 1 (now " + HeifGainMap.enabled() + ")");
        } catch (Throwable t) {
            Entry.log("ultra hdr: not hooked: " + t);
        }
    }

    static final class FinalImage extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam p) {
            final Uri uri = (Uri) p.args[0];
            if (uri == null || Boolean.TRUE.equals(p.args[4])) return;
            WORKER.execute(() -> {
                try {
                    addGainMap(uri);
                } catch (Throwable t) {
                    Entry.log("ultra hdr: " + uri + ": " + t);
                }
            });
        }
    }

    static void addGainMap(Uri uri) throws Exception {
        ContentResolver resolver = currentResolver();
        if (resolver == null) return;
        byte[] photo;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return;
            photo = readAll(in);
        }
        byte[] out;
        if (isPlainJpeg(photo)) {
            out = withGainMap(photo);
        } else if (HeifGainMap.isPlainHeif(photo) && HeifGainMap.enabled()) {
            out = HeifGainMap.withGainMap(photo);
        } else {
            return;
        }
        if (out == null || !decodesWithGainMap(out)) {
            Entry.log("ultra hdr: " + uri + ": gain map not recognised, photo left as is");
            return;
        }
        try (OutputStream os = resolver.openOutputStream(uri, "wt")) {
            if (os == null) return;
            os.write(out);
        }
    }

    /** Android's own decoder finds the gain map (Bitmap.hasGainmap, API 34). */
    static boolean decodesWithGainMap(byte[] jpeg) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 16;
        Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        if (b == null) return false;
        boolean has = Boolean.TRUE.equals(Bitmap.class.getMethod("hasGainmap").invoke(b));
        b.recycle();
        return has;
    }

    static ContentResolver currentResolver() throws Exception {
        Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        return app == null ? null : ((android.content.Context) app).getContentResolver();
    }

    /** A single JPEG ending at its EOI, without a gain map or appended motion video. */
    static boolean isPlainJpeg(byte[] d) {
        if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) return false;
        if ((d[d.length - 2] & 0xFF) != 0xFF || (d[d.length - 1] & 0xFF) != 0xD9) return false;
        String head = new String(d, 0, Math.min(d.length, 1 << 17), StandardCharsets.ISO_8859_1);
        return !head.contains("hdrgm") && !head.contains("MotionPhoto") && !head.contains("MicroVideo");
    }

    static byte[] withGainMap(byte[] photo) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = GAIN_MAP_SAMPLE;
        Bitmap small = BitmapFactory.decodeByteArray(photo, 0, photo.length, o);
        if (small == null) return null;
        byte[] gainMap = gainMapJpeg(small);
        small.recycle();
        return assemble(photo, gainMap);
    }

    /** Grey gain map values, one per pixel: 0 leaves a pixel as it is, 255 brightens it by MAX_STOPS. */
    static byte[] gainValues(Bitmap src, int w, int h) {
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        byte[] out = new byte[w * h];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float y = (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
            float t = Math.max(0f, Math.min(1f, (y - RAMP_START) / (RAMP_END - RAMP_START)));
            out[i] = (byte) Math.round(t * t * (3f - 2f * t) * 255f);
        }
        return out;
    }

    static byte[] gainMapJpeg(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        byte[] g = gainValues(src, w, h);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            int v = g[i] & 0xFF;
            px[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        Bitmap map = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        map.compress(Bitmap.CompressFormat.JPEG, GAIN_MAP_QUALITY, jpeg);
        map.recycle();
        return insertAfterSoi(jpeg.toByteArray(), app1Xmp(gainMapXmp()));
    }

    static String gainMapXmp() {
        String stops = Float.toString(MAX_STOPS);
        return "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description rdf:about=\"\" xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\""
                + " hdrgm:Version=\"1.0\" hdrgm:GainMapMin=\"0\" hdrgm:GainMapMax=\"" + stops + "\""
                + " hdrgm:Gamma=\"1\" hdrgm:OffsetSDR=\"0.015625\" hdrgm:OffsetHDR=\"0.015625\""
                + " hdrgm:HDRCapacityMin=\"0\" hdrgm:HDRCapacityMax=\"" + stops + "\""
                + " hdrgm:BaseRenditionIsHDR=\"False\"/></rdf:RDF></x:xmpmeta>";
    }

    static String containerDescription(int gainMapLength) {
        return "<rdf:Description rdf:about=\"\" xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\""
                + " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\""
                + " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\" hdrgm:Version=\"1.0\">"
                + "<Container:Directory><rdf:Seq>"
                + "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Semantic=\"Primary\" Item:Mime=\"image/jpeg\"/></rdf:li>"
                + "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Semantic=\"GainMap\" Item:Mime=\"image/jpeg\""
                + " Item:Length=\"" + gainMapLength + "\"/></rdf:li>"
                + "</rdf:Seq></Container:Directory></rdf:Description>";
    }

    static final byte[] XMP_ID = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1);

    static byte[] app1Xmp(String xmp) {
        byte[] body = xmp.getBytes(StandardCharsets.UTF_8);
        int len = 2 + XMP_ID.length + body.length;
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        s.write(0xFF);
        s.write(0xE1);
        s.write(len >> 8);
        s.write(len);
        s.write(XMP_ID, 0, XMP_ID.length);
        s.write(body, 0, body.length);
        return s.toByteArray();
    }

    /**
     * Photo with the Container XMP (merged into its own XMP when it has one) and an MPF index
     * after its first APP segments, followed by the gain map JPEG.
     */
    static byte[] assemble(byte[] photo, byte[] gainMap) {
        int insertAt = 2;
        int xmpStart = -1, xmpEnd = -1;
        int i = 2;
        while (i + 4 <= photo.length && (photo[i] & 0xFF) == 0xFF) {
            int marker = photo[i + 1] & 0xFF;
            if (marker < 0xE0 || marker > 0xEF) break;
            int len = ((photo[i + 2] & 0xFF) << 8) | (photo[i + 3] & 0xFF);
            if (marker == 0xE1 && startsWith(photo, i + 4, XMP_ID)) {
                xmpStart = i;
                xmpEnd = i + 2 + len;
            } else if (marker == 0xE1 && insertAt == 2) {
                insertAt = i + 2 + len; // after EXIF
            }
            i += 2 + len;
        }
        String description = containerDescription(gainMap.length);
        byte[] xmpSegment;
        if (xmpStart >= 0) {
            String old = new String(photo, xmpStart + 4 + XMP_ID.length, xmpEnd - xmpStart - 4 - XMP_ID.length,
                    StandardCharsets.UTF_8);
            int close = old.lastIndexOf("</rdf:RDF>");
            if (close < 0) return null;
            xmpSegment = app1Xmp(old.substring(0, close) + description + old.substring(close));
        } else {
            xmpSegment = app1Xmp("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF"
                    + " xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" + description
                    + "</rdf:RDF></x:xmpmeta>");
        }
        if (xmpSegment.length - 2 > 0xFFFF) return null;

        ByteArrayOutputStream head = new ByteArrayOutputStream();
        ByteArrayOutputStream rest = new ByteArrayOutputStream();
        if (xmpStart >= 0) {
            head.write(photo, 0, xmpStart);
            head.write(xmpSegment, 0, xmpSegment.length);
            int from = xmpEnd;
            int to = Math.max(insertAt, xmpEnd);
            head.write(photo, from, to - from);
            insertAt = to;
        } else {
            head.write(photo, 0, insertAt);
            head.write(xmpSegment, 0, xmpSegment.length);
        }
        rest.write(photo, insertAt, photo.length - insertAt);

        int mpfSegment = 90;
        int tiffStart = head.size() + 8; // FF E2, length, "MPF\0"
        int primaryLength = head.size() + mpfSegment + rest.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream(primaryLength + gainMap.length);
        byte[] h = head.toByteArray();
        out.write(h, 0, h.length);
        byte[] mpf = mpf(primaryLength, gainMap.length, primaryLength - tiffStart);
        out.write(mpf, 0, mpf.length);
        byte[] r = rest.toByteArray();
        out.write(r, 0, r.length);
        out.write(gainMap, 0, gainMap.length);
        return out.toByteArray();
    }

    /** APP2 MPF: two images, the primary at offset 0 and the gain map after it. */
    static byte[] mpf(int primaryLength, int gainMapLength, int gainMapOffset) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(90).order(java.nio.ByteOrder.BIG_ENDIAN);
        b.put((byte) 0xFF).put((byte) 0xE2).putShort((short) 88);
        b.put("MPF\0".getBytes(StandardCharsets.ISO_8859_1));
        b.put((byte) 'M').put((byte) 'M').putShort((short) 0x2A).putInt(8);
        b.putShort((short) 3);
        b.putShort((short) 0xB000).putShort((short) 7).putInt(4).put("0100".getBytes(StandardCharsets.ISO_8859_1));
        b.putShort((short) 0xB001).putShort((short) 4).putInt(1).putInt(2);
        b.putShort((short) 0xB002).putShort((short) 7).putInt(32).putInt(50);
        b.putInt(0);
        b.putInt(0x030000).putInt(primaryLength).putInt(0).putShort((short) 0).putShort((short) 0);
        b.putInt(0).putInt(gainMapLength).putInt(gainMapOffset).putShort((short) 0).putShort((short) 0);
        return b.array();
    }

    static byte[] insertAfterSoi(byte[] jpeg, byte[] segment) {
        byte[] out = new byte[jpeg.length + segment.length];
        System.arraycopy(jpeg, 0, out, 0, 2);
        System.arraycopy(segment, 0, out, 2, segment.length);
        System.arraycopy(jpeg, 2, out, 2 + segment.length, jpeg.length - 2);
        return out;
    }

    static boolean startsWith(byte[] d, int at, byte[] prefix) {
        if (at + prefix.length > d.length) return false;
        for (int k = 0; k < prefix.length; k++) if (d[at + k] != prefix[k]) return false;
        return true;
    }

    static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
