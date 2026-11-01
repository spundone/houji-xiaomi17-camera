package Sh;

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

/**
 * Stand-in for the photo task class of camera 6.8.001960.0, as the Leica port's processor
 * reads it: the two fields the port added, the result holder and the total result. Its
 * class loader (dev.spundone.xcam17.LeicaLegendary) resolves Sh.t to this class, so the
 * names and members must stay exactly as the port's code refers to them.
 */
public final class t {
    public String legendaryStyle;
    public CaptureResult legendaryPreviewResult;
    public h f;
    public TotalCaptureResult total;

    public TotalCaptureResult Q() {
        return total;
    }
}
