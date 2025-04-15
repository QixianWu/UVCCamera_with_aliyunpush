package org.uvccamera.flutter;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;

/**
 * Created by Kenny on 2025/4/15.
 */
public class RtmpEventStreamHandler implements EventChannel.StreamHandler{

    private static final String TAG = "RtmpEventStreamHandler";

    @Nullable
    private EventChannel.EventSink eventSink;

    private final Object eventSinkLock = new Object();

    public EventChannel.EventSink getEventSink() {
        synchronized (eventSinkLock) {
            return eventSink;
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        Log.d(TAG, "onListen: " + arguments);
        synchronized (eventSinkLock) {
            this.eventSink = events;
        }
    }

    @Override
    public void onCancel(Object arguments) {
        Log.d(TAG, "onCancel: " + arguments);
        synchronized (eventSinkLock) {
            this.eventSink = null;
        }
    }

    /**
     * 发送 FPS 事件
     * @param fps 当前帧率
     */
    public void sendFpsEvent(float fps) {
        if (eventSink == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("type", "fps");
        event.put("value", fps);

        getEventSink().success(event);
    }

    /**
     * 发送连接状态事件
     * @param type 事件类型
     * @param message 事件消息
     */
    public void sendConnectionEvent(String type, @Nullable String message) {
        if (eventSink == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        if (message != null) {
            event.put("value", message);
        }

        getEventSink().success(event);
    }

    /**
     * 发送比特率变化事件
     * @param bitrate 当前比特率
     */
    public void sendBitrateEvent(long bitrate) {
        if (eventSink == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("type", "bitrate");
        event.put("value", bitrate);

        getEventSink().success(event);
    }
}
