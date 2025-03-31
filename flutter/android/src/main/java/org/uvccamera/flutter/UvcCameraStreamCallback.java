package org.uvccamera.flutter;

import android.content.Context;
import android.util.Log;

import com.alivc.live.pusher.AlivcLivePusher;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import org.webrtc.ali.VideoFrame;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/* package-private */ class UvcCameraStreamCallback implements IFrameCallback {

    /**
     * Log tag
     */
    private static final String TAG = UvcCameraStreamCallback.class.getCanonicalName();

    private final UVCCamera camera;
    private final AlivcLivePusher alivcLivePusher;

    final Context context;

    // 帧率控制变量
    private int maxFps = 30;

    final YUVRecorder recorder;

    UvcCameraStreamCallback(Context context, UVCCamera camera, AlivcLivePusher alivcLivePusher) {
        this.context = context;
        this.camera = camera;
        this.alivcLivePusher = alivcLivePusher;

        this.recorder = new YUVRecorder(context, "output.yuv");

    }

    @Override
    public void onFrame(ByteBuffer frame) {
        Size previewSize = camera.getPreviewSize();

        camera.getPreviewSize()

        // 获取帧数据
        byte[] data = new byte[frame.remaining()];
        frame.get(data);
        frame.rewind();

        // 自动检测帧格式
//        String format = detectFrameFormat(data, previewSize.width, previewSize.height);
//        Log.d("FrameFormat", "检测到格式：" + format + " 数据长度：" + data.length);

        previewSize.width = 1280;
        previewSize.height = 1024;
        processNV21(data, previewSize);

//        // 根据格式处理数据
//        switch (format) {
//            case "NV21":
//                processNV21(data, previewSize);
//                break;
//            case "YUYV":
//                processYUYV(data, previewSize);
//                break;
//            case "RGBA":
//                processRGBA(data, previewSize);
//                break;
//            default:
//                Log.e("FormatError", "不支持的格式：" + format);
//        }
    }

    // 格式检测方法
    // 修改格式检测方法
    private String detectFrameFormat(byte[] data, int width, int height) {
        int actualLength = data.length;
        int ySize = width * height;

        // 计算带填充字节的可能长度
        boolean maybeNV21WithPadding = actualLength == (ySize * 3) / 2;
        boolean maybeYUYVWithPadding = actualLength % (width * 2) == 0;

        // 优先检查特征值
        if (isLikelyNV21(data, width, height)) {
            return maybeNV21WithPadding ? "NV21_PADDED" : "NV21";
        }
        if (isLikelyYUYV(data, width, height)) {
            return maybeYUYVWithPadding ? "YUYV_PADDED" : "YUYV";
        }

        // 添加调试日志辅助分析
        logSampleBytes(data, width, height);
        return "UNKNOWN";
    }

    // 新增采样日志方法
    private void logSampleBytes(byte[] data, int width, int height) {
        int ySize = width * height;
        Log.d("FormatDebug", "采样数据："
                + " Y[0]=" + (data[0] & 0xFF)
                + " UV[0]=" + (data[ySize] & 0xFF) + "," + (data[ySize + 1] & 0xFF)
                + " MidY=" + (data[ySize / 2] & 0xFF)
                + " LastUV=" + (data[data.length - 2] & 0xFF) + "," + (data[data.length - 1] & 0xFF));
    }

    // 优化YUYV特征验证
    private boolean isLikelyYUYV(byte[] data, int width, int height) {
        // 检查至少10个像素点的Y分量模式
        int validChecks = 0;
        for (int i = 0; i < Math.min(data.length, 400); i += 4) {
            if (i + 3 >= data.length) break;
            // YUYV中相邻Y分量可以相同（取决于图像内容），改为检查UV值范围
            int u = data[i + 1] & 0xFF;
            int v = data[i + 3] & 0xFF;
            if (u < 16 || u > 240 || v < 16 || v > 240) return false;
            validChecks++;
        }
        return validChecks > 5;
    }

    // NV21特征验证
    private boolean isLikelyNV21(byte[] data, int width, int height) {
        int ySize = width * height;
        if (data.length < ySize * 1.5) return false;

        // 检查UV分量排列模式
        for (int i = ySize; i < ySize + 10; i += 2) {
            if (data[i] == 0 && data[i + 1] == 0) return false; // NV21应有交替的VU值
        }
        return true;
    }

    // 各格式处理方法
    private void processNV21(byte[] data, Size size) {

//        byte[] yuv420p = nv21ToYuv420p2(data, size.width, size.height);
//
//        byte[] yuv420p = new byte[data.length];
//
//        NV21_TO_yuv420P(yuv420p, data, size.width, size.height);

        Log.i(TAG, "processNV21: "+size.width + " "+size.height);

//        int stride = (size.width + 15) / 16 * 16;

            int stride = size.width;
            int videoSize = size.width * size.height * 3 / 2;


//        recorder.addFrame(data);


//        byte[] nv21;
//
//        if (stride != size.width) {
//            nv21 =  alignNV21(data, size.width, size.height, stride);
//        }else{
//            nv21 = data;
//        }

        alivcLivePusher.inputStreamVideoData(
                data,
                size.width,
                size.height,
                stride, // NV21 stride
                videoSize,
                System.nanoTime() / 1000,
                0
        );
    }

    private void processYUYV(byte[] data, Size size) {
        // 添加YUYV转NV21逻辑
//        byte[] converted = convertYUYVtoNV21(data, size.width, size.height);
//        processNV21(converted, size);
    }

    private void processRGBA(byte[] data, Size size) {
        // 添加RGBA转NV21逻辑
//        byte[] converted = convertRGBAToNV21(data, size.width, size.height);
//        processNV21(converted, size);
    }

    public static byte[] alignNV21(byte[] nv21, int width, int height, int stride) {
        int sliceHeight = height +304 ; // 由于无法获取 sliceHeight，先假设等于 height

        if (stride == width) {
            return nv21; // 无需调整
        }



        int ySize = width * height;
        int uvSize = ySize / 2;
        int alignedYSize = stride * sliceHeight;
        int alignedUVSize = (stride * sliceHeight) / 2;
        byte[] alignedNV21 = new byte[alignedYSize + alignedUVSize];

        // 处理 Y 分量
        for (int i = 0; i < height; i++) {
            System.arraycopy(nv21, i * width, alignedNV21, i * stride, width);
        }

        // 处理 UV 分量
        int uvStart = ySize;
        int alignedUVStart = alignedYSize;
        for (int i = 0; i < height / 2; i++) {
            System.arraycopy(nv21, uvStart + i * width, alignedNV21, alignedUVStart + i * stride, width);
        }

        return alignedNV21;
    }

    private void NV21ToI420(byte[] nv21, byte[] i420, int width, int height) {
        int frameSize = width * height;
        int uIndex = frameSize;
        int vIndex = frameSize + frameSize / 4;

        System.arraycopy(nv21, 0, i420, 0, frameSize);  // 复制 Y 分量

        for (int i = 0; i < frameSize / 4; i++) {
            i420[uIndex + i] = nv21[frameSize + 2 * i + 1]; // U 分量
            i420[vIndex + i] = nv21[frameSize + 2 * i];     // V 分量
        }
    }

    public static byte[] nv21ToYuv420p(byte[] nv21, int width, int height) {
        byte[] yuv420p = new byte[width * height * 3 / 2];
        // 拷贝Y分量
        System.arraycopy(nv21, 0, yuv420p, 0, width * height);
        // 分离UV分量（NV21的VU交错 → YUV420P的U平面和V平面）
        int uvOffset = width * height;
        for (int i = 0; i < width * height / 4; i++) {
            yuv420p[uvOffset + i] = nv21[uvOffset + i * 2 + 1]; // U分量
            yuv420p[uvOffset + width * height / 4 + i] = nv21[uvOffset + i * 2]; // V分量
        }
        return yuv420p;
    }

    public final static int NV21_TO_yuv420P(byte[] dst, byte[] src, int w, int h) {
        int ysize = w * h;
        int usize = w * h * 1 / 4;

        byte[] dsttmp = dst;

        // y
        System.arraycopy(src, 0, dst, 0, ysize);

        // u, 1/4
        int srcPointer = ysize;
        int dstPointer = ysize;
        int count = usize;
        while (count > 0) {
            srcPointer++;
            dst[dstPointer] = src[srcPointer];
            dstPointer++;
            srcPointer++;
            count--;
        }

        // v, 1/4
        srcPointer = ysize;

        count = usize;
        while (count > 0) {
            dst[dstPointer] = src[srcPointer];
            dstPointer++;
            srcPointer += 2;
            count--;
        }

        dst = dsttmp;

        // _EF_TIME_DEBUG_END(0x000414141);

        return 0;
    }

    public static byte[] nv21ToYuv420p2(byte[] nv21Data, int width, int height) {
        int frameSize = width * height;
        byte[] yuv420pData = new byte[(int) (frameSize * 1.5)]; // YUV420p需要1.5倍的Y平面大小空间

        // 分离Y和UV数据
        byte[] yPlane = new byte[frameSize];
        byte[] uvPlane = new byte[frameSize / 2]; // UV平面大小为Y平面的一半
        System.arraycopy(nv21Data, 0, yPlane, 0, frameSize);
        System.arraycopy(nv21Data, frameSize, uvPlane, 0, frameSize / 2);

        // 复制Y平面到YUV420p的Y平面位置
        System.arraycopy(yPlane, 0, yuv420pData, 0, frameSize);

        // 重新排列UV平面到YUV420p的UV平面位置
        int yuvIndex = frameSize; // Y平面的大小即为开始UV平面的索引
        for (int i = 0; i < uvPlane.length; i += 2) { // 处理每两个UV值为一组
            yuv420pData[yuvIndex++] = uvPlane[i + 1]; // U值
            yuv420pData[yuvIndex++] = uvPlane[i];     // V值
        }

        return yuv420pData;
    }
}
