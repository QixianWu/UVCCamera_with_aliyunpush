/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uvccamera.flutter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import androidx.annotation.NonNull;

import com.pedro.common.AudioCodec;
import com.pedro.common.TimeUtils;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.EncoderErrorCallback;
import com.pedro.encoder.Frame;
import com.pedro.encoder.TimestampMode;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAudioData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.sources.audio.AudioSource;
import com.pedro.encoder.input.sources.audio.NoAudioSource;
import com.pedro.encoder.input.sources.video.NoVideoSource;
import com.pedro.encoder.input.sources.video.VideoSource;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.library.base.recording.BaseRecordController;
import com.pedro.library.base.recording.RecordController;
import com.pedro.library.util.AndroidMuxerRecordController;
import com.pedro.library.util.FpsListener;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.library.view.GlStreamInterface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Math.max;

/**
 * Created by pedro on 21/2/22.
 * Allow:
 * - video source camera1, camera2 or screen.
 * - audio source microphone or internal.
 * - Rotation on realtime.
 */
public abstract class StreamBase {

    private final GetMicrophoneData getMicrophoneData = new GetMicrophoneData() {
        @Override
        public void inputPCMData(@NonNull Frame frame) {
            audioEncoder.inputPCMData(frame);
        }
    };

    // video and audio encoders
    private final VideoEncoder videoEncoder;
    private final VideoEncoder videoEncoderRecord;
    private final AudioEncoder audioEncoder;

    // video render
    private final GlStreamInterface glInterface;

    // video/audio record
    private BaseRecordController recordController = new AndroidMuxerRecordController();
    private final FpsListener fpsListener = new FpsListener();

    private boolean isStreaming = false;
    private boolean isOnPreview = false;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private boolean differentRecordResolution = false;

    public StreamBase(Context context, VideoSource vSource, AudioSource aSource) {
        this.videoSource = vSource;
        this.audioSource = aSource;
        this.glInterface = new GlStreamInterface(context);

        this.videoEncoder = new VideoEncoder(getVideoData);
        this.videoEncoderRecord = new VideoEncoder(getVideoDataRecord);
        this.audioEncoder = new AudioEncoder(getAacData);
    }

