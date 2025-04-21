package org.uvccamera.flutter;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.pedro.encoder.input.sources.video.VideoSource;
import com.serenegiant.usb.UVCCamera;

/**
 * Created by Kenny on 2025/4/14.
 */
public class CameraUvcSource extends VideoSource {

    private final UVCCamera uvcCamera;

    private boolean running = false;
    private Surface surface;

    public CameraUvcSource(UVCCamera uvcCamera) {
        this.uvcCamera = uvcCamera;
    }

    @Override
    protected boolean create(int width, int height, int fps, int rotation) {
        return true;
    }

    @Override
    public void start(@NonNull SurfaceTexture surfaceTexture) {
        setSurfaceTexture(surfaceTexture);
        surface = new Surface(surfaceTexture);
        running = true;
        uvcCamera.startCapture(surface);
    }

    @Override
    public void stop() {
        if (uvcCamera != null) {
            uvcCamera.stopCapture();
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }

        running = false;
    }

    @Override
    public void release() {

    }

    @Override
    public boolean isRunning() {
        return running;
    }


}
