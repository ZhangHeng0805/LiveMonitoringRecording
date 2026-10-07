package cn.zhangheng.lmr.service;

import lombok.Getter;
import lombok.Setter;

import javax.management.ObjectName;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:25
 * @version: 1.0
 * @description:
 */
public abstract class Starter {
    @Getter
    private boolean isRunning = false;

    public void setRunning(boolean running) {
        isRunning = running;
        updateTime = System.currentTimeMillis();
    }

    @Setter
    protected int delayedSec = 0;
    protected long updateTime = 0;

    public abstract void start();

    public abstract void stop();

    public abstract void destroy();


}
