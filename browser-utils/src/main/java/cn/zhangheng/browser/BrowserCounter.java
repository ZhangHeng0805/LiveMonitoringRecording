package cn.zhangheng.browser;

import lombok.Data;

import java.util.Objects;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/04/13 星期一 19:41
 * @version: 1.0
 * @description:
 */
@Data
public class BrowserCounter {
    private String url;
    private String threadName;
    private int count;
    private long lastTimestamp;

    public BrowserCounter(String url) {
        this.threadName = Thread.currentThread().getName();
        this.url = url;
    }

    public void add() {
        count++;
        lastTimestamp = System.currentTimeMillis();
        if (!Objects.equals(threadName, Thread.currentThread().getName())) {
            threadName = Thread.currentThread().getName();
        }
    }
}
