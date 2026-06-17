package cn.zhangheng.common.task;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/14 星期日 14:25
 * @version: 1.0
 * @description:
 */
public interface AsyncTaskQueueMBean {
    // 任务计数
    long getTotalTaskCount();
    long getSuccessTaskCount();
    long getFailTaskCount();

    // 队列堆积
    int getWaitingQueueSize();

    // 耗时指标
    long getTotalCostTimeMs();
    long getMaxCostTimeMs();
    double getAvgCostTimeMs();

    // 配置与状态
    int getCorePoolSize();
    int getMaxRetryTimes();
    boolean getIsRunning();

    // 操作方法
    void resetMonitorMetrics();
}
