package dev.spundone.xcam17.aosp;

/**
 * Port of Entry.hookVideoLensSwitching and hookFrontDolby4k.
 *
 * Multi-lens video is allowed per quality and HDR state by the HAL mask
 * com.xiaomi.videosat.supportedRange (0x8F8F on houji), checked by data.i.U1(mode, quality).
 * With Dolby Vision or HDR10 on, U1 first asks those components, which bail out at 4K30 on
 * houji; inside U1 they're answered so U1 reaches the mask. 4K60 ("8,60") is allowed as well.
 */
public final class VideoLens {
    /** Depth of U1 calls on this thread, so the HDR answers only change inside it. */
    private static final ThreadLocal<int[]> IN_SAT_CHECK = ThreadLocal.withInitial(() -> new int[1]);

    private VideoLens() {}

    public static void satEnter() {
        IN_SAT_CHECK.get()[0]++;
    }

    public static void satExit() {
        IN_SAT_CHECK.get()[0]--;
    }

    public static boolean satResult(boolean allowed, String quality) {
        return allowed || "8,60".equals(quality);
    }

    /** HDR10 and HDR10 Pro "is on" checks: off while U1 runs. */
    public static boolean hdrInsideSatCheck(boolean on) {
        return on && IN_SAT_CHECK.get()[0] == 0;
    }

    /** Dolby's "SAT allowed at this quality" (type, quality): yes for type 2 while U1 runs. */
    public static boolean dolbyInsideSatCheck(boolean allowed, int type) {
        return allowed || (IN_SAT_CHECK.get()[0] > 0 && type == 2);
    }

    /** Dolby Vision config (type, quality, fps, caps): adds front 4K30 (type 3, quality 8, 30 fps). */
    public static boolean dolby4k30(boolean allowed, int type, int quality, int fps) {
        return allowed || (type == 3 && quality == 8 && fps == 30);
    }
}
