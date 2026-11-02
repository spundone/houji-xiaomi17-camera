package dev.spundone.xcam17.aosp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The HEIF side of {@link UltraHdrJpeg}: adds the same estimated gain map to HEIF photos as an
 * ISO 21496-1 gain map, the layout Android's camera service writes for HEIC Ultra HDR and its
 * image decoder (CrabbyAvif, in libhwui) reads:
 * - a hidden HEVC image item holding the gain map, quarter size;
 * - a 'tmap' item holding the gain map metadata, derived ('dimg') from the photo and the gain map;
 * - an 'altr' group offering the 'tmap' item ahead of the photo, and the 'tmap' brand.
 *
 * The photo's own items stay as they are, primary included, so readers that don't know gain
 * maps see the same HEIF. The meta box is rebuilt from its parsed parts and every file offset
 * in 'iloc' is moved by the bytes the edit adds before 'mdat'; the new items' data go into a
 * second 'mdat' at the end. The gain map is encoded with the platform HEVC encoder.
 *
 * Opt-in ({@link #PROPERTY} set to 1: the KernelSU module's Action button, or a ROM prop): Android decodes these
 * files with the gain map, but Google Photos won't back them up ("Can't back up"), and macOS (27)
 * and ffmpeg refuse any HEIF with a 'tmap' item, the same layout Android's own HEIC Ultra HDR uses.
 */
final class HeifGainMap {
    static final int GAIN_MAP_DIVISOR = 4;
    static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;

    static final String PROPERTY = "debug.xcam17.heic_ultrahdr";

    private HeifGainMap() {}

    static boolean enabled() {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, PROPERTY);
            return "1".equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A HEIF image without a gain map. */
    static boolean isPlainHeif(byte[] d) {
        if (d.length < 16 || !"ftyp".equals(fourcc(d, 4))) return false;
        int ftypSize = u32(d, 0);
        boolean heif = false;
        for (int o = 8; o + 4 <= ftypSize; o += 4) {
            if (o == 12) continue; // minor version
            String brand = fourcc(d, o);
            if ("tmap".equals(brand)) return false;
            if ("heic".equals(brand) || "heix".equals(brand) || "mif1".equals(brand)) heif = true;
        }
        return heif;
    }

    static byte[] withGainMap(byte[] photo) throws Exception {
        Heif heif = Heif.parse(photo);
        if (heif == null || heif.hasTmap) return null;
        int w = heif.primaryWidth / GAIN_MAP_DIVISOR & ~15;
        int h = heif.primaryHeight / GAIN_MAP_DIVISOR & ~15;
        if (w < 16 || h < 16) return null;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = UltraHdrJpeg.GAIN_MAP_SAMPLE;
        Bitmap small = BitmapFactory.decodeByteArray(photo, 0, photo.length, o);
        if (small == null) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(small, w, h, true);
        byte[] gain = UltraHdrJpeg.gainValues(scaled, w, h);
        if (scaled != small) scaled.recycle();
        small.recycle();

        Hevc coded = Hevc.encodeGrey(gain, w, h);
        if (coded == null) return null;
        return heif.addGainMap(coded, w, h, tmapMetadata());
    }

    /** ISO 21496-1 metadata, one channel, in the units the JPEG hdrgm XMP uses (log2 stops). */
    static byte[] tmapMetadata() {
        ByteBuffer b = ByteBuffer.allocate(62);
        b.put((byte) 0);                 // version
        b.putShort((short) 0);           // minimum_version
        b.putShort((short) 0);           // writer_version
        b.put((byte) 0x40);              // single channel, use_base_colour_space
        b.putInt(0).putInt(1);           // base_hdr_headroom 0
        b.putInt((int) UltraHdrJpeg.MAX_STOPS).putInt(1); // alternate_hdr_headroom
        b.putInt(0).putInt(1);           // gain_map_min
        b.putInt((int) UltraHdrJpeg.MAX_STOPS).putInt(1); // gain_map_max
        b.putInt(1).putInt(1);           // gamma
        b.putInt(1).putInt(64);          // base_offset
        b.putInt(1).putInt(64);          // alternate_offset
        return b.array();
    }

    /** A HEVC still: its parameter sets and its frame as length-prefixed NAL units. */
    static final class Hevc {
        byte[] vps, sps, pps;
        byte[] frame;

        static Hevc encodeGrey(byte[] grey, int w, int h) throws Exception {
            MediaFormat f = MediaFormat.createVideoFormat(HEVC, w, h);
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            f.setInteger(MediaFormat.KEY_BIT_RATE, w * h * 4);
            f.setInteger(MediaFormat.KEY_FRAME_RATE, 1);
            f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0);
            f.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
            f.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
            f.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            MediaCodec codec = MediaCodec.createEncoderByType(HEVC);
            Hevc out = new Hevc();
            List<byte[]> nals = new ArrayList<>();
            try {
                codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                int in = codec.dequeueInputBuffer(2_000_000);
                if (in < 0) return null;
                Image img = codec.getInputImage(in);
                if (img == null) return null;
                fillGrey(img, grey, w, h);
                codec.queueInputBuffer(in, 0, w * h * 3 / 2, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                for (int tries = 0; tries < 100; tries++) {
                    int idx = codec.dequeueOutputBuffer(info, 100_000);
                    if (idx < 0) continue;
                    ByteBuffer buf = codec.getOutputBuffer(idx);
                    byte[] data = new byte[info.size];
                    if (buf != null) {
                        buf.position(info.offset);
                        buf.get(data);
                    }
                    nals.addAll(splitAnnexB(data));
                    boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(idx, false);
                    if (end) break;
                }
            } finally {
                try {
                    codec.stop();
                } catch (Throwable ignored) {
                    // Already stopped or never started.
                }
                codec.release();
            }
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            for (byte[] nal : nals) {
                if (nal.length < 2) continue;
                int type = (nal[0] >> 1) & 0x3F;
                if (type == 32) out.vps = nal;
                else if (type == 33) out.sps = nal;
                else if (type == 34) out.pps = nal;
                else if (type < 32) {
                    frame.write(nal.length >>> 24);
                    frame.write(nal.length >>> 16);
                    frame.write(nal.length >>> 8);
                    frame.write(nal.length);
                    frame.write(nal, 0, nal.length);
                }
            }
            out.frame = frame.toByteArray();
            return out.vps == null || out.sps == null || out.pps == null || out.frame.length == 0 ? null : out;
        }

        static void fillGrey(Image img, byte[] grey, int w, int h) {
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer y = planes[0].getBuffer();
            int yRow = planes[0].getRowStride(), yPix = planes[0].getPixelStride();
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) y.put(r * yRow + c * yPix, grey[r * w + c]);
            }
            for (int p = 1; p < 3; p++) {
                ByteBuffer uv = planes[p].getBuffer();
                int row = planes[p].getRowStride(), pix = planes[p].getPixelStride();
                for (int r = 0; r < h / 2; r++) {
                    for (int c = 0; c < w / 2; c++) {
                        int at = r * row + c * pix;
                        if (at < uv.limit()) uv.put(at, (byte) 128);
                    }
                }
            }
        }

        static List<byte[]> splitAnnexB(byte[] d) {
            List<byte[]> out = new ArrayList<>();
            int start = -1;
            for (int i = 0; i + 3 <= d.length; ) {
                boolean sc3 = d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1;
                boolean sc4 = i + 4 <= d.length && d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 0 && d[i + 3] == 1;
                if (sc3 || sc4) {
                    if (start >= 0) out.add(trimZeros(d, start, i));
                    i += sc4 ? 4 : 3;
                    start = i;
                } else {
                    i++;
                }
            }
            if (start >= 0 && start < d.length) out.add(trimZeros(d, start, d.length));
            return out;
        }

        static byte[] trimZeros(byte[] d, int from, int to) {
            while (to > from && d[to - 1] == 0) to--;
            byte[] r = new byte[to - from];
            System.arraycopy(d, from, r, 0, r.length);
            return r;
        }

        /** hvcC from the parameter sets; profile, tier and level are read from the SPS. */
        byte[] hvcC() {
            byte[] rbsp = unescape(sps);
            // NAL header (2 bytes), then vps id (4 bits), max_sub_layers_minus1 (3), nesting (1).
            int subLayers = ((rbsp[2] >> 1) & 0x7) + 1;
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(1);
            b.write(rbsp, 3, 12); // general profile_tier_level: 1 + 4 + 6 + 1 bytes
            b.write(0xF0);
            b.write(0x00);        // min_spatial_segmentation_idc
            b.write(0xFC);        // parallelismType
            b.write(0xFD);        // chroma_format_idc 1
            b.write(0xF8);        // bit_depth_luma_minus8 0
            b.write(0xF8);        // bit_depth_chroma_minus8 0
            b.write(0);
            b.write(0);           // avgFrameRate
            b.write((subLayers << 3) | (1 << 2) | 3); // numTemporalLayers, temporalIdNested, lengthSizeMinusOne
            b.write(3);
            for (byte[] nal : new byte[][] {vps, sps, pps}) {
                b.write(0x80 | ((nal[0] >> 1) & 0x3F));
                b.write(0);
                b.write(1);
                b.write(nal.length >>> 8);
                b.write(nal.length);
                b.write(nal, 0, nal.length);
            }
            return b.toByteArray();
        }

        static byte[] unescape(byte[] nal) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(nal.length);
            int zeros = 0;
            for (byte v : nal) {
                if (zeros >= 2 && v == 3) {
                    zeros = 0;
                    continue;
                }
                zeros = v == 0 ? zeros + 1 : 0;
                b.write(v);
            }
            return b.toByteArray();
        }
    }

    /** The parts of a HEIF file the edit touches, parsed; everything else is copied as is. */
    static final class Heif {
        byte[] file;
        int ftypEnd, metaStart, metaEnd;
        int metaVersionFlags;
        /** meta's children in order, as raw boxes; the edited ones are replaced when written. */
        List<byte[]> children = new ArrayList<>();
        List<String> childTypes = new ArrayList<>();
        int primaryId;
        int primaryWidth, primaryHeight;
        int primaryIspeIndex;
        boolean hasTmap;
        int maxItemId;
        // iloc
        int ilocVersion, ilocFlags, offsetSize, lengthSize, baseOffsetSize, indexSize;
        List<long[]> ilocItems = new ArrayList<>(); // id, construction, dataRef, baseOffset, extentCount
        List<long[][]> ilocExtents = new ArrayList<>(); // per item: {index, offset, length}
        // iinf
        int iinfVersion, iinfFlags;
        List<byte[]> infes = new ArrayList<>();
        // ipco / ipma
        List<byte[]> properties = new ArrayList<>();
        int ipmaVersion, ipmaFlags;
        byte[] ipmaEntries;
        int ipmaCount;
        byte[] irefBody; // iref after its FullBox header, or null
        int irefVersion;
        byte[] grplBody; // grpl contents, or null

        static Heif parse(byte[] d) {
            Heif h = new Heif();
            h.file = d;
            int off = 0;
            while (off + 8 <= d.length) {
                long size = u32(d, off) & 0xFFFFFFFFL;
                String type = fourcc(d, off + 4);
                int hdr = 8;
                if (size == 1) {
                    size = u64(d, off + 8);
                    hdr = 16;
                } else if (size == 0) {
                    size = d.length - off;
                }
                if ("ftyp".equals(type)) h.ftypEnd = (int) (off + size);
                if ("meta".equals(type)) {
                    h.metaStart = off;
                    h.metaEnd = (int) (off + size);
                    h.metaVersionFlags = u32(d, off + hdr);
                    if (!h.parseMeta(off + hdr + 4, h.metaEnd)) return null;
                }
                off += size;
            }
            if (h.metaStart == 0 || h.ftypEnd == 0 || h.ftypEnd > h.metaStart) return null;
            return h.primaryWidth > 0 ? h : null;
        }

        boolean parseMeta(int off, int end) {
            byte[] ipco = null;
            while (off + 8 <= end) {
                int size = u32(file, off);
                String type = fourcc(file, off + 4);
                if (size < 8 || off + size > end) return false;
                byte[] box = new byte[size];
                System.arraycopy(file, off, box, 0, size);
                children.add(box);
                childTypes.add(type);
                switch (type) {
                    case "pitm":
                        primaryId = file[off + 8] == 0 ? u16(file, off + 12) : u32(file, off + 12);
                        break;
                    case "iloc":
                        if (!parseIloc(off + 8, off + size)) return false;
                        break;
                    case "iinf":
                        parseIinf(off + 8, off + size);
                        break;
                    case "iprp":
                        for (int p = off + 8; p + 8 <= off + size; ) {
                            int ps = u32(file, p);
                            String pt = fourcc(file, p + 4);
                            if ("ipco".equals(pt)) {
                                ipco = new byte[ps];
                                System.arraycopy(file, p, ipco, 0, ps);
                                for (int q = p + 8; q + 8 <= p + ps; q += u32(file, q)) {
                                    byte[] prop = new byte[u32(file, q)];
                                    System.arraycopy(file, q, prop, 0, prop.length);
                                    properties.add(prop);
                                }
                            } else if ("ipma".equals(pt)) {
                                ipmaVersion = file[p + 8] & 0xFF;
                                ipmaFlags = u32(file, p + 8) & 0xFFFFFF;
                                ipmaCount = u32(file, p + 12);
                                ipmaEntries = new byte[ps - 16];
                                System.arraycopy(file, p + 16, ipmaEntries, 0, ipmaEntries.length);
                            }
                            if (ps < 8) return false;
                            p += ps;
                        }
                        break;
                    case "iref":
                        irefVersion = file[off + 8] & 0xFF;
                        irefBody = new byte[size - 12];
                        System.arraycopy(file, off + 12, irefBody, 0, irefBody.length);
                        break;
                    case "grpl":
                        grplBody = new byte[size - 8];
                        System.arraycopy(file, off + 8, grplBody, 0, grplBody.length);
                        break;
                    default:
                        break;
                }
                off += size;
            }
            if (ipco == null || ipmaEntries == null || primaryId == 0 || ilocItems.isEmpty()) return false;
            // The primary's ispe, from its ipma entry.
            int p = 0;
            for (int i = 0; i < ipmaCount && p < ipmaEntries.length; i++) {
                int id = ipmaVersion < 1 ? u16(ipmaEntries, p) : u32(ipmaEntries, p);
                p += ipmaVersion < 1 ? 2 : 4;
                int n = ipmaEntries[p++] & 0xFF;
                for (int k = 0; k < n; k++) {
                    int index = (ipmaFlags & 1) != 0 ? u16(ipmaEntries, p) & 0x7FFF : ipmaEntries[p] & 0x7F;
                    p += (ipmaFlags & 1) != 0 ? 2 : 1;
                    if (id != primaryId || index < 1 || index > properties.size()) continue;
                    byte[] prop = properties.get(index - 1);
                    if ("ispe".equals(fourcc(prop, 4))) {
                        primaryWidth = u32(prop, 12);
                        primaryHeight = u32(prop, 16);
                        primaryIspeIndex = index;
                    }
                }
            }
            return true;
        }

        boolean parseIloc(int off, int end) {
            ilocVersion = file[off] & 0xFF;
            ilocFlags = u32(file, off) & 0xFFFFFF;
            if (ilocVersion > 2) return false;
            offsetSize = (file[off + 4] >> 4) & 0xF;
            lengthSize = file[off + 4] & 0xF;
            baseOffsetSize = (file[off + 5] >> 4) & 0xF;
            indexSize = ilocVersion > 0 ? file[off + 5] & 0xF : 0;
            int p = off + 6;
            long count;
            if (ilocVersion < 2) {
                count = u16(file, p);
                p += 2;
            } else {
                count = u32(file, p) & 0xFFFFFFFFL;
                p += 4;
            }
            for (long i = 0; i < count; i++) {
                long id;
                if (ilocVersion < 2) {
                    id = u16(file, p);
                    p += 2;
                } else {
                    id = u32(file, p) & 0xFFFFFFFFL;
                    p += 4;
                }
                long construction = 0;
                if (ilocVersion > 0) {
                    construction = u16(file, p) & 0xF;
                    p += 2;
                }
                long dataRef = u16(file, p);
                p += 2;
                long base = readN(file, p, baseOffsetSize);
                p += baseOffsetSize;
                int extents = u16(file, p);
                p += 2;
                long[][] ex = new long[extents][3];
                for (int e = 0; e < extents; e++) {
                    if (indexSize > 0) {
                        ex[e][0] = readN(file, p, indexSize);
                        p += indexSize;
                    }
                    ex[e][1] = readN(file, p, offsetSize);
                    p += offsetSize;
                    ex[e][2] = readN(file, p, lengthSize);
                    p += lengthSize;
                }
                if (p > end) return false;
                ilocItems.add(new long[] {id, construction, dataRef, base, extents});
                ilocExtents.add(ex);
                maxItemId = (int) Math.max(maxItemId, id);
            }
            // New items need room for file offsets and a gain map longer than 64 KiB.
            return (offsetSize == 4 || offsetSize == 8) && (lengthSize == 4 || lengthSize == 8);
        }

        void parseIinf(int off, int end) {
            iinfVersion = file[off] & 0xFF;
            iinfFlags = u32(file, off) & 0xFFFFFF;
            int p = off + 4 + (iinfVersion == 0 ? 2 : 4);
            while (p + 8 <= end) {
                int size = u32(file, p);
                if (size < 8) break;
                byte[] infe = new byte[size];
                System.arraycopy(file, p, infe, 0, size);
                infes.add(infe);
                int version = file[p + 8] & 0xFF;
                if (version >= 2) {
                    int id = version == 2 ? u16(file, p + 12) : u32(file, p + 12);
                    String type = fourcc(file, p + 12 + (version == 2 ? 4 : 6));
                    if ("tmap".equals(type)) hasTmap = true;
                    maxItemId = Math.max(maxItemId, id);
                }
                p += size;
            }
        }

        byte[] addGainMap(Hevc gm, int w, int h, byte[] metadata) {
            int gainId = maxItemId + 1;
            int tmapId = maxItemId + 2;
            int groupId = maxItemId + 3;
            if (tmapId > 0xFFFF || ilocVersion < 2 && tmapId > 0xFFFF) return null;

            // Properties: gain map hvcC, ispe, colr (full range); the tmap item reuses the photo's ispe.
            int hvcIndex = properties.size() + 1;
            properties.add(box("hvcC", gm.hvcC()));
            int gainIspe = properties.size() + 1;
            properties.add(fullBox("ispe", 0, 0, be32(w), be32(h)));
            int gainColr = properties.size() + 1;
            properties.add(box("colr", "nclx".getBytes(StandardCharsets.ISO_8859_1),
                    new byte[] {0, 1, 0, 13, 0, 1, (byte) 0x80}));
            // Three 8-bit channels, for the gain map and the tone-mapped (tmap) image.
            int pixi = properties.size() + 1;
            properties.add(fullBox("pixi", 0, 0, new byte[] {3, 8, 8, 8}));
            // The tone-mapped image's colour: sRGB, as the photo (macOS rejects a tmap without it).
            int tmapColr = properties.size() + 1;
            properties.add(box("colr", "nclx".getBytes(StandardCharsets.ISO_8859_1),
                    new byte[] {0, 1, 0, 13, 0, 6, (byte) 0x80}));
            if (properties.size() > 127 && (ipmaFlags & 1) == 0) return null;

            ByteArrayOutputStream ipma = new ByteArrayOutputStream();
            ipma.write(ipmaEntries, 0, ipmaEntries.length);
            writeIpma(ipma, gainId, new int[] {hvcIndex | 0x8000, gainIspe, gainColr, pixi});
            writeIpma(ipma, tmapId, new int[] {primaryIspeIndex, pixi, tmapColr});

            infes.add(fullBox("infe", 2, 1, be16(gainId), be16(0), "hvc1".getBytes(StandardCharsets.ISO_8859_1), new byte[1]));
            infes.add(fullBox("infe", 2, 0, be16(tmapId), be16(0), "tmap".getBytes(StandardCharsets.ISO_8859_1), new byte[1]));

            ByteArrayOutputStream iref = new ByteArrayOutputStream();
            if (irefBody != null) iref.write(irefBody, 0, irefBody.length);
            boolean wideRef = irefBody != null && irefVersion == 1;
            iref.write(0);
            iref.write(0);
            iref.write(0);
            iref.write(wideRef ? 20 : 14);
            iref.write('d');
            iref.write('i');
            iref.write('m');
            iref.write('g');
            writeId(iref, tmapId, wideRef);
            iref.write(0);
            iref.write(2);
            writeId(iref, primaryId, wideRef);
            writeId(iref, gainId, wideRef);

            ByteArrayOutputStream grpl = new ByteArrayOutputStream();
            if (grplBody != null) grpl.write(grplBody, 0, grplBody.length);
            byte[] altr = fullBox("altr", 0, 0, be32(groupId), be32(2), be32(tmapId), be32(primaryId));
            grpl.write(altr, 0, altr.length);

            // ftyp with the tmap brand.
            int ftypSize = ftypEnd;
            byte[] ftyp = new byte[ftypSize + 4];
            System.arraycopy(file, 0, ftyp, 0, ftypSize);
            System.arraycopy("tmap".getBytes(StandardCharsets.ISO_8859_1), 0, ftyp, ftypSize, 4);
            putU32(ftyp, 0, ftyp.length);

            // Two passes: the iloc size doesn't depend on offsets, so build once to measure.
            byte[] meta = buildMeta(0, 0, gm.frame.length, metadata.length, gainId, tmapId,
                    iref.toByteArray(), grpl.toByteArray(), ipma.toByteArray());
            long shift = (ftyp.length - ftypSize) + ((long) meta.length - (metaEnd - metaStart));
            long tailMdat = file.length + shift + 8;
            meta = buildMeta(shift, tailMdat, gm.frame.length, metadata.length, gainId, tmapId,
                    iref.toByteArray(), grpl.toByteArray(), ipma.toByteArray());

            ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + meta.length + gm.frame.length + 256);
            out.write(ftyp, 0, ftyp.length);
            out.write(file, ftypSize, metaStart - ftypSize);
            out.write(meta, 0, meta.length);
            out.write(file, metaEnd, file.length - metaEnd);
            byte[] mdatHeader = new byte[8];
            putU32(mdatHeader, 0, 8 + gm.frame.length + metadata.length);
            System.arraycopy("mdat".getBytes(StandardCharsets.ISO_8859_1), 0, mdatHeader, 4, 4);
            out.write(mdatHeader, 0, 8);
            out.write(gm.frame, 0, gm.frame.length);
            out.write(metadata, 0, metadata.length);
            return out.toByteArray();
        }

        void writeIpma(ByteArrayOutputStream s, int id, int[] assoc) {
            if (ipmaVersion < 1) {
                s.write(id >>> 8);
                s.write(id);
            } else {
                byte[] b = be32(id);
                s.write(b, 0, 4);
            }
            s.write(assoc.length);
            for (int a : assoc) {
                if ((ipmaFlags & 1) != 0) {
                    s.write(a >>> 8);
                    s.write(a);
                } else {
                    s.write((a & 0x8000) != 0 ? 0x80 | (a & 0x7F) : a & 0x7F);
                }
            }
            ipmaCount++;
        }

        static void writeId(ByteArrayOutputStream s, int id, boolean wide) {
            if (wide) {
                s.write(id >>> 24);
                s.write(id >>> 16);
            }
            s.write(id >>> 8);
            s.write(id);
        }

        byte[] buildMeta(long shift, long tail, int gainLength, int metaLength, int gainId, int tmapId,
                byte[] iref, byte[] grpl, byte[] ipma) {
            ByteArrayOutputStream m = new ByteArrayOutputStream();
            boolean wroteIref = false, wroteGrpl = false;
            for (int i = 0; i < children.size(); i++) {
                String type = childTypes.get(i);
                byte[] b;
                switch (type) {
                    case "iloc":
                        b = iloc(shift, tail, gainLength, metaLength, gainId, tmapId);
                        break;
                    case "iinf":
                        b = iinf();
                        break;
                    case "iprp":
                        b = box("iprp", box("ipco", concat(properties)),
                                fullBox("ipma", ipmaVersion, ipmaFlags, be32(ipmaCount), ipma));
                        break;
                    case "iref":
                        b = fullBox("iref", irefVersion, 0, iref);
                        wroteIref = true;
                        break;
                    case "grpl":
                        b = box("grpl", grpl);
                        wroteGrpl = true;
                        break;
                    default:
                        b = children.get(i);
                }
                m.write(b, 0, b.length);
            }
            if (!wroteIref) {
                byte[] b = fullBox("iref", 0, 0, iref);
                m.write(b, 0, b.length);
            }
            if (!wroteGrpl) {
                byte[] b = box("grpl", grpl);
                m.write(b, 0, b.length);
            }
            byte[] body = m.toByteArray();
            byte[] out = new byte[12 + body.length];
            putU32(out, 0, out.length);
            System.arraycopy("meta".getBytes(StandardCharsets.ISO_8859_1), 0, out, 4, 4);
            putU32(out, 8, metaVersionFlags);
            System.arraycopy(body, 0, out, 12, body.length);
            return out;
        }

        byte[] iinf() {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            int n = infes.size();
            if (iinfVersion == 0) {
                b.write(n >>> 8);
                b.write(n);
            } else {
                byte[] c = be32(n);
                b.write(c, 0, 4);
            }
            for (byte[] e : infes) b.write(e, 0, e.length);
            return fullBox("iinf", iinfVersion, iinfFlags, b.toByteArray());
        }

        byte[] iloc(long shift, long tail, int gainLength, int metaLength, int gainId, int tmapId) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write((offsetSize << 4) | lengthSize);
            b.write((baseOffsetSize << 4) | indexSize);
            int count = ilocItems.size() + 2;
            if (ilocVersion < 2) {
                b.write(count >>> 8);
                b.write(count);
            } else {
                byte[] c = be32(count);
                b.write(c, 0, 4);
            }
            for (int i = 0; i < ilocItems.size(); i++) {
                long[] it = ilocItems.get(i);
                long[][] ex = ilocExtents.get(i);
                // File offsets (construction 0, this file) move with the bytes added before them.
                boolean inFile = it[1] == 0 && it[2] == 0;
                long base = it[3];
                if (inFile && baseOffsetSize > 0 && base > 0) base += shift;
                writeIlocItem(b, it[0], it[1], it[2], base);
                writeN(b, ex.length, 2);
                for (long[] e : ex) {
                    if (indexSize > 0) writeN(b, e[0], indexSize);
                    long offset = e[1];
                    if (inFile && (baseOffsetSize == 0 || it[3] == 0)) offset += shift;
                    writeN(b, offset, offsetSize);
                    writeN(b, e[2], lengthSize);
                }
            }
            for (int k = 0; k < 2; k++) {
                writeIlocItem(b, k == 0 ? gainId : tmapId, 0, 0, 0);
                writeN(b, 1, 2);
                if (indexSize > 0) writeN(b, 0, indexSize);
                writeN(b, k == 0 ? tail : tail + gainLength, offsetSize);
                writeN(b, k == 0 ? gainLength : metaLength, lengthSize);
            }
            return fullBox("iloc", ilocVersion, ilocFlags, b.toByteArray());
        }

        void writeIlocItem(ByteArrayOutputStream b, long id, long construction, long dataRef, long base) {
            writeN(b, id, ilocVersion < 2 ? 2 : 4);
            if (ilocVersion > 0) writeN(b, construction, 2);
            writeN(b, dataRef, 2);
            writeN(b, base, baseOffsetSize);
        }
    }

    // Box helpers.

    static byte[] box(String type, byte[]... parts) {
        byte[] body = concat(java.util.Arrays.asList(parts));
        byte[] out = new byte[8 + body.length];
        putU32(out, 0, out.length);
        System.arraycopy(type.getBytes(StandardCharsets.ISO_8859_1), 0, out, 4, 4);
        System.arraycopy(body, 0, out, 8, body.length);
        return out;
    }

    static byte[] fullBox(String type, int version, int flags, byte[]... parts) {
        byte[][] all = new byte[parts.length + 1][];
        all[0] = be32((version << 24) | (flags & 0xFFFFFF));
        System.arraycopy(parts, 0, all, 1, parts.length);
        return box(type, all);
    }

    static byte[] concat(List<byte[]> parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) b.write(p, 0, p.length);
        return b.toByteArray();
    }

    static byte[] be32(int v) {
        return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    static byte[] be16(int v) {
        return new byte[] {(byte) (v >>> 8), (byte) v};
    }

    static void writeN(ByteArrayOutputStream b, long v, int n) {
        for (int i = n - 1; i >= 0; i--) b.write((int) (v >>> (8 * i)));
    }

    static long readN(byte[] d, int at, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v = (v << 8) | (d[at + i] & 0xFF);
        return v;
    }

    static int u16(byte[] d, int at) {
        return ((d[at] & 0xFF) << 8) | (d[at + 1] & 0xFF);
    }

    static int u32(byte[] d, int at) {
        return ((d[at] & 0xFF) << 24) | ((d[at + 1] & 0xFF) << 16) | ((d[at + 2] & 0xFF) << 8) | (d[at + 3] & 0xFF);
    }

    static long u64(byte[] d, int at) {
        return ((long) u32(d, at) << 32) | (u32(d, at + 4) & 0xFFFFFFFFL);
    }

    static void putU32(byte[] d, int at, int v) {
        d[at] = (byte) (v >>> 24);
        d[at + 1] = (byte) (v >>> 16);
        d[at + 2] = (byte) (v >>> 8);
        d[at + 3] = (byte) v;
    }

    static String fourcc(byte[] d, int at) {
        return new String(d, at, 4, StandardCharsets.ISO_8859_1);
    }
}
