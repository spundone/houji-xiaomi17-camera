package android.media;

import android.content.Context;

/**
 * Stand-in for HyperOS's audio parameter manager (the spelling is Xiaomi's), which the
 * camera's Movie recorder (MiCinematicRecorder) refers to. AOSP has no such class, so Movie's
 * own recorder failed to load. Nothing here does anything: every effect reports itself as not
 * created and every query returns nothing, which the camera already handles as "no AI audio".
 * The camera module serves this class to the camera when its own class loader can't find it.
 */
public class AudioParaManger {
    public interface EventListener {}

    public interface TuneListener {}

    public AudioParaManger(AudioRecord record, Context context) {}

    public AudioParaManger(MediaRecorder recorder, Context context) {}

    public boolean creatOzoEffect(int type) {
        return false;
    }

    public boolean createAudioEffects(int type) {
        return false;
    }

    public void createAudioObject(EventListener events, TuneListener tune) {}

    public void createOzo(EventListener events) {}

    public void createOzo(EventListener events, TuneListener tune) {}

    public int[] getAudioConfig() {
        return null;
    }

    public boolean init(int a, int b, int c, double d, double e, double f, double g, double h, boolean i) {
        return false;
    }

    public void prepare() {}

    public void releaseAudioEffect() {}

    public void releaseOzoEffect() {}

    public void setAudioFocusAzimuth(double azimuth) {}

    public void setAudioFocusAzimuth(double azimuth, int type) {}

    public void setAudioFocusElevation(double elevation) {}

    public void setAudioFocusElevation(double elevation, int type) {}

    public void setAudioFocusHeight(double height) {}

    public void setAudioFocusWidth(double width) {}

    public void setAudioWindNoise(boolean on) {}

    public void setAudioZoomLevel(double level) {}

    public int setFocusRegion(int a, int b, int c, int d, int e) {
        return 0;
    }

    public void setMaxSupportLevel(int level) {}

    public void setRecordType(int type) {}

    public void setUserGain(double gain) {}

    public void setUserMode(boolean on) {}

    public int setViewRegion(int a, int b, int c, int d, int e) {
        return 0;
    }

    public void start() {}
}
