package dev.spundone.xcam17.aosp;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import java.nio.charset.StandardCharsets;

/**
 * Port of Entry.hookClientName. Qualcomm's camera HAL on houji reads the vendor tag
 * com.xiaomi.sessionparams.clientName from capture requests and passes it straight to
 * strcmp(); with nothing set it crashes in CamX::SensorNode::ExecuteProcessRequest. The patch
 * routes every CameraDevice.create*CaptureRequest call in the camera through here, which
 * stamps the package name on the new builder.
 */
public final class ClientName {
    static final CaptureRequest.Key<byte[]> CLIENT_NAME =
            new CaptureRequest.Key<>("com.xiaomi.sessionparams.clientName", byte[].class);
    // NUL-terminated, as the HAL reads it as a C string.
    static final byte[] VALUE = ("com.android.camera" + "\0").getBytes(StandardCharsets.US_ASCII);

    private ClientName() {}

    public static CaptureRequest.Builder createCaptureRequest(CameraDevice device, int template)
            throws CameraAccessException {
        return stamp(device.createCaptureRequest(template));
    }

    public static CaptureRequest.Builder createReprocessCaptureRequest(CameraDevice device,
            TotalCaptureResult result) throws CameraAccessException {
        return stamp(device.createReprocessCaptureRequest(result));
    }

    static CaptureRequest.Builder stamp(CaptureRequest.Builder builder) {
        if (builder == null) return null;
        try {
            builder.set(CLIENT_NAME, VALUE);
        } catch (IllegalArgumentException e) {
            // The HAL doesn't define the tag (another device or ROM): nothing to fix.
            Log.once("client name: tag not defined by this HAL: " + e.getMessage());
        }
        return builder;
    }
}
