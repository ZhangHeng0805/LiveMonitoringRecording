package cn.zhangheng.common.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/21 星期一 10:14
 * @version: 1.0
 * @description:
 */
public class AsyncTaskQueue {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    // 任务队列
    private final BlockingQueue<Runnable> taskQueue;
    // 线程池
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning;
    // 核心线程数
    private final int corePoolSize;

    /**
     * 构造函数
     * @param corePoolSize 核心线程数
     * @param queueCapacity 任务队列容量
     */
    public AsyncTaskQueue(int corePoolSize, int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.taskQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.isRunning = new AtomicBoolean(false);
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                0L, TimeUnit.MILLISECONDS,
                taskQueue,
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "async-task-thread-" + counter++);
                        thread.setDaemon(false); // 非守护线程
                        return thread;
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        throw new RejectedExecutionException("任务队列已满，无法添加新任务");
                    }
                }
        );
    }
    /**
     * 启动任务队列
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("异步任务队列已启动，核心线程数: " + corePoolSize);
        }
    }
    public <T> void submit(Task<T> task, TaskCallback<T> callback) {
        if (!isRunning.get()) {
            throw new IllegalStateException("任务队列尚未启动，请先调用start()方法");
        }

        // 将任务提交到线程池
        executorService.submit(() -> {
            try {
                T result = task.execute();
                if (callback != null) {
                    callback.onSuccess(result);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(e);
                } else {
                    log.error("任务执行失败: " + e.getMessage(),e);
//                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 优雅地关闭任务队列
     * 等待所有已提交的任务执行完成，但不再接受新任务
     * @param timeout 等待超时时间
     * @param unit 时间单位
     * @return 如果所有任务都已完成返回true，否则返回false
     * @throws InterruptedException 如果等待被中断
     */
    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        if (isRunning.compareAndSet(true, false)) {
            executorService.shutdown();
            return executorService.awaitTermination(timeout, unit);
        }
        return true;
    }

    /**
     * 强制关闭任务队列
     * 尝试停止所有正在执行的任务，清空队列
     */
    public void forceShutdown() {
        if (isRunning.compareAndSet(true, false)) {
            executorService.shutdownNow();
        }
    }

    /**
     * 获取当前队列中的任务数量
     * @return 任务数量
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 检查队列是否正在运行
     * @return 如果正在运行返回true，否则返回false
     */
    public boolean isRunning() {
        return isRunning.get();
    }

}
