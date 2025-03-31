package org.uvccamera.flutter;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import com.alivc.live.pusher.AlivcLivePusher;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Kenny on 2025/3/4.
 */
public class VideoCodec {

    final AlivcLivePusher alivcLivePusher;

    final UVCCamera camera;

    private MediaCodec mediaCodec;

    private boolean isLiving;

    private long timeStamp;

//    private final ExecutorService encoderExecutor;

    private final UvcCameraStreamCallback uvcCameraStreamCallback;

    final Context context;

    public VideoCodec(Context context, UVCCamera camera, AlivcLivePusher alivcLivePusher) {
        this.context = context;
        this.camera = camera;
        this.alivcLivePusher = alivcLivePusher;

        uvcCameraStreamCallback = new UvcCameraStreamCallback(
                context,
                camera,
                alivcLivePusher
        );

//        encoderExecutor = Executors.newSingleThreadExecutor();
//
//        Size previewSize = camera.getPreviewSize();
//        // 配置编码参数
//        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, previewSize.width, previewSize.height);
//        //编码数据源的格式
//        // videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//        //         MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        //码率
//        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
//        //帧率
//        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
//        //关键帧间隔，2秒
//        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
//        try {
//            // 创建编码器
//            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
//
//
//// 增加编码器配置有效性检查
//            MediaCodecInfo codecInfo = mediaCodec.getCodecInfo();
//            MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
//            int colorFormat = findSupportedColorFormat(caps);
//
//// 使用实际支持的颜色格式
//            Log.i("TAG", "colorFormat: " + colorFormat);
//            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
//
//            mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new RuntimeException("初始化编码器失败", e);
//        }
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
        // 增加编码器状态检查
//        if (mediaCodec == null) {
//            throw new IllegalStateException("编码器未正确初始化");
//        }
        // 从编码器创建一个画布, 画布上的图像会被编码器自动编码
//        Surface surface = mediaCodec.createInputSurface();
//        camera.startCapture(surface);
//        mediaCodec.start();

        // 设置为true，使编码循环能够运行
//        isLiving = false;

        camera.setFrameCallback(uvcCameraStreamCallback, UVCCamera.PIXEL_FORMAT_NV21);

//        encoderExecutor.execute(() -> {
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//            //TODO mediaCodec有个关键帧问题，需要手动触发输出关键帧
//            while (isLiving) {
//                if (timeStamp != 0) {
//                    //2000毫秒 手动触发输出关键帧
//                    if (System.currentTimeMillis() - timeStamp >= 2000) {
//                        Bundle params = new Bundle();
//                        //立即刷新 让下一帧是关键帧
//                        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
//                        mediaCodec.setParameters(params);
//                        timeStamp = System.currentTimeMillis();
//                    }
//                } else {
//                    timeStamp = System.currentTimeMillis();
//                }
//
//                //获得编码之后的数据
//                //从输出队列获取到输出到数据
//                int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);//超时时间：10微秒
//                if (index >= 0) {
//                    //成功取出的编码数据
//                    ByteBuffer buffer = mediaCodec.getOutputBuffer(index);
//
//                    if (buffer != null) {
//                        handleEncodedData(buffer, bufferInfo);
//                    }
//
//                    //释放，让队列中index位置能放新数据
//                    mediaCodec.releaseOutputBuffer(index, false);
//                }
//            }
//        });
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
        byte[] test = new byte[bufferInfo.size];
        buffer.get(outData);
        //这样也能拿到 sps pps
//        ByteBuffer sps = mediaCodec.getOutputFormat().getByteBuffer
//                ("csd-0");
//        ByteBuffer pps = mediaCodec.getOutputFormat().getByteBuffer
//                ("csd-1");
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.i("TAG", "收到编码器配置数据");
            // 对于配置帧，可能需要特殊处理
            return;
        }

        Log.i("TAG", "handleEncodedData: " + outData.length);

        // 转换成NV21
        buffer.position(bufferInfo.offset);
        buffer.limit(bufferInfo.offset + bufferInfo.size);

        byte[] ba = new byte[buffer.remaining()];
        buffer.get(ba);

        byte[] yuv = new byte[ba.length];
        convertNV12toNV21(ba, yuv, previewSize.width, previewSize.height);

//        alivcLivePusher.inputStreamVideoData(
//                test, previewSize.width, previewSize.height, previewSize.width, bufferInfo.size, bufferInfo.presentationTimeUs, 0
//        );
//        alivcLivePusher.inputStreamVideoData(
//                test, previewSize.width, previewSize.height, previewSize.width, 0, bufferInfo.presentationTimeUs, 0
//        );

        Log.i("TAG", "handleEncodedData: " + isH264(outData));
        Log.i("TAG", "handleEncodedData: " + isNV21(yuv, previewSize.width, previewSize.height));
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
//        encoderExecutor.shutdown();

    }

    public void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }
}
