package cn.zhangheng.common.util;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/19 星期六 12:41
 * @version: 1.0
 * @description:
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AsyncBatchLogger {
    private final Path logPath;
    private long lastFlushTime = System.currentTimeMillis();
    private final List<String> lines = new ArrayList<>();
    private final Lock lock = new ReentrantLock();
    // 最大等待队列大小，防止内存溢出
    private static final int MAX_QUEUE_SIZE = 10000;

    // 异步任务执行器（单线程足够，避免过多线程竞争磁盘IO）
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "async-log-writer");
        thread.setDaemon(true); // 守护线程，避免阻塞程序退出
        return thread;
    });

    public AsyncBatchLogger(Path logPath) {
        this.logPath = logPath;
    }

    /**
     * 异步写入日志到文件
     * 提交任务到线程池，不阻塞调用方
     */
    public void asyncLog(Iterable<? extends CharSequence> linesToWrite) {
        if (linesToWrite == null) return;
        // 避免任务队列堆积过多导致OOM
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
            if (threadPool.getQueue().size() >= MAX_QUEUE_SIZE) {
                System.err.println("日志任务队列已满，可能丢失日志！当前队列大小: " + threadPool.getQueue().size());
                return;
            }
        }

        // 提交异步任务
        executor.submit(() -> {
            try {
                Files.write(logPath, linesToWrite,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("异步日志写入失败: " + e.getMessage());
                // 可根据需求添加重试逻辑，如：
                // retryWrite(linesToWrite, 3); // 最多重试3次
            }
        });
    }

    /**
     * 高频日志收集方法
     * 仅在主线程做日志收集和判断，实际写入异步执行
     */
    public void highLog(String info) {
        if (info == null) return;

        lock.lock();
        try {
            lines.add(info);
            long currentTime = System.currentTimeMillis();

            // 满足触发条件时，提交异步写入
            if (currentTime - lastFlushTime > 1000 || lines.size() > 100) {
                List<String> toWrite = new ArrayList<>(lines);
                lines.clear();
                lastFlushTime = currentTime;
                asyncLog(toWrite); // 异步执行，不阻塞当前线程
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制刷新剩余日志（程序退出前必须调用）
     */
    public void flushRemaining() {
        lock.lock();
        try {
            if (!lines.isEmpty()) {
                List<String> remaining = new ArrayList<>(lines);
                lines.clear();
                // 同步写入剩余日志，确保程序退出时不丢失
                try {
                    Files.write(logPath, remaining,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                } catch (IOException e) {
                    System.err.println("程序退出时日志刷新失败: " + e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }

        // 关闭线程池，等待剩余任务完成
        executor.shutdown();
        try {
            // 等待所有异步任务完成，最多等3秒
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // 强制终止未完成的任务
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
