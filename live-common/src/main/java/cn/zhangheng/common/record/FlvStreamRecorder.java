package cn.zhangheng.common.record;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/15 星期四 22:43
 * @version: 1.0
 * @description:
 */

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Task;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class FlvStreamRecorder extends Task {
    private static final int BufferSize = 512 * 1024;
    // 缓冲区队列容量
    private static final int QUEUE_CAPACITY = 10;

    private final AtomicLong totalBytes = new AtomicLong(0);

    public long getTotalBytes() {
        return totalBytes.get();
    }

    static {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "20");
        System.setProperty("sun.net.client.defaultReadTimeout", "60000");
    }

    @Getter
    private String flvPath;
    @Getter
    private String flvUrl;
    @Getter
    private String definition;//清晰度

    private int runCount = 0;

    private Runnable runnable = null;
    @Setter
    private ProgressCallback progressCallback = null;
    @Setter
    private String cookie;

    public FlvStreamRecorder(String flvUrl, String outputFile, int timeoutSeconds, String definition) {
        initRunnable(flvUrl, outputFile, timeoutSeconds, definition);
    }

    public void initRunnable(String flvUrl, String flvPath, int timeoutSeconds, String definition) {
        this.flvUrl = flvUrl;
        this.flvPath = flvPath;
        this.definition = definition;
        this.runnable = () -> {
            try {
                if (flvPath.indexOf("[") < flvPath.indexOf("]")) {
                    String owner = flvPath.substring(flvPath.indexOf("[") + 1, flvPath.indexOf("]"));
                    Thread.currentThread().setName(owner + "-recorder-" + Thread.currentThread().getId());
                } else {
                    Thread.currentThread().setName("recorder-" + Thread.currentThread().getId());
                }
                recordFlvStream(flvUrl, flvPath, timeoutSeconds);
            } catch (Exception e) {
                if (progressCallback != null) {
                    progressCallback.onError(e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public void start() {
        try {
            run(false);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 异步调用结束时需手动调用stop方法停止
     *
     * @param isAsync
     * @throws ExecutionException
     */
    @Override
    public void run(boolean isAsync) throws ExecutionException {
        runCount++;
        if (runCount > 1) {
            reset();
//            initRunnable(flvUrl,flvPath, timeoutSeconds);
        }
        if (mainExecutors.isShutdown() || mainExecutors.isTerminated()) {
            mainExecutors = Executors.newFixedThreadPool(1);
        }
        Future<?> future = mainExecutors.submit(runnable);
        if (!isAsync) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Task.log.error("FLV录制主任务中断：" + ThrowableUtil.getAllCauseMessage(e));
            } finally {
                isRunning.set(false);
            }
        }
        mainExecutors.shutdown();
    }

    public void stop(boolean force) {
        isRunning.set(false);
        if (force) {
            mainExecutors.shutdownNow();
        } else {
            mainExecutors.shutdown();
        }
    }

    private void reset() {
        totalBytes.set(0);
        startTime = null;
        endTime = null;
    }

    public static Path getFlvFilePath(Room roomInfo) {
        String prefix = "【" + FileUtil.filterFileName(roomInfo.getOwner()) + "】直播录制";
        String fileName = prefix + TimeUtil.toTime(roomInfo.getStartTime(), "yyyy-MM-dd HH-mm-ss") + ".flv";
        Path basePath = LogUtil.getBasePath(roomInfo);
        if (!Files.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
            } catch (IOException e) {
                Task.log.error("创建文件夹失败：" + ThrowableUtil.getAllCauseMessage(e));
            }
        }
        Path path = Paths.get(basePath.toString(), fileName);
        if (Files.exists(path)) {
            fileName = prefix + TimeUtil.toTime(new Date(), "yyyy-MM-dd HH-mm-ss") + ".flv";
            return Paths.get(basePath.toString(), fileName);
        }
        return path;
    }

    /**
     * 录制FLV直播流到本地文件
     *
     * @param streamUrl      FLV直播流地址
     * @param outputFile     输出文件路径
     * @param timeoutSeconds 超时时间(秒)，0表示不超时
     * @throws IOException 当发生IO错误时抛出
     */
    private void recordFlvStream(String streamUrl, String outputFile, int timeoutSeconds)
            throws IOException {
        isRunning.set(true);
        ScheduledExecutorService executor = null;
        // 设置超时控制
        if (timeoutSeconds > 0) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(() -> {
                isRunning.set(false);
                if (progressCallback != null) {
                    progressCallback.onTimeout();
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        }

        URLConnection conn = getUrlConnection(streamUrl);
        try (ReadableByteChannel inChannel = Channels.newChannel(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            startTime = System.currentTimeMillis();

            if (progressCallback != null) {
                progressCallback.onStart();
            }

            long totalBytes = asyncReadWrite(fos, inChannel);

            if (progressCallback != null) {
                progressCallback.onComplete(totalBytes, endTime - startTime);
            }

        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            isRunning.set(false);
        }
    }

    private URLConnection getUrlConnection(String streamUrl) throws IOException {
        URL url = new URL(streamUrl);
        URLConnection conn = url.openConnection();

        conn.setRequestProperty("User-Agent", Constant.User_Agent);
        conn.setRequestProperty("Connection", "keep-alive");
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        if (url.getHost().indexOf("bili") > 0) {
            //bilibili需要加header
            conn.setRequestProperty("Referer", Room.Platform.Bili.getMainUrl());
        } else if (url.getHost().indexOf("douyin") > 0) {
            conn.setRequestProperty("Referer", Room.Platform.DouYin.getMainUrl());
        }
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(15000);
        return conn;
    }

    /**
     * 同步读写
     *
     * @param fos
     * @param inChannel
     * @return
     * @throws IOException
     */
    private long readWrite(FileOutputStream fos, ReadableByteChannel inChannel) throws IOException {
        long totalBytes = 0;
        try (FileChannel outChannel = fos.getChannel()) {
            FileDescriptor fd = fos.getFD();
            ByteBuffer buffer = ByteBuffer.allocateDirect(BufferSize); // 缓冲区
            long lastReportTime = startTime;

            // 主录制循环
            while (isRunning.get()) {
                int bytesRead = inChannel.read(buffer);
                if (bytesRead == -1) {
                    break; // 流结束
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    outChannel.write(buffer);
                }
                totalBytes += bytesRead;
                buffer.clear();

                // 进度报告
                long currentTime = System.currentTimeMillis();
                if (progressCallback != null && currentTime - lastReportTime > 1000) {
                    long durationMS = currentTime - startTime;
                    progressCallback.onProgress(totalBytes, durationMS, currentTime);
                    lastReportTime = currentTime;
                    // 定期同步磁盘，平衡性能和数据安全性
                    if ((int) (durationMS / 1000) % 10 == 0) {
                        fd.sync();
                    }
                }
            }
            fd.sync();
            endTime = System.currentTimeMillis();
        } catch (IOException e) {
            Task.log.error("readWrite发生异常：" + ThrowableUtil.getAllCauseMessage(e));
            Thread.currentThread().interrupt();
            isRunning.set(false);
        }
        return totalBytes;
    }

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
                    readTask(inChannel, readQueue, writeQueue, totalBytes);
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


    private void readTask(ReadableByteChannel inChannel, BlockingQueue<ByteBuffer> readQueue, BlockingQueue<ByteBuffer> writeQueue, AtomicLong totalBytes) throws InterruptedException {
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
                        totalBytes.addAndGet(bytesRead);
                    } else {
                        // 没有数据可读，归还缓冲区
                        writeQueue.put(buffer);
                    }
                } catch (IOException e) {
                    Task.log.error("录制中网络读取发生异常：" + ThrowableUtil.getAllCauseMessage(e));
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
                    outChannel.write(buffer);
                }
                // 归还空缓冲区到写队列
                buffer.clear();
                writeQueue.put(buffer);
                // 定期同步磁盘，平衡性能和数据安全性
                if (totalBytes.get() % (BufferSize * 10) == 0) {
                    fd.sync();
                }
                // 进度报告 暂时不需要主动回调
//                long currentTime = System.currentTimeMillis();
//                if (progressCallback != null && currentTime - lastReportTime > 1000) {
//                    long durationMS = currentTime - startTime;
//                    progressCallback.onProgress(totalBytes.get(), durationMS, currentTime);
//                    lastReportTime = currentTime;
//                }
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
     * 进度回调接口
     */
    public interface ProgressCallback {
        default void onStart() {
            Task.log.info("FLV录制已启动!");
        }

        default void onProgress(long bytesReceived, long durationMS, long timeStamp) {
            Task.log.info(
                    "已录制: " + FileUtil.fileSizeStr(bytesReceived)
                            + ", 时长: " + TimeUtil.formatMSToCn((int) durationMS)
                            + ", 码率: " + getBitrate(bytesReceived, durationMS) + " kbps");
        }

        default void onComplete(long totalBytes, long totalDurationMS) {
            long bitrate = getBitrate(totalBytes, totalDurationMS);
            Task.log.info(
                    "FLV录制完成! 总计: " + FileUtil.fileSizeStr(totalBytes)
                            + ", 时长: " + TimeUtil.formatMSToCn((int) totalDurationMS)
                            + ", 平均码率: " + bitrate + " kbps");
        }

        default void onTimeout() {
            Task.log.info("FLV录制超时，已自动停止!");
        }

        default void onError(Throwable throwable) {
            Task.log.error(throwable);
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
//        String flvUrl = "https://cn-jxnc-cm-01-39.bilivideo.com/live-bvc/216120/live_299648350_1208456_2500.flv?expires=1748192108&pt=web&deadline=1748192108&len=0&oi=1866233469&platform=web&qn=250&trid=100029dad3c9ede4ddc27b3ca4bdc668333d&uipk=100&uipv=100&nbs=1&uparams=cdn,deadline,len,oi,platform,qn,trid,uipk,uipv,nbs&cdn=cn-gotcha01&upsig=7fd07bd2fdc883af01b7631d221aa24f&site=0993ad965240cb1511816ecdc558c540&free_type=0&mid=520318232&sche=ban&sid=cn-jxnc-cm-01-39&chash=1&bmt=1&sg=lr&trace=73&isp=cm&rg=Central&pv=Hubei&p2p_type=1&info_source=cache&origin_bitrate=380225&strategy_ids=57&sl=1&deploy_env=prod&strategy_types=2&hot_cdn=0&hdr_type=0&sk=0480b9d2936c72ddd70c94947b704c65&source=puv3_onetier&score=1&codec=0&pp=srt&suffix=2500&vd=nc&zoneid_l=151388163&sid_l=stream_name_cold&src=puv3&order=1";
        String flvUrl = "https://cn-jxnc-cm-01-16.bilivideo.com/live-bvc/432721/live_500017420_15183310.flv?expires=1748284865&pt=web&deadline=1748284865&len=0&oi=1866233469&platform=web&qn=10000&trid=1000400883854e2df31835d0e2aa156834a7&uipk=100&uipv=100&nbs=1&uparams=cdn,deadline,len,oi,platform,qn,trid,uipk,uipv,nbs&cdn=cn-gotcha01&upsig=b2d2aced0e2120906d4ddb2c58398a55&site=7310aec9bcd34257c712215ccb1f79f3&free_type=0&mid=0&sche=ban&sid=cn-jxnc-cm-01-16&chash=0&bmt=1&sg=lr&trace=73&isp=cm&rg=Central&pv=Hubei&codec=0&sk=6168ccbdb616af3bb097fa85095bded8&pp=srt&p2p_type=1&sl=1&hdr_type=0&deploy_env=prod&origin_bitrate=836517&hot_cdn=0&score=1&suffix=origin&info_source=origin&source=puv3_onetier&vd=nc&zoneid_l=151388163&sid_l=stream_name_cold&src=puv3&order=1";
//        String flvUrl = "https://cn-hbwh-cm-01-11.bilivideo.com/live-bvc/328453/live_21144080_bs_4998535_bluray.flv?expires=1748284669&pt=&deadline=1748284669&len=0&oi=1866233469&platform=&qn=10000&trid=1000d9fc3909916e44a6a10da769e69d7982&uipk=100&uipv=100&nbs=1&uparams=cdn,deadline,len,oi,platform,qn,trid,uipk,uipv,nbs&cdn=cn-gotcha01&upsig=9c94f65e887176a70af1fadefbc48b2d&sk=1d25f4da1575fcb927b5b789251233f8&p2p_type=0&sl=2&free_type=0&mid=0&sid=cn-hbwh-cm-01-11&chash=0&bmt=1&sche=ban&score=18&pp=rtmp&source=one&trace=8c1&site=fb36216e847afe8545a5c221df4d1c7d&zoneid_l=151388163&sid_l=live_21144080_bs_4998535_bluray&order=1";
        String outputPath = TimeUtil.getNowUnix() + ".flv";

        try {
            System.out.println("开始录制FLV流...");
            FlvStreamRecorder flvStreamRecorder = new FlvStreamRecorder(flvUrl, outputPath, 60, "原画");
            flvStreamRecorder.setProgressCallback(new ProgressCallback() {
            });
            flvStreamRecorder.start();
        } catch (Exception e) {
            System.err.println("录制过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
