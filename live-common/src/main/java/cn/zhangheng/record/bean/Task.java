package cn.zhangheng.record.bean;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    //标记当前线程是否正在执行本类提交的任务，用于停止时避免"自己中断自己"
    private static final ThreadLocal<Boolean> IN_TASK_WORKER = new ThreadLocal<>();
    //线程池统一通过 submit()/isExecutorShutdown()/releaseExecutor() 访问，禁止直接引用或置空
    private final String threadNamePrefix;
    private final int coreSize;
    //独立锁对象：不与子类业务 synchronized 方法（如 RoomMonitor.refresh）争用同一个监视器
    private final Object executorLock = new Object();
    private volatile ThreadPoolExecutor mainExecutors;

    protected Task() {
        this("task-thread-", 1);
    }

    protected Task(String threadNamePrefix, int coreSize) {
        this.threadNamePrefix = threadNamePrefix;
        this.coreSize = Math.max(coreSize, 1);
        this.mainExecutors = newExecutor();
    }

    /**
     * 按构造参数创建线程池（保留原有队列容量与拒绝策略）
     */
    private ThreadPoolExecutor newExecutor() {
        return new ThreadPoolExecutor(
                coreSize, coreSize,
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(coreSize * 5),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, threadNamePrefix + count.getAndIncrement());
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

    /**
     * 原子提交：取池与提交在同一把锁内完成，避免取到池后池被并发释放导致提交失败；
     * 极端情况下（池在提交瞬间被关闭）重建一次并重试
     */
    protected Future<?> submit(Runnable task) {
        synchronized (executorLock) {
            if (isShutdown(mainExecutors)) {
                mainExecutors = newExecutor();
            }
            try {
                return mainExecutors.submit(wrap(task));
            } catch (RejectedExecutionException e) {
                mainExecutors = newExecutor();
                return mainExecutors.submit(wrap(task));
            }
        }
    }

    /**
     * 包装任务，标记当前线程处于本类的工作线程中（供关池时判断是不是自己）
     */
    private Runnable wrap(Runnable task) {
        return () -> {
            IN_TASK_WORKER.set(Boolean.TRUE);
            try {
                task.run();
            } finally {
                IN_TASK_WORKER.remove();
            }
        };
    }

    /**
     * 线程池是否已关闭（含已被释放的情况）
     */
    protected boolean isExecutorShutdown() {
        synchronized (executorLock) {
            return isShutdown(mainExecutors);
        }
    }

    /**
     * 释放线程池，可重复调用；锁内只做引用交换，取消动作放在锁外，避免与 worker 抢锁
     */
    protected void releaseExecutor(boolean force) {
        ThreadPoolExecutor pool;
        synchronized (executorLock) {
            pool = mainExecutors;
            mainExecutors = null;
        }
        if (pool == null) return;
        if (force && !Boolean.TRUE.equals(IN_TASK_WORKER.get())) {
            pool.shutdownNow();
        } else {
            //force=false，或由池内线程自身触发停止时，只做优雅关闭，避免自己中断自己
            pool.shutdown();
        }
    }

    private boolean isShutdown(ThreadPoolExecutor pool) {
        return pool == null || pool.isShutdown() || pool.isTerminated();
    }

    public abstract void run(boolean isAsync) throws ExecutionException;

    public void stop(boolean force) {
        if (isRunning.compareAndSet(true, false)) {
            releaseExecutor(force);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

}
