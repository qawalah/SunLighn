package dev.qawalah.sunlighn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

/**
 * The back camera's live picture, behind the viewfinder's scene in AR (3.5). Plain Camera2, no libraries. It fills
 * its view (centre-crop) and says the lens that matches what the screen shows, so the drawn sky lies on the real one.
 * Without a camera (or in tests) it stays dark and says nothing.
 */
final class CameraFeed extends TextureView implements TextureView.SurfaceTextureListener {
    interface Listener {
        /** The 35 mm-equivalent focal length of what the screen shows, held upright (portrait). */
        void onLens(double focalMm);
    }

    private final Listener listener;
    private CameraDevice device;
    private CameraCaptureSession session;
    private Size preview;
    private double eqFocal = 26;           // a phone's main camera, until the camera says
    private boolean closed;

    CameraFeed(Context c, Listener l) {
        super(c);
        listener = l;
        setContentDescription("Camera");
        setSurfaceTextureListener(this);
    }

    void close() {
        closed = true;
        try {
            if (session != null) session.close();
            if (device != null) device.close();
        } catch (RuntimeException ignored) {
        }
        session = null;
        device = null;
    }

    @Override protected void onDetachedFromWindow() {
        close();
        super.onDetachedFromWindow();
    }

    @SuppressLint("MissingPermission")
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return;
            String id = null;
            for (String c : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(c).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { id = c; break; }
            }
            if (id == null) return;
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            preview = choose(map == null ? null : map.getOutputSizes(SurfaceTexture.class));
            float[] focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focals != null && focals.length > 0 && sensor != null) {
                float longSide = Math.max(sensor.getWidth(), sensor.getHeight());
                eqFocal = focals[0] * 36.0 / longSide;          // full frame: 36 mm on the long side
            }
            fit(w, h);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    if (closed) { d.close(); return; }
                    device = d;
                    start(st);
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); }
                @Override public void onError(CameraDevice d, int e) { d.close(); }
            }, null);
        } catch (Exception ignored) {          // no camera, or no permission: the drawn scene stays
        }
    }

    /** A preview size near 1280 on the long side, 4:3 if there is one (most phones' sensors). */
    private static Size choose(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 960);
        Size best = sizes[0];
        for (Size s : sizes) {
            boolean fourThree = s.getWidth() * 3 == s.getHeight() * 4;
            boolean bestFourThree = best.getWidth() * 3 == best.getHeight() * 4;
            int d = Math.abs(s.getWidth() - 1280), bd = Math.abs(best.getWidth() - 1280);
            if ((fourThree && !bestFourThree) || (fourThree == bestFourThree && d < bd)) best = s;
        }
        return best;
    }

    private void start(SurfaceTexture st) {
        try {
            st.setDefaultBufferSize(preview.getWidth(), preview.getHeight());
            Surface surface = new Surface(st);
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(surface);
            device.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (closed || device == null) { s.close(); return; }
                    session = s;
                    try {
                        s.setRepeatingRequest(b.build(), null, null);
                    } catch (Exception ignored) {
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {}
            }, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Centre-crop the preview (held upright: the sensor's long side runs down the screen) and say the lens of what
     * shows: a crop shows less than the whole picture, which is a longer lens.
     */
    private void fit(int w, int h) {
        if (preview == null || w == 0 || h == 0) return;
        float pw = Math.min(preview.getWidth(), preview.getHeight()), ph = Math.max(preview.getWidth(), preview.getHeight());
        float scale = Math.max(w / pw, h / ph);
        Matrix m = new Matrix();
        // TextureView stretches the buffer to the view; undo that and centre-crop instead
        m.setScale(pw * scale / w, ph * scale / h, w / 2f, h / 2f);
        setTransform(m);
        double shownWidth = w / (pw * scale);                // the share of the picture's width on screen
        // The viewfinder held upright is 24 mm wide in full-frame terms; the camera's short side is 36 x pw/ph mm
        double upright = eqFocal * 24.0 / (36.0 * pw / ph);
        if (listener != null) listener.onLens(upright / Math.max(0.2, shownWidth));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { fit(w, h); }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        close();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
}
