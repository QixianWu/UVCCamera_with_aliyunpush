package org.uvccamera.flutter;

/**
 * Created by Kenny on 2025/3/28.
 */
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class YUVRecorder {
    private FileOutputStream fos;
    private volatile boolean isRecording = false;
    private final BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>(); // 缓存队列
    private Thread writeThread;

    public YUVRecorder(Context context, String fileName) {
        try {
            String filePath = context.getExternalFilesDir(null).getAbsolutePath() + "/output.yuv";

            File file = new File(filePath);

            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean dirCreated = parentDir.mkdirs();
                System.out.println("创建目录: " + parentDir.getAbsolutePath() + " 结果: " + dirCreated);
            }

            // 确保文件存在
            if (!file.exists()) {
                boolean fileCreated = file.createNewFile();
                System.out.println("创建文件: " + file.getAbsolutePath() + " 结果: " + fileCreated);
            }
            fos = new FileOutputStream(file);
            isRecording = true;
            System.out.println("YUV 录制开始: " + file.getAbsolutePath());

            // 启动写入线程
            writeThread = new Thread(this::writeLoop);
            writeThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 添加一帧 YUV 数据到缓存队列
    public void addFrame(byte[] yuvData) {
        if (isRecording) {
            frameQueue.offer(yuvData); // 非阻塞入队，避免主线程等待
        }
    }

    // 线程循环写入 YUV 数据
    private void writeLoop() {
        try {
            while (isRecording || !frameQueue.isEmpty()) {
                byte[] frameData = frameQueue.poll(); // 取出一帧（如果为空则等待）
                if (frameData != null && fos != null) {
                    fos.write(frameData);
                    fos.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 停止录制
    public void stop() {
        isRecording = false;
        if (writeThread != null) {
            try {
                writeThread.join(); // 等待写入线程结束
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (fos != null) {
            try {
                fos.close();
                System.out.println("YUV 录制结束");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
