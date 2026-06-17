package cn.zhangheng.tool.extractor;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.zhangheng.tool.bean.FrameScoreResult;
import cn.zhangheng.tool.ffmpeg.FFmpegExtractRawFrame;
import cn.zhangheng.tool.util.SimilarFrameUtil;
import com.zhangheng.file.FileUtil;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.zhangheng.tool.util.OpenCvUtil.IMG_SUFFIX;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/11 星期四 05:15
 * @version: 1.0
 * @description:
 */
public class VideoFrameExtractor {

    private final LinkedBlockingQueue<Path> queue = new LinkedBlockingQueue<>(10000);
    private WatchService watcher;
    private final ThreadPoolExecutor filterPool = new ThreadPoolExecutor(
            8, 16,
            30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            r -> {
                Thread t = new Thread(r);
                t.setName("filter-frame-" + t.getId());
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    private final AtomicInteger watcherCount = new AtomicInteger(0);
    private final AtomicInteger filterCount = new AtomicInteger(0);
    private final AtomicBoolean isScanFinish = new AtomicBoolean(false);
    private final AtomicBoolean isExtractFinish = new AtomicBoolean(false);
    private final String video, tempDir, resDir;
    @Setter
    private int frameStepSecond = 2;


    public VideoFrameExtractor(String video, String tempDir, String resDir) {
        this.video = video;
        this.tempDir = tempDir;
        this.resDir = resDir;
        FileUtil.mkdir(tempDir);
        FileUtil.mkdir(resDir);
    }


    public void run() {
        long sta = System.currentTimeMillis();
        //开始截取视频帧
        startExtractRawFrame(video, tempDir);
        //开始定时扫描视频帧目录
        startScanDirMonitor(Paths.get(tempDir));
        //开始过滤视频帧
        startFilterHighQualityFrame();
        System.out.println("总耗时:" + (System.currentTimeMillis() - sta) + ", 视频截取帧数:" + watcherCount.get() + ", 过滤后帧数:" + filterCount.get());
    }

    public void startFilterHighQualityFrame() {
        for (int i = 0; i < 8; i++) {
            filterPool.submit(() -> {
                Path path = null;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (isScanFinish.get()) {
                            path = queue.poll();
                        } else {
                            path = queue.poll(20, TimeUnit.SECONDS);
                        }
                        if (path == null) {
//                            System.out.println(Thread.currentThread().getName() + "线程退出！" + queue.size());
                            break;
                        }
                        FrameScoreResult frame = FrameScoreResult.scoreFrame(path);
                        if (!frame.isValid()) continue;
                        if (SimilarFrameUtil.isSimilarFrame(frame.getHash(), frame.getTotalScore())) continue;
                        String outName = String.format(FileUtil.getMainName(frame.getFrame().getFileName().toString()) + "_score_%.2f" + IMG_SUFFIX, frame.getTotalScore());
                        Path target = Paths.get(resDir, outName);
                        Path src = frame.getFrame();
                        try {
                            // 原子覆盖目标文件
                            Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            // 移动失败兜底：降级复制后删原文件
                            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        filterCount.incrementAndGet();
                        System.out.printf("🎉 保存优质帧 %s | 得分:%.2f %n", outName, frame.getTotalScore());
                    } catch (Exception ignored) {
                        break;
                    } finally {
                        if (path != null) {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            });
        }
        filterPool.shutdown();
        try {
            filterPool.awaitTermination(1,TimeUnit.HOURS);
        } catch (InterruptedException e) {
        }
    }

    //
    public void startExtractRawFrame(String video, String tempDir) {
        Thread thread = new Thread(() -> {
            try {
                FFmpegExtractRawFrame rawFrame = new FFmpegExtractRawFrame(tempDir);
                rawFrame.setFrameStepSecond(frameStepSecond);
                rawFrame.run(video);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isExtractFinish.set(true);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void startWatchDirMonitor(Path watchPath) throws IOException {
        // 创建监听服务
        watcher = FileSystems.getDefault().newWatchService();
        // 注册要监听的事件
        watchPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
        Thread thread = new Thread(() -> {
            System.out.println(watchPath + "文件夹监听已开启!");
            WatchKey key;
            try {
                // poll(超时时间,单位) 阻塞等待事件；无超时用 take() 无限阻塞
                while ((key = watcher.poll(10, TimeUnit.SECONDS)) != null) {
                    // 遍历本次所有事件
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        // 过滤溢出事件（事件过多堆积）
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            System.out.println("过滤溢出事件（事件过多堆积）");
                            continue;
                        }
                        // 新增文件
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path fileName = (Path) event.context();
                            Path fullPath = watchPath.resolve(fileName);
                            queue.put(fullPath);
                            watcherCount.incrementAndGet();
                        }
                    }
                    // 重置key，不重置下次收不到事件
                    boolean isValid = key.reset();
                    if (!isValid) {
                        // 目录被删除、监听失效，退出循环
                        System.out.println("监听目录失效，停止监控");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("监听线程被中断");
            }
            System.out.println(watchPath + "文件夹监听已结束!" + watcherCount.get());
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void startScanDirMonitor(Path watchPath) {
        Set<Path> set = new ConcurrentHashSet<>();
        Thread thread = new Thread(() -> {
            try {
                int i = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try (Stream<Path> stream = Files.list(watchPath);) {
                        List<Path> paths = stream.filter(p -> p.toFile().getName().endsWith(IMG_SUFFIX) && set.add(p))
                                .collect(Collectors.toList());
                        for (Path path : paths) {
                            queue.put(path);
                            watcherCount.incrementAndGet();
                        }
                        if (paths.isEmpty()) {
                            i++;
                        } else {
                            i = 0;
                        }
                        if (i > 20) break;
                        if (!isExtractFinish.get())
                            TimeUnit.SECONDS.sleep(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (isExtractFinish.get()) i = 20;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println(watchPath + "文件扫描结束！" + set.size());
                set.clear();
                isScanFinish.set(true);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (!filterPool.isShutdown()) {
            filterPool.shutdownNow();
        }
    }

}
