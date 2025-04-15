package org.uvccamera.flutter;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.pedro.common.ConnectChecker;
import com.pedro.library.rtmp.RtmpStream;
import com.pedro.library.util.BitrateAdapter;
import com.pedro.library.util.FpsListener;

/**
 * Created by Kenny on 2025/4/14.
 */
public class RtmpConnectChecker implements ConnectChecker, FpsListener.Callback {

    private RtmpStream genericStream;

    private RtmpEventStreamHandler rtmpEventStreamHandler;

    private final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    private final BitrateAdapter bitrateAdapter = new BitrateAdapter(bitrate -> {
        // 处理 bitrate 变化
        genericStream.setVideoBitrateOnFly(bitrate);

        // 发送比特率变化事件
        if (rtmpEventStreamHandler != null) {
            rtmpEventStreamHandler.sendBitrateEvent(bitrate);
        }
    });

    @Override
    public void onNewBitrate(long bitrate) {
        bitrateAdapter.adaptBitrate(bitrate, genericStream.getStreamClient().hasCongestion());
    }

    @Override
    public void onConnectionStarted(@NonNull String s) {
        if (rtmpEventStreamHandler != null) {
            rtmpEventStreamHandler.sendConnectionEvent("onConnectionStarted", s);
        }
    }

    @Override
    public void onConnectionSuccess() {
        if (rtmpEventStreamHandler != null) {
            rtmpEventStreamHandler.sendConnectionEvent("onConnectionSuccess", null);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull String s) {
        if (genericStream.getStreamClient().reTry(5000, s, null)) {
            if (rtmpEventStreamHandler != null) {
                rtmpEventStreamHandler.sendConnectionEvent("onConnectionRetrying", s);
            }
        } else {
            if (rtmpEventStreamHandler != null) {
                rtmpEventStreamHandler.sendConnectionEvent("onConnectionFailed", s);
            }
        }
    }

    @Override
    public void onDisconnect() {
        if (rtmpEventStreamHandler != null) {
            rtmpEventStreamHandler.sendConnectionEvent("onDisconnect", null);
        }
    }

    @Override
    public void onAuthError() {
        genericStream.stopStream();
    }

    @Override
    public void onAuthSuccess() {

    }

    /**
     * 设置 RTMP 流
     * @param stream RTMP 流
     */
    public void setRtmpStream(RtmpStream stream) {
        this.genericStream = stream;
    }

    public void setRtmpEventStreamHandler(RtmpEventStreamHandler rtmpEventStreamHandler) {
        this.rtmpEventStreamHandler = rtmpEventStreamHandler;
    }

    @Override
    public void onFps(int fps) {
        // 需要在主线成操作
        mainLooperHandler.post(() ->{
            if (rtmpEventStreamHandler != null) {
                rtmpEventStreamHandler.sendFpsEvent(fps);
            }
        });

    }
}
