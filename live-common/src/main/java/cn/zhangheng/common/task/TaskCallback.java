package cn.zhangheng.common.task;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/21 星期一 10:28
 * @version: 1.0
 * @description: 任务完成回调接口
 */
public interface TaskCallback<T> {
    void onSuccess(T result);
    void onFailure(Throwable e);
}
