package dev.spundone.xcam17.aosp;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;

/**
 * Port of AnnexBVideo. Xiaomi's recorder asks the video encoder for length-prefixed NAL units,
 * which AOSP's MPEG4Writer rejects ("csd0 doesn't start with a start code"), leaving files with
 * audio only. The patch routes the camera's MediaCodec.configure and setParameters calls
 * through here, which strip those vendor keys so the encoder keeps Annex-B start codes.
 */
public final class AnnexBVideo {
    static final String[] NAL_LENGTH_KEYS = {
            "vendor.qti-ext-enc-nal-length-bs.num-bytes",
            "vendor.x.feature-nal-length-bitstream.value",
            "feature-nal-length-bitstream",
            "nal-length-in-bytes",
            "vendor.mtk.venc.nal.length.prefer",
            "vendor.mtk.venc.nal.length.bytes",
    };

    private AnnexBVideo() {}

    public static void configure(MediaCodec codec, MediaFormat format, Surface surface, MediaCrypto crypto,
            int flags) {
        if (format != null) {
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                for (String key : NAL_LENGTH_KEYS) {
                    if (format.containsKey(key)) format.removeKey(key);
                }
            }
        }
        codec.configure(format, surface, crypto, flags);
    }

    public static void setParameters(MediaCodec codec, Bundle params) {
        if (params != null) {
            for (String key : NAL_LENGTH_KEYS) params.remove(key);
        }
        codec.setParameters(params);
    }
}
