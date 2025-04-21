package org.uvccamera.flutter;

import android.content.Context;
import android.media.MediaCodec;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.input.sources.audio.AudioSource;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.encoder.input.sources.video.VideoSource;
import com.pedro.library.util.streamclient.RtmpStreamClient;
import com.pedro.library.util.streamclient.StreamClientListener;
import com.pedro.rtmp.rtmp.RtmpClient;

import java.nio.ByteBuffer;

/**
* Created by Kenny on 2025/4/17.
*/
public class CustomRtmpStream extends StreamBase {

    private final RtmpClient rtmpClient;

    private final StreamClientListener streamClientListener;

    public CustomRtmpStream(@NonNull Context context,@NonNull ConnectChecker connectChecker) {
        this(context, connectChecker, new Camera2Source(context),new MicrophoneSource());
    }

    public CustomRtmpStream(@NonNull Context context,@NonNull ConnectChecker connectChecker, @NonNull VideoSource vSource, @NonNull AudioSource aSource) {
        super(context, vSource, aSource);

        rtmpClient = new RtmpClient(connectChecker);

        streamClientListener = this::requestKeyframe;
    }



    @Override
    protected void onAudioInfoImp(int sampleRate, boolean isStereo) {
        rtmpClient.setAudioInfo(sampleRate, isStereo);
    }

    @Override
    protected void startStreamImp(@NonNull String endPoint) {
        Size resolution = super.getVideoResolution();
        rtmpClient.setVideoResolution(resolution.getWidth(), resolution.getHeight());
        rtmpClient.setFps(super.getVideoFps());
        rtmpClient.connect(endPoint);
    }

    @Override
    protected void stopStreamImp() {
        rtmpClient.disconnect();
    }

    @Override
    protected void onVideoInfoImp(@NonNull ByteBuffer sps, @Nullable ByteBuffer pps, @Nullable ByteBuffer vps) {
        rtmpClient.setVideoInfo(sps, pps, vps);
    }

    @Override
    protected void getVideoDataImp(@NonNull ByteBuffer videoBuffer, @NonNull MediaCodec.BufferInfo info) {
        rtmpClient.sendVideo(videoBuffer, info);
    }

    @Override
    protected void getAudioDataImp(@NonNull ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        rtmpClient.sendAudio(byteBuffer, bufferInfo);
    }

    @NonNull
    @Override
    public RtmpStreamClient getStreamClient() {
        return new RtmpStreamClient(rtmpClient, streamClientListener);
    }

    @Override
    protected void setVideoCodecImp(@NonNull VideoCodec videoCodec) {
        rtmpClient.setVideoCodec(videoCodec);
    }

    @Override
    protected void setAudioCodecImp(@NonNull AudioCodec audioCodec) {
        rtmpClient.setAudioCodec(audioCodec);
    }


}
