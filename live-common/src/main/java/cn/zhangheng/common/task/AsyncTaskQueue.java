package cn.zhangheng.common.task;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/21 星期一 10:14
 * @version: 1.0
 * @description:
 */
public class AsyncTaskQueue implements AsyncTaskQueueMBean {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    // ========== 配置参数 ==========
    private final int corePoolSize;
    private final int queueCapacity;
    // 重试配置
    private final int maxRetryTimes;
    private final long retryIntervalMs;

    // ========== 线程池核心组件 ==========
    private final PriorityBlockingQueue<Runnable> taskQueue;
    private final ThreadPoolExecutor executorService;
    private final AtomicBoolean isRunning;

    // ========== 监控指标埋点（原子安全） ==========
    // 任务总数、成功数、失败数
    private final AtomicLong totalTaskCount = new AtomicLong(0);
    private final AtomicLong successTaskCount = new AtomicLong(0);
    private final AtomicLong failTaskCount = new AtomicLong(0);
    // 耗时统计 毫秒
    private final AtomicLong totalCostTimeMs = new AtomicLong(0);
    private final AtomicLong maxCostTimeMs = new AtomicLong(0);

    // 全局线程命名计数器
    private static final AtomicInteger GLOBAL_THREAD_COUNTER = new AtomicInteger(0);

    // 线程工厂
    private static final ThreadFactory CUSTOM_THREAD_FACTORY = r -> {
        Thread thread = new Thread(r, "async-task-thread-" + GLOBAL_THREAD_COUNTER.getAndIncrement());
        thread.setDaemon(false);
        thread.setUncaughtExceptionHandler((t, e) ->
                LoggerFactory.getLogger(AsyncTaskQueue.class).error("线程[" + t.getName() + "]未捕获异常", e)
        );
        return thread;
    };

    // 拒绝策略
    private static final RejectedExecutionHandler QUEUE_FULL_HANDLER = (r, executor) -> {
        BlockingQueue<?> queue = executor.getQueue();
        throw new RejectedExecutionException(
                String.format("优先级队列已满,容量:%d,当前排队:%d", queue.remainingCapacity() + queue.size(), queue.size())
        );
    };

    // ========== MBean 注册 ==========
    private ObjectName mbeanObjectName;

    //--------------------------------------------------------------------------
    // 构造器 1：无重试默认配置
    public AsyncTaskQueue(int corePoolSize, int queueCapacity) {
        this(corePoolSize, queueCapacity, 0, 0);
    }

    // 构造器2：自定义重试参数
    public AsyncTaskQueue(int corePoolSize, int queueCapacity, int maxRetryTimes, long retryIntervalMs) {
        // 参数校验
        if (corePoolSize <= 0) throw new IllegalArgumentException("核心线程必须>0");
        if (queueCapacity <= 0) throw new IllegalArgumentException("队列容量必须>0");
        if (maxRetryTimes < 0) throw new IllegalArgumentException("重试次数不能为负数");
        if (retryIntervalMs < 0) throw new IllegalArgumentException("重试间隔不能为负数");

        this.corePoolSize = corePoolSize;
        this.queueCapacity = queueCapacity;
        this.maxRetryTimes = maxRetryTimes;
        this.retryIntervalMs = retryIntervalMs;
        this.isRunning = new AtomicBoolean(false);
        // 初始化比较器，泛型锁定Runnable，强转取PriorityTask
        Comparator<Runnable> priorityComparator = Comparator
                .<Runnable>comparingInt(r -> ((PriorityTask<?>) r).getPriority())
                .reversed()
                .thenComparingInt(r -> ((PriorityTask<?>) r).getCurrentRetry());

        // 优先级队列：容量限制 + 优先级倒序（数字越大优先级越高）
        this.taskQueue = new PriorityBlockingQueue<>(queueCapacity, priorityComparator);

        // 初始化线程池
        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                0L, TimeUnit.MILLISECONDS,
                taskQueue,
                CUSTOM_THREAD_FACTORY,
                QUEUE_FULL_HANDLER
        );
        executorService.prestartAllCoreThreads();

