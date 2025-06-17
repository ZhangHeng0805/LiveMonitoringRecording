package cn.zhangheng.common.record;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/15 星期四 22:43
 * @version: 1.0
 * @description:
 */

import cn.zhangheng.common.bean.Constant;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class FlvStreamRecorder extends Recorder {
    private static final int BufferSize = 512 * 1024;
    // 缓冲区队列容量
    private static final int QUEUE_CAPACITY = 10;

    private final AtomicLong totalBytes = new AtomicLong(0);

    static {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "20");
        System.setProperty("sun.net.client.defaultReadTimeout", "60000");
    }

    public FlvStreamRecorder(String downloadUrl, String saveFilePath, String definition) {
        super(downloadUrl, saveFilePath, definition);
    }


    public void start() {
        try {
            run(false);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public long getTimeMs() {
        if (endTime != null) {
            return endTime - startTime;
        } else {
            return System.currentTimeMillis() - startTime;
        }
    }

    @Override
    public long getDownloadSize() {
        return totalBytes.get();
    }

    @Override
    public String getProgressMsg() {
        long totalMS = getTimeMs();
        return "已录制: "
                + TimeUtil.formatMSToCn((int) totalMS)
                + " / " + FileUtil.fileSizeStr(getDownloadSize())
                + " / " + FlvStreamRecorder.getBitrate(getDownloadSize(), totalMS) + " kbps";
    }

    @Override
    public void download() throws Exception {
        recordFlvStream(getDownloadUrl(), getSaveFilePath());
    }


    /**
     * 录制FLV直播流到本地文件
     *
     * @param streamUrl  FLV直播流地址
     * @param outputFile 输出文件路径
     * @throws IOException 当发生IO错误时抛出
     */
    private void recordFlvStream(String streamUrl, String outputFile)
            throws IOException {
        URL url = new URL(streamUrl);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", Constant.User_Agent);
        if (room != null) {
            conn.setRequestProperty("Referer", room.getPlatform().getMainUrl() + room.getId());
            if (room.getCookie() != null) {
                conn.setRequestProperty("Cookie", room.getCookie());
            }
        }
        try (ReadableByteChannel inChannel = Channels.newChannel(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            asyncReadWrite(fos, inChannel);
        }
    }

    /**
     * 同步读写
     *
     * @param fos
     * @param inChannel
     * @return
     * @throws IOException
     */
//    private long readWrite(FileOutputStream fos, ReadableByteChannel inChannel) throws IOException {
//        long totalBytes = 0;
//        try (FileChannel outChannel = fos.getChannel()) {
//            FileDescriptor fd = fos.getFD();
//            ByteBuffer buffer = ByteBuffer.allocateDirect(BufferSize); // 缓冲区
//            long lastReportTime = startTime;
//
//            // 主录制循环
//            while (isRunning.get()) {
//                int bytesRead = inChannel.read(buffer);
//                if (bytesRead == -1) {
//                    break; // 流结束
//                }
//                buffer.flip();
//                while (buffer.hasRemaining()) {
//                    outChannel.write(buffer);
//                }
//                totalBytes += bytesRead;
//                buffer.clear();
//
//                // 进度报告
//                long currentTime = System.currentTimeMillis();
//                if (progressCallback != null && currentTime - lastReportTime > 1000) {
//                    long durationMS = currentTime - startTime;
//                    progressCallback.onProgress(totalBytes, durationMS, currentTime);
//                    lastReportTime = currentTime;
//                    // 定期同步磁盘，平衡性能和数据安全性
//                    if ((int) (durationMS / 1000) % 10 == 0) {
//                        fd.sync();
//                    }
//                }
//            }
//            fd.sync();
//            endTime = System.currentTimeMillis();
//        } catch (IOException e) {
//            Task.log.error("readWrite发生异常：" + ThrowableUtil.getAllCauseMessage(e));
//            Thread.currentThread().interrupt();
//            isRunning.set(false);
//        }
//        return totalBytes;
//    }

    /**
     * 异步读写 读写同时进行
     *
     * @param fos
     * @param inChannel
     * @return
     * @throws IOException
     */
    private long asyncReadWrite(FileOutputStream fos, ReadableByteChannel inChannel) throws IOException {
//        AtomicLong totalBytes = new AtomicLong(0);

        // 创建有界队列用于读写分离
        BlockingQueue<ByteBuffer> readQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        BlockingQueue<ByteBuffer> writeQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // 初始化缓冲区池
        for (int i = 0; i < QUEUE_CAPACITY; i++) {
            writeQueue.offer(ByteBuffer.allocateDirect(BufferSize));
        }

        try {
            // 启动读取线程
            Future<?> future = executor.submit(() -> {
                try {
                    readTask(inChannel, readQueue, writeQueue);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            // 写入线程(当前线程)
            writeTask(fos, readQueue, writeQueue, totalBytes);

            future.get();

            endTime = System.currentTimeMillis();
            return totalBytes.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            isRunning.set(false);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            // 清空队列
            readQueue.clear();
            writeQueue.clear();
        }
    }


    private void readTask(ReadableByteChannel inChannel, BlockingQueue<ByteBuffer> readQueue, BlockingQueue<ByteBuffer> writeQueue) throws InterruptedException {
        try {
            while (isRunning.get()) {
                try {
                    // 从写队列获取空缓冲区
                    ByteBuffer buffer = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (buffer == null) continue;

                    buffer.clear();
                    int bytesRead = 0;

                    // 批量读取数据
                    while (buffer.hasRemaining()) {
                        int read = inChannel.read(buffer);
                        if (read == -1) {
                            isRunning.set(false);
                            break;
                        }
                        if (read == 0) break;
                        bytesRead += read;
                    }

                    if (bytesRead > 0) {
                        buffer.flip();
                        // 将满缓冲区放入读队列
                        readQueue.put(buffer);
                    } else {
                        // 没有数据可读，归还缓冲区
                        writeQueue.put(buffer);
                    }
                } catch (IOException e) {
                    log.error("录制中网络读取发生异常：{}", ThrowableUtil.getAllCauseMessage(e), e);
                    Thread.currentThread().interrupt();
                    isRunning.set(false);
                }
            }

//            // 处理剩余数据
//            while (!readQueue.isEmpty()) {
//                TimeUnit.SECONDS.sleep(1); // 等待写入线程处理
//            }
        } catch (InterruptedException e) {
            throw e;
//            Thread.currentThread().interrupt();
//            isRunning.set(false);
        }
    }

    private void writeTask(FileOutputStream fos, BlockingQueue<ByteBuffer> readQueue, BlockingQueue<ByteBuffer> writeQueue, AtomicLong totalBytes) {
        try (FileChannel outChannel = fos.getChannel()) {
            FileDescriptor fd = fos.getFD();
//            long lastReportTime = startTime;
            while (isRunning.get() || !readQueue.isEmpty()) {
                // 从读队列获取满缓冲区
                ByteBuffer buffer = readQueue.poll(100, TimeUnit.MILLISECONDS);
                if (buffer == null) continue;

                // 写入数据
                while (buffer.hasRemaining()) {
                    totalBytes.addAndGet(outChannel.write(buffer));
                }
                // 归还空缓冲区到写队列
                buffer.clear();
                writeQueue.put(buffer);
                // 定期同步磁盘，平衡性能和数据安全性
                if (totalBytes.get() % (BufferSize * 10) == 0) {
                    fd.sync();
                }
            }
            fd.sync();
        } catch (IOException e) {
            isRunning.set(false);
            throw new RuntimeException("写入文件失败", e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * 计算码率kbps
     *
     * @param bytesReceived
     * @param durationMS
     * @return
     */
    public static long getBitrate(long bytesReceived, long durationMS) {
        return (long) ((bytesReceived * 8) / ((durationMS / 1000.0) * 1024));
    }

    // 示例用法
    public static void main(String[] args) {
        String flvUrl = "https://pull-flv-l26.douyincdn.com/stage/stream-7510992371950078760_or4.flv?arch_hrchy=h1&exp_hrchy=h1&expire=6845c418&major_anchor_level=common&sign=a3f11bfb3a0f7da42112bc72bf92c2ef&t_id=037-20250602011047DE5DAE6B3E4CC10A3E24-rqU2Dx&unique_id=stream-7510992371950078760_139_flv_or4&abr_pts=-800";
        String outputPath = TimeUtil.getNowUnix() + ".flv";

        try {
            System.out.println("开始录制FLV流...");
            Recorder flvStreamRecorder = new FlvStreamRecorder(flvUrl, outputPath, "原画");
            flvStreamRecorder.setTimeoutSeconds(60);
            flvStreamRecorder.setProgressCallback(new Recorder.ProgressCallback() {
            });
            flvStreamRecorder.run(false);
        } catch (Exception e) {
            System.err.println("录制过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