    /**
     * Necessary only one time before start preview, stream or record.
     * If you want change values stop preview, stream and record is necessary.
     *
     * @param profile codec value from MediaCodecInfo.CodecProfileLevel class
     * @param level codec value from MediaCodecInfo.CodecProfileLevel class
     *
     * @throws IllegalArgumentException if current video parameters are not supported by the VideoSource
     * @throws IllegalArgumentException if you use differentRecordResolution but the aspect ratio is not the same than stream resolution
     * @return True if success, False if failed
     */
    public boolean prepareVideo(int width, int height, int bitrate, int fps, int iFrameInterval,
                               int rotation, int profile, int level,
                               int recordWidth, int recordHeight, int recordBitrate) throws IllegalArgumentException {
        if (isStreaming || isRecording() || isOnPreview) {
            throw new IllegalStateException("Stream, record and preview must be stopped before prepareVideo");
        }
        differentRecordResolution = false;
        if (recordWidth > 0 && recordHeight > 0) {
            if ((double) recordWidth / recordHeight != (double) width / height) {
                throw new IllegalArgumentException("The aspect ratio of record and stream resolution must be the same");
            }
            differentRecordResolution = true;
        }
        boolean videoResult = videoSource.init(max(width, recordWidth), max(height, recordHeight), fps, rotation);
        if (videoResult) {
            if (differentRecordResolution) {
                // using different record resolution
                if (rotation == 90 || rotation == 270) {
                    glInterface.setEncoderRecordSize(recordHeight, recordWidth);
                } else {
                    glInterface.setEncoderRecordSize(recordWidth, recordHeight);
                }
            }
            if (rotation == 90 || rotation == 270) {
                glInterface.setEncoderSize(height, width);
            } else {
                glInterface.setEncoderSize(width, height);
            }
            boolean isPortrait = rotation == 90 || rotation == 270;
            glInterface.setIsPortrait(isPortrait);
            glInterface.setCameraOrientation(rotation == 0 ? 270 : rotation - 90);
            glInterface.forceOrientation(videoSource.getOrientationConfig());
            if (differentRecordResolution) {
                boolean result = videoEncoderRecord.prepareVideoEncoder(recordWidth, recordHeight, fps, recordBitrate, rotation,
                        iFrameInterval, FormatVideoEncoder.SURFACE, profile, level);
                if (!result) return false;
            }
            boolean result = videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation,
                    iFrameInterval, FormatVideoEncoder.SURFACE, profile, level);
            forceFpsLimit(true);
            return result;
        }
        return false;
    }

    // 重载方法，提供默认参数
    public boolean prepareVideo(int width, int height, int bitrate) throws IllegalArgumentException {
        return prepareVideo(width, height, bitrate, 30, 2, 0, -1, -1, 0, 0, bitrate);
    }

    public boolean prepareVideo(int width, int height, int bitrate, int fps) throws IllegalArgumentException {
        return prepareVideo(width, height, bitrate, fps, 2, 0, -1, -1, 0, 0, bitrate);
    }

    public boolean prepareVideo(int width, int height, int bitrate, int fps, int iFrameInterval) throws IllegalArgumentException {
        return prepareVideo(width, height, bitrate, fps, iFrameInterval, 0, -1, -1, 0, 0, bitrate);
    }

    /**
     * Necessary only one time before start stream or record.
     * If you want change values stop stream and record is necessary.
     *
     * @throws IllegalArgumentException if current video parameters are not supported by the AudioSource
     * @return True if success, False if failed
     */
    public boolean prepareAudio(int sampleRate, boolean isStereo, int bitrate, boolean echoCanceler,
                               boolean noiseSuppressor) throws IllegalArgumentException {
        if (isStreaming || isRecording()) {
            throw new IllegalStateException("Stream and record must be stopped before prepareAudio");
        }
        boolean audioResult = audioSource.init(sampleRate, isStereo, echoCanceler, noiseSuppressor);
        if (audioResult) {
            onAudioInfoImp(sampleRate, isStereo);
            return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
        }
        return false;
    }

    // 重载方法，提供默认参数
    public boolean prepareAudio(int sampleRate, boolean isStereo, int bitrate) throws IllegalArgumentException {
        return prepareAudio(sampleRate, isStereo, bitrate, false, false);
    }

    /**
     * Start stream.
     *
     * Must be called after prepareVideo and prepareAudio
     */
    public void startStream(String endPoint) {
        if (isStreaming) throw new IllegalStateException("Stream already started, stopStream before startStream again");
        isStreaming = true;
        startStreamImp(endPoint);
        if (!isRecording()) {
            startSources();
        } else {
            requestKeyframe();
        }
    }

    /**
     * Force VideoEncoder to produce a keyframe. Ignored if not recording or streaming.
     * This could be ignored depend of the Codec implementation in each device.
     */
    public void requestKeyframe() {
        if (videoEncoder.isRunning()) {
            videoEncoder.requestKeyframe();
        }
        if (videoEncoderRecord.isRunning()) {
            videoEncoderRecord.requestKeyframe();
        }
    }

    /**
     * Set video bitrate in bits per second while streaming.
     *
     * @param bitrate in bits per second.
     */
    public void setVideoBitrateOnFly(int bitrate) {
        videoEncoder.setVideoBitrateOnFly(bitrate);
    }

    /**
     * Force stream to work with fps selected in prepareVideo method. Must be called before prepareVideo.
     * Must be called after prepareVideo
     *
     * @param enabled true to enabled, false to disable, enabled by default.
     */
    public void forceFpsLimit(boolean enabled) {
        int fps = enabled ? videoEncoder.getFps() : 0;
        videoEncoder.setForceFps(fps);
        videoEncoderRecord.setForceFps(fps);
        glInterface.forceFpsLimit(fps);
    }

    /**
     * @param codecTypeVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param codecTypeAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    public void forceCodecType(CodecUtil.CodecType codecTypeVideo, CodecUtil.CodecType codecTypeAudio) {
        videoEncoder.forceCodecType(codecTypeVideo);
        videoEncoderRecord.forceCodecType(codecTypeVideo);
        audioEncoder.forceCodecType(codecTypeAudio);
    }

    /**
     * Stop stream.
     *
     * @return True if encoders prepared successfully with previous parameters. False other way
     * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
     *
     * Must be called after prepareVideo and prepareAudio.
     */
    public boolean stopStream() {
        isStreaming = false;
        stopStreamImp();
        if (!isRecording()) {
            stopSources();
            return prepareEncoders();
        }
        return true;
    }

    /**
     * Start record.
     *
     * Must be called after prepareVideo and prepareAudio.
     */
    public void startRecord(String path, RecordController.Listener listener) throws IOException {
        if (isRecording()) throw new IllegalStateException("Record already started, stopRecord before startRecord again");
        recordController.startRecord(path, listener);
        if (!isStreaming) {
            startSources();
        } else {
            videoEncoder.requestKeyframe();
            videoEncoderRecord.requestKeyframe();
        }
    }

    /**
     * @return True if encoders prepared successfully with previous parameters. False other way
     * If return is false you will need call prepareVideo and prepareAudio manually again before startStream or StartRecord
     *
     * Must be called after prepareVideo and prepareAudio.
     */
    public boolean stopRecord() {
        recordController.stopRecord();
        if (!isStreaming) {
            stopSources();
            return prepareEncoders();
        }
        return true;
    }

    /**
     * Pause record. Ignored if you are not recording.
     */
    public void pauseRecord() {
        recordController.pauseRecord();
    }

    /**
     * Resume record. Ignored if you are not recording and in pause mode.
     */
    public void resumeRecord() {
        recordController.resumeRecord();
    }

    /**
     * Start preview in the selected TextureView.
     * Must be called after prepareVideo.
     */
    public void startPreview(TextureView textureView, boolean autoHandle) {
        if (autoHandle) {
            textureView.setSurfaceTextureListener(new SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
                    if (!isOnPreview) startPreview(textureView);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
                    getGlInterface().setPreviewResolution(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
                    if (isOnPreview) stopPreview();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {
                }
            });
            if (textureView.isAvailable() && !isOnPreview) startPreview(textureView);
        } else {
            startPreview(new Surface(textureView.getSurfaceTexture()), textureView.getWidth(), textureView.getHeight());
        }
    }

    // 重载方法，提供默认参数
    public void startPreview(TextureView textureView) {
        startPreview(textureView, false);
    }

    /**
     * Start preview in the selected SurfaceView.
     * Must be called after prepareVideo.
     */
    public void startPreview(SurfaceView surfaceView, boolean autoHandle) {
        if (autoHandle) {
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    if (!isOnPreview) startPreview(surfaceView);
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    getGlInterface().setPreviewResolution(width, height);
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    if (isOnPreview) stopPreview();
                }
            });
            if (surfaceView.getHolder().getSurface().isValid() && !isOnPreview) startPreview(surfaceView);
        } else {
            startPreview(surfaceView.getHolder().getSurface(), surfaceView.getWidth(), surfaceView.getHeight());
        }
    }

    // 重载方法，提供默认参数
    public void startPreview(SurfaceView surfaceView) {
        startPreview(surfaceView, false);
    }

    /**
     * Start preview in the selected SurfaceTexture.
     * Must be called after prepareVideo.
     */
    public void startPreview(SurfaceTexture surfaceTexture, int width, int height) {
        startPreview(new Surface(surfaceTexture), width, height);
    }

    /**
     * Start preview in the selected Surface.
     * Must be called after prepareVideo.
     */
    public void startPreview(Surface surface, int width, int height) {
        if (!surface.isValid()) throw new IllegalArgumentException("Make sure the Surface is valid");
        if (isOnPreview) throw new IllegalStateException("Preview already started, stopPreview before startPreview again");
        isOnPreview = true;
        if (!glInterface.isRunning()) glInterface.start();
        if (!videoSource.isRunning()) {
            videoSource.start(glInterface.getSurfaceTexture());
        }
        glInterface.attachPreview(surface);
        glInterface.setPreviewResolution(width, height);
    }

    /**
     * Stop preview.
     * Must be called after prepareVideo.
     */
    public void stopPreview() {
        isOnPreview = false;
        if (!isStreaming && !isRecording()) videoSource.stop();
        glInterface.deAttachPreview();
        if (!isStreaming && !isRecording()) glInterface.stop();
    }

    /**
     * Change video source to Camera1 or Camera2.
     * Must be called after prepareVideo.
     *
     * @throws IllegalArgumentException if current video parameters are not supported by the VideoSource
     */
    public void changeVideoSource(VideoSource source) throws IllegalArgumentException {
        boolean wasRunning = videoSource.isRunning();
        boolean wasCreated = videoSource.getCreated();
        if (wasCreated) {
            int width = videoEncoder.getWidth();
            int height = videoEncoder.getHeight();
            if (differentRecordResolution) {
                width = max(width, videoEncoderRecord.getWidth());
                height = max(height, videoEncoderRecord.getHeight());
            }
            source.init(width, height, videoEncoder.getFps(), videoEncoder.getRotation());
        }
        videoSource.stop();
        videoSource.release();
        if (wasRunning) source.start(glInterface.getSurfaceTexture());
        glInterface.forceOrientation(source.getOrientationConfig());
        videoSource = source;
    }

    /**
     * Change audio source.
     * Must be called after prepareAudio.
     *
     * @throws IllegalArgumentException if current video parameters are not supported by the AudioSource
     */
    public void changeAudioSource(AudioSource source) throws IllegalArgumentException {
        boolean wasRunning = audioSource.isRunning();
        boolean wasCreated = audioSource.getCreated();
        if (wasCreated) source.init(audioSource.getSampleRate(), audioSource.isStereo(), audioSource.getEchoCanceler(), audioSource.getNoiseSuppressor());
        audioSource.stop();
        audioSource.release();
        if (wasRunning) source.start(getMicrophoneData);
        audioSource = source;
    }

    /**
     * Set the mode to calculate timestamp. By default CLOCK.
     * Must be called before startRecord/startStream or it will be ignored.
     */
    public void setTimestampMode(TimestampMode timestampModeVideo, TimestampMode timestampModeAudio) {
        videoEncoder.setTimestampMode(timestampModeVideo);
        videoEncoderRecord.setTimestampMode(timestampModeVideo);
        audioEncoder.setTimestampMode(timestampModeAudio);
    }

    /**
     * Set a callback to know errors related with Video/Audio encoders
     * @param encoderErrorCallback callback to use, null to remove
     */
    public void setEncoderErrorCallback(EncoderErrorCallback encoderErrorCallback) {
        videoEncoder.setEncoderErrorCallback(encoderErrorCallback);
        videoEncoderRecord.setEncoderErrorCallback(encoderErrorCallback);
        audioEncoder.setEncoderErrorCallback(encoderErrorCallback);
    }

    /**
     * @param callback get fps while record or stream
     */
    public void setFpsListener(FpsListener.Callback callback) {
        fpsListener.setCallback(callback);
    }

    /**
     * Change stream orientation depend of activity orientation.
     * This method affect to preview and stream.
     * Must be called after prepareVideo.
     */
    public void setOrientation(int orientation) {
        glInterface.setCameraOrientation(orientation);
    }

    /**
     * Get glInterface used to render video.
     * This is useful to send filters to stream.
     * Must be called after prepareVideo.
     */
    public GlStreamInterface getGlInterface() {
        return glInterface;
    }

    /**
     * Replace the current BaseRecordController.
     * This method allow record in other format or even create your custom implementation and record in a new format.
     */
    public void setRecordController(BaseRecordController recordController) {
        if (!isRecording()) this.recordController = recordController;
    }

    /**
     * return surface texture that can be used to render and encode custom data. Return null if video not prepared.
     * start and stop rendering must be managed by the user.
     */
    public SurfaceTexture getSurfaceTexture() {
        if (!(videoSource instanceof NoVideoSource)) {
            throw new IllegalStateException("getSurfaceTexture only available with VideoManager.Source.DISABLED");
        }
        return glInterface.getSurfaceTexture();
    }

    protected Size getVideoResolution() {
        return new Size(videoEncoder.getWidth(), videoEncoder.getHeight());
    }

    protected int getVideoFps() {
        return videoEncoder.getFps();
    }

    private void startSources() {
        if (!glInterface.isRunning()) glInterface.start();
        if (!videoSource.isRunning()) {
            videoSource.start(glInterface.getSurfaceTexture());
        }
        audioSource.start(getMicrophoneData);
        long startTs = TimeUtils.getCurrentTimeMicro();
        videoEncoder.start(startTs);
        if (differentRecordResolution) videoEncoderRecord.start(startTs);
        audioEncoder.start(startTs);
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        if (differentRecordResolution) glInterface.addMediaCodecRecordSurface(videoEncoderRecord.getInputSurface());
    }

    private void stopSources() {
        if (!isOnPreview) videoSource.stop();
        audioSource.stop();
        glInterface.removeMediaCodecSurface();
        glInterface.removeMediaCodecRecordSurface();
        if (!isOnPreview) glInterface.stop();
        videoEncoder.stop();
        videoEncoderRecord.stop();
        audioEncoder.stop();
        if (!isRecording()) recordController.resetFormats();
    }

    /**
     * Stop stream, record and preview and then release all resources.
     * You must call it after finish all the work.
     */
    public void release() {
        if (isStreaming) stopStream();
        if (isRecording()) stopRecord();
        if (isOnPreview) stopPreview();
        stopSources();
        videoSource.release();
        audioSource.release();
    }

    /**
     * Reset VideoEncoder. Only recommended if a VideoEncoder class error is received in the EncoderErrorCallback
     *
     * @return true if success, false if failed
     */
    public boolean resetVideoEncoder() {
        if (differentRecordResolution) {
            glInterface.removeMediaCodecRecordSurface();
            boolean result = videoEncoderRecord.reset();
            if (!result) return false;
            glInterface.addMediaCodecRecordSurface(videoEncoderRecord.getInputSurface());
        }
        glInterface.removeMediaCodecSurface();
        boolean result = videoEncoder.reset();
        if (!result) return false;
        glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
        return true;
    }

    /**
     * Reset AudioEncoder. Only recommended if an AudioEncoder class error is received in the EncoderErrorCallback
     *
     * @return true if success, false if failed
     */
    public boolean resetAudioEncoder() {
        return audioEncoder.reset();
    }

    private boolean prepareEncoders() {
        if (differentRecordResolution) {
            boolean result = videoEncoderRecord.prepareVideoEncoder();
            if (!result) return false;
        }
        return videoEncoder.prepareVideoEncoder() && audioEncoder.prepareAudioEncoder();
    }

    private final GetAudioData getAacData = new GetAudioData() {
        @Override
        public void getAudioData(@NonNull ByteBuffer audioBuffer, @NonNull MediaCodec.BufferInfo info) {
            getAudioDataImp(audioBuffer, info);
            recordController.recordAudio(audioBuffer, info);
        }

        @Override
        public void onAudioFormat(@NonNull MediaFormat mediaFormat) {
            boolean isOnlyAudio = videoSource instanceof NoVideoSource;
            recordController.setAudioFormat(mediaFormat, isOnlyAudio);
        }
    };

    private final GetVideoData getVideoData = new GetVideoData() {
        @Override
        public void onVideoInfo(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
            onVideoInfoImp(sps.duplicate(), pps != null ? pps.duplicate() : null, vps != null ? vps.duplicate() : null);
        }

        @Override
        public void getVideoData(@NonNull ByteBuffer videoBuffer, @NonNull MediaCodec.BufferInfo info) {
            fpsListener.calculateFps();
            getVideoDataImp(videoBuffer, info);
            if (!differentRecordResolution) recordController.recordVideo(videoBuffer, info);
        }

        @Override
        public void onVideoFormat(@NonNull MediaFormat mediaFormat) {
            if (!differentRecordResolution) {
                boolean isOnlyVideo = audioSource instanceof NoAudioSource;
                recordController.setVideoFormat(mediaFormat, isOnlyVideo);
            }
        }
    };

    private final GetVideoData getVideoDataRecord = new GetVideoData() {
        @Override
        public void onVideoInfo(@NonNull ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        }

        @Override
        public void getVideoData(@NonNull ByteBuffer videoBuffer, MediaCodec.BufferInfo info) {
            recordController.recordVideo(videoBuffer, info);
        }

        @Override
        public void onVideoFormat(@NonNull MediaFormat mediaFormat) {
            boolean isOnlyVideo = audioSource instanceof NoAudioSource;
            recordController.setVideoFormat(mediaFormat, isOnlyVideo);
        }
    };

    public boolean isStreaming() {
        return isStreaming;
    }

    public boolean isOnPreview() {
        return isOnPreview;
    }

    public boolean isRecording() {
        return recordController.isRunning();
    }

    protected abstract void onAudioInfoImp(int sampleRate, boolean isStereo);
    protected abstract void startStreamImp(String endPoint);
    protected abstract void stopStreamImp();
    protected abstract void onVideoInfoImp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);
    protected abstract void getVideoDataImp(ByteBuffer videoBuffer, MediaCodec.BufferInfo info);
    protected abstract void getAudioDataImp(ByteBuffer audioBuffer, MediaCodec.BufferInfo info);

    public abstract StreamBaseClient getStreamClient();

    /**
     * Change VideoCodec used.
     * This could fail depend of the Codec supported in each Protocol. For example AV1 is not supported in SRT
     */
    public void setVideoCodec(VideoCodec codec) {
        setVideoCodecImp(codec);
        recordController.setVideoCodec(codec);
        String type;
        switch (codec) {
            case H264:
                type = CodecUtil.H264_MIME;
                break;
            case H265:
                type = CodecUtil.H265_MIME;
                break;
            case AV1:
                type = CodecUtil.AV1_MIME;
                break;
            default:
                type = CodecUtil.H264_MIME;
        }
        videoEncoder.setType(type);
        videoEncoderRecord.setType(type);
    }

    /**
     * Change AudioCodec used.
     * This could fail depend of the Codec supported in each Protocol. For example G711 is not supported in SRT
     */
    public void setAudioCodec(AudioCodec codec) {
        setAudioCodecImp(codec);
        recordController.setAudioCodec(codec);
        String type;
        switch (codec) {
            case G711:
                type = CodecUtil.G711_MIME;
                break;
            case AAC:
                type = CodecUtil.AAC_MIME;
                break;
            case OPUS:
                type = CodecUtil.OPUS_MIME;
                break;
            default:
                type = CodecUtil.AAC_MIME;
        }
        audioEncoder.setType(type);
    }

    protected abstract void setVideoCodecImp(VideoCodec codec);
    protected abstract void setAudioCodecImp(AudioCodec codec);
}