        // 注册JMX MBean
        registerMBean();
    }

    //--------------------------------------------------------------------------
    // 启动,关闭
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("异步任务队列启动，核心线程:{},队列容量:{},最大重试:{}", corePoolSize, queueCapacity, maxRetryTimes);
        }
    }

    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        if (isRunning.compareAndSet(true, false)) {
            log.debug("开始关闭任务队列，等待超时设置:{} {}", timeout, unit);
            executorService.shutdown();
            boolean finish = executorService.awaitTermination(timeout, unit);
            log.info(finish ? "任务队列关闭全部完成" : "关闭超时，存在未结束任务");
            // 线程池关闭完毕后注销MBean
            unregisterMBean();
            return finish;
        }
        return true;
    }

    public boolean shutdown() throws InterruptedException {
        return shutdown(30, TimeUnit.SECONDS);
    }

    public void forceShutdown() {
        if (isRunning.compareAndSet(true, false)) {
            log.warn("强制关闭队列");
            List<Runnable> unTasks = executorService.shutdownNow();
            log.warn("强制关闭丢弃任务数量:{}", unTasks.size());
            // 强制停止后注销MBean
            unregisterMBean();
        }
    }

    //--------------------------------------------------------------------------
    // 提交入口：带优先级、重试、回调
    public <T> void submit(Task<T> task, TaskCallback<T> callback) {
        submit(task, callback, 5); // 默认优先级5
    }

    public <T> void submit(Task<T> task, TaskCallback<T> callback, int priority) {
        submit(task, callback, priority, 0);
    }

    /**
     * 完整提交入口
     *
     * @param task         业务任务
     * @param callback     回调
     * @param priority     优先级 数字越大越优先
     * @param currentRetry 内部递归重试计数，外部传0
     */
    private <T> void submit(Task<T> task, TaskCallback<T> callback, int priority, int currentRetry) {
        if (!isRunning.get()) {
            throw new IllegalStateException("队列未启动，请先start()");
        }
        if (task == null) {
            throw new IllegalArgumentException("task不能为null");
        }

        PriorityTask<T> priorityTask = new PriorityTask<>(task, callback, priority, currentRetry);
        totalTaskCount.incrementAndGet(); // 总任务计数+1

        executorService.execute(priorityTask);
    }

    //--------------------------------------------------------------------------
    // 监控指标埋点写入
    private void recordSuccess(long costMs) {
        successTaskCount.incrementAndGet();
        totalCostTimeMs.addAndGet(costMs);
        // 更新最大耗时
        long oldMax;
        do {
            oldMax = maxCostTimeMs.get();
        } while (costMs > oldMax && !maxCostTimeMs.compareAndSet(oldMax, costMs));
    }

    private void recordFail() {
        failTaskCount.incrementAndGet();
    }

    //--------------------------------------------------------------------------
    // 获取队列大小
    public int getQueueSize() {
        return taskQueue.size();
    }

    //获取排队剩余容量
    public int getQueueRemainingCapacity() {
        return queueCapacity - taskQueue.size();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    //--------------------------------------------------------------------------
    // JMX MBean 注册
    private void registerMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            String mbeanName = "AsyncTaskQueue:type=AsyncTaskQueueMonitor,instance=" + System.identityHashCode(this);
            mbeanObjectName = new ObjectName(mbeanName);
            if (!mbs.isRegistered(mbeanObjectName)) {
                mbs.registerMBean(this, mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX MBean注册失败", e);
        }
    }

    // JMX注销（销毁时调用）
    private void unregisterMBean() {
        if (mbeanObjectName == null) {
            return;
        }
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            if (mbs.isRegistered(mbeanObjectName)) {
                mbs.unregisterMBean(mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX注销异常", e);
        }
        // 置空，防止重复注销
        mbeanObjectName = null;
    }

    //--------------------------------------------------------------------------
    // 内部包装：优先级任务载体
    private class PriorityTask<T> implements Runnable {
        final Task<T> task;
        final TaskCallback<T> callback;
        @Getter
        final int priority;//优先级，越大越高
        @Getter
        final int currentRetry;

        PriorityTask(Task<T> task, TaskCallback<T> callback, int priority, int currentRetry) {
            this.task = task;
            this.callback = callback;
            this.priority = priority;
            this.currentRetry = currentRetry;
        }


        @Override
        public void run() {
            long start = System.currentTimeMillis();
            try {
                T result = task.execute();
                recordSuccess(System.currentTimeMillis() - start);
                if (callback != null) {
                    try {
                        callback.onSuccess(result);
                    } catch (Throwable cbEx) {
                        log.error("成功回调异常", cbEx);
                    }
                }
            } catch (Throwable ex) {
                // 判断是否可以重试
                if (currentRetry < maxRetryTimes) {
                    log.warn("任务失败，准备重试{}次", currentRetry + 1);
                    // 间隔等待后重新入队
                    if (retryIntervalMs > 0) {
                        try {
                            Thread.sleep(retryIntervalMs);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    // 重新提交重试任务
                    submit(task, callback, priority, currentRetry + 1);
                } else {
                    recordFail();
                    log.error("任务重试次数耗尽({})，最终失败", maxRetryTimes, ex);
                    if (callback != null) {
                        callback.onFailure(ex);
                    }
                }
            }
        }
    }

    //==========================================================================
    // 实现 MBean 接口所有指标（供JMX读取）
    @Override
    public long getTotalTaskCount() {
        return totalTaskCount.get();
    }

    @Override
    public long getSuccessTaskCount() {
        return successTaskCount.get();
    }

    @Override
    public long getFailTaskCount() {
        return failTaskCount.get();
    }

    @Override
    public int getWaitingQueueSize() {
        return getQueueSize();
    }

    @Override
    public long getTotalCostTimeMs() {
        return totalCostTimeMs.get();
    }

    @Override
    public long getMaxCostTimeMs() {
        return maxCostTimeMs.get();
    }

    @Override
    public double getAvgCostTimeMs() {
        long suc = successTaskCount.get();
        if (suc == 0) return 0.0;
        return (double) totalCostTimeMs.get() / suc;
    }

    @Override
    public int getCorePoolSize() {
        return corePoolSize;
    }

    @Override
    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    @Override
    public boolean getIsRunning() {
        return isRunning.get();
    }

    @Override
    public void resetMonitorMetrics() {
        // JMX支持一键重置监控指标
        totalTaskCount.set(0);
        successTaskCount.set(0);
        failTaskCount.set(0);
        totalCostTimeMs.set(0);
        maxCostTimeMs.set(0);
        log.info("监控指标已手动重置");
    }
}
