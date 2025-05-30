package cn.zhangheng.common.bean;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import lombok.Getter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/23 星期五 15:31
 * @version: 1.0
 * @description:
 */
public abstract class Task {

    protected static final Log log = LogFactory.get();
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    @Getter
    protected Long startTime;
    @Getter
    protected Long endTime;
    protected ExecutorService mainExecutors;

    protected Task() {
        this.mainExecutors = Executors.newFixedThreadPool(1);
    }

    public abstract void run(boolean isAsync) throws ExecutionException;

    public abstract void stop(boolean force);

    public boolean isRunning() {
        return isRunning.get();
    }

}
