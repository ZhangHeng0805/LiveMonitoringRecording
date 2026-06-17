package cn.zhangheng.common.bean;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/23 星期五 15:31
 * @version: 1.0
 * @description:
 */
public abstract class Task {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    @Getter
    protected Long startTime;
    @Getter
    protected Long endTime;
    protected ExecutorService mainExecutors;

    protected Task() {
        this("task-thread-", 1);
    }

    protected Task(String threadNamePrefix, int coreSize) {
        this.mainExecutors = new ThreadPoolExecutor(
                coreSize, coreSize,
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(coreSize * 5),
                new ThreadFactory() {
                    private int count = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, threadNamePrefix + count++);
                        thread.setUncaughtExceptionHandler((t, e) ->
                                log.error("线程[" + t.getName() + "]未捕获异常", e)
                        );
                        return thread;
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        BlockingQueue<?> queue = executor.getQueue();
                        throw new RejectedExecutionException(
                                String.format("线程池队列已满,容量:%d,当前排队:%d", queue.remainingCapacity() + queue.size(), queue.size())
                        );
                    }
                }
        );
    }

    public abstract void run(boolean isAsync) throws ExecutionException;

    public void stop(boolean force) {
        isRunning.set(false);
        if (force) {
            mainExecutors.shutdownNow();
        } else {
            mainExecutors.shutdown();
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

}
