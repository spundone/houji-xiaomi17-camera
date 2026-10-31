package dev.spundone.xcam17;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Xiaomi's own recorder (MiMediaCodecRecorder) asks the video encoder for length-prefixed
 * NAL units instead of Annex-B start codes. HyperOS's MPEG4Writer accepts those; AOSP's
 * doesn't ("csd0 doesn't start with a start code"), so the video track fails and the file
 * comes out with audio only. Stripping the vendor keys before the encoder is configured
 * keeps the encoder on its default Annex-B output, which every muxer understands.
 */
final class AnnexBVideo {
    static final String[] NAL_LENGTH_KEYS = {
            "vendor.qti-ext-enc-nal-length-bs.num-bytes",
            "vendor.x.feature-nal-length-bitstream.value",
            "feature-nal-length-bitstream",
            "nal-length-in-bytes",
            "vendor.mtk.venc.nal.length.prefer",
            "vendor.mtk.venc.nal.length.bytes",
    };

    private AnnexBVideo() {}

    static void hook() {
        try {
            XposedBridge.hookAllMethods(MediaCodec.class, "configure", new StripFromFormat());
            XposedBridge.hookAllMethods(MediaCodec.class, "setParameters", new StripFromParameters());
            Entry.log("annexb: encoders keep start codes");
        } catch (Throwable t) {
            Entry.log("annexb: not hooked: " + t);
        }
    }

    static final class StripFromFormat extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            if (p.args.length == 0 || !(p.args[0] instanceof MediaFormat)) return;
            MediaFormat format = (MediaFormat) p.args[0];
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("video/")) return;
            for (String key : NAL_LENGTH_KEYS) {
                if (format.containsKey(key)) format.removeKey(key);
            }
        }
    }

    static final class StripFromParameters extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam p) {
            if (p.args.length == 0 || !(p.args[0] instanceof Bundle)) return;
            Bundle params = (Bundle) p.args[0];
            for (String key : NAL_LENGTH_KEYS) params.remove(key);
        }
    }
}
