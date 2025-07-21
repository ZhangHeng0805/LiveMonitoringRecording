package cn.zhangheng.common.task;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/21 星期一 10:27
 * @version: 1.0
 * @description: 异步任务接口，所有需要加入队列的任务都要实现此接口
 */
@FunctionalInterface
public interface Task<T> {
    T execute() throws Exception;
}
