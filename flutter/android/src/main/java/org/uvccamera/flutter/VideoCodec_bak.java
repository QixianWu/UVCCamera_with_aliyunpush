package org.uvccamera.flutter;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import com.alivc.live.pusher.AlivcLivePusher;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Kenny on 2025/3/4.
 */
public class VideoCodec_bak {

    final AlivcLivePusher alivcLivePusher;

    final UVCCamera camera;

    private MediaCodec mediaCodec;

    private MediaCodec decoder;

    private boolean isLiving;

    private long timeStamp;

    private final ExecutorService encoderExecutor;

    private final UvcCameraStreamCallback uvcCameraStreamCallback;

    final Context context;

    // 添加帧率统计变量
    private long frameCount = 0;
    private long lastFpsLogTime = 0;
    private static final long FPS_LOG_INTERVAL = 1000; // 每秒记录一次


    public VideoCodec_bak(Context context, UVCCamera camera, AlivcLivePusher alivcLivePusher) {
        this.context = context;
        this.camera = camera;
        this.alivcLivePusher = alivcLivePusher;

        uvcCameraStreamCallback = new UvcCameraStreamCallback(
                context,
                camera,
                alivcLivePusher
        );

        encoderExecutor = Executors.newSingleThreadExecutor();

        Size previewSize = camera.getPreviewSize();
        // 配置编码参数
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, previewSize.width, previewSize.height);
        //编码数据源的格式
        // videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
        //         MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //码率
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
        //帧率
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        //关键帧间隔，2秒
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        try {
            // 创建编码器
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);


// 增加编码器配置有效性检查
            MediaCodecInfo codecInfo = mediaCodec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
            int colorFormat = findSupportedColorFormat(caps);

// 使用实际支持的颜色格式
            Log.i("TAG", "colorFormat: " + colorFormat);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

            mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("初始化编码器失败", e);
        }

        try {
            // 创建解码器
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, previewSize.width, previewSize.height);
            decoder.configure(format, null, null, 0);
        }catch (IOException e){
            e.printStackTrace();
            throw new RuntimeException("初始化解码器失败", e);
        }

    }

    // 新增颜色格式检查方法
    private int findSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        for (int format : caps.colorFormats) {
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return format;
            }
        }
        throw new IllegalStateException("设备不支持Surface颜色格式");
    }

    public void startEncoding() {
//         增加编码器状态检查
        if (mediaCodec == null) {
            throw new IllegalStateException("编码器未正确初始化");
        }
//         从编码器创建一个画布, 画布上的图像会被编码器自动编码
        Surface surface = mediaCodec.createInputSurface();
        camera.startCapture(surface);
        mediaCodec.start();

        decoder.start();

        // 设置为true，使编码循环能够运行
        isLiving = true;

//        camera.setFrameCallback(uvcCameraStreamCallback, UVCCamera.PIXEL_FORMAT_NV21);

        encoderExecutor.execute(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //TODO mediaCodec有个关键帧问题，需要手动触发输出关键帧
            while (isLiving) {
                if (timeStamp != 0) {
                    //2000毫秒 手动触发输出关键帧
                    if (System.currentTimeMillis() - timeStamp >= 2000) {
                        Bundle params = new Bundle();
                        //立即刷新 让下一帧是关键帧
                        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                        mediaCodec.setParameters(params);
                        timeStamp = System.currentTimeMillis();
                    }
                } else {
                    timeStamp = System.currentTimeMillis();
                }

                //获得编码之后的数据
                //从输出队列获取到输出到数据
                int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);//超时时间：10微秒
                if (index >= 0) {
                    //成功取出的编码数据
                    ByteBuffer buffer = mediaCodec.getOutputBuffer(index);

                    if (buffer != null) {
                        handleEncodedData(buffer, bufferInfo);
                    }

                    //释放，让队列中index位置能放新数据
                    mediaCodec.releaseOutputBuffer(index, false);
                }
            }
        });
    }

    public static void convertNV12toNV21(byte[] nv12, byte[] nv21, int width, int height) {
        int size = width * height;
        int uvSize = size / 2;

        // 复制 Y 通道数据
        System.arraycopy(nv12, 0, nv21, 0, size);

        // 交换 U 和 V 通道
        for (int i = 0; i < uvSize; i += 2) {
            // NV12 格式是 UV 排列，NV21 格式是 VU 排列
            nv21[size + i] = nv12[size + i + 1];     // V
            nv21[size + i + 1] = nv12[size + i];     // U
        }
    }

    private void handleEncodedData(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        Size previewSize = camera.getPreviewSize();
        byte[] outData = new byte[bufferInfo.size];
        buffer.get(outData);

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.i("TAG", "收到编码器配置数据");
            // 对于配置帧，需要特殊处理，将SPS和PPS送入解码器
            int inputBufferIndex = decoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(outData);
                decoder.queueInputBuffer(inputBufferIndex, 0, outData.length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            }
            return;
        }

        Log.i("TAG", "handleEncodedData: " + outData.length);

        buffer.position(bufferInfo.offset);
        buffer.limit(bufferInfo.offset + bufferInfo.size);

        // 将H.264数据送入解码器
        int inputBufferIndex = decoder.dequeueInputBuffer(10000); // 10ms超时
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(outData);
            decoder.queueInputBuffer(inputBufferIndex, 0, outData.length, bufferInfo.presentationTimeUs, bufferInfo.flags);

            // 添加日志跟踪输入
            Log.i("TAG", "送入解码器数据大小: " + outData.length + ", 时间戳: " + bufferInfo.presentationTimeUs);
        } else {
            Log.e("TAG", "无法获取解码器输入缓冲区");
        }

        // 获取解码后的YUV数据
        MediaCodec.BufferInfo decoderBufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 10000); // 10ms超时

        // 添加日志跟踪输出索引
        Log.i("TAG", "解码器输出索引: " + outputBufferIndex);

        if (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);

            // 计算YUV420P的大小
            int ySize = previewSize.width * previewSize.height;
            int uvSize = ySize / 4;
            int totalSize = ySize + uvSize * 2; // YUV420P总大小

            byte[] yuvData = new byte[totalSize];

            if (outputBuffer != null) {
                outputBuffer.position(decoderBufferInfo.offset);
                outputBuffer.get(yuvData, 0, Math.min(decoderBufferInfo.size, yuvData.length));

                // 帧率统计
                frameCount++;
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFpsLogTime >= FPS_LOG_INTERVAL) {
                    float fps = frameCount * 1000.0f / (currentTime - lastFpsLogTime);
                    Log.i("TAG", "processNV21 FPS: " + fps + " frames/sec");
                    frameCount = 0;
                    lastFpsLogTime = currentTime;
                }

                // 将YUV数据传给推流SDK
                alivcLivePusher.inputStreamVideoData(
                        yuvData,
                        previewSize.width,
                        previewSize.height,
                        previewSize.width,
                        yuvData.length,
                        decoderBufferInfo.presentationTimeUs,
                        0
                );

                Log.i("TAG", "解码成功，传输YUV420P数据，大小: " + yuvData.length);
            }

            // 释放解码器输出缓冲区
            decoder.releaseOutputBuffer(outputBufferIndex, false);
        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // 输出格式变化，可以获取新的格式信息
            MediaFormat newFormat = decoder.getOutputFormat();
            Log.i("TAG", "解码器输出格式变化: " + newFormat);
        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            Log.i("TAG", "解码器需要更多时间");
        } else {
            Log.e("TAG", "解码器返回未知索引: " + outputBufferIndex);
        }
    }

    public boolean isH264(byte[] data) {
        if (data == null || data.length < 4) return false;
        return (data[0] == 0x00 && data[1] == 0x00 &&
                ((data[2] == 0x00 && data[3] == 0x01) || data[2] == 0x01));
    }

    public boolean isNV21(byte[] data, int width, int height) {
        int expectedSize = width * height * 3 / 2;
        return data != null && data.length == expectedSize;
    }

    public void stopEncoding() {
        camera.setFrameCallback(null, 0);
        isLiving = false;
        release();
//        encoderExecutor.shutdownNow();
        encoderExecutor.shutdown();

    }

    public void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }

        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
    }
}
