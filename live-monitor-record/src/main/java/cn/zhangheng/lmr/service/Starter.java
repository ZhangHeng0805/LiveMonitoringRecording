package cn.zhangheng.lmr.service;

import lombok.Setter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:25
 * @version: 1.0
 * @description:
 */
public abstract class Starter {
    @Setter
    protected int delayedSec = 0;

    public abstract void start();

    public abstract void stop();


}
