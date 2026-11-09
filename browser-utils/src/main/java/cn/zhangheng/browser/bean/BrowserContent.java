package cn.zhangheng.browser.bean;

import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/03/30 星期一 18:57
 * @version: 1.0
 * @description:
 */
public class BrowserContent {
    private final BrowserContext context;
    @Getter
    @JmxAttribute(desc = "浏览器实例UA")
    private final String userAgent;
    @Getter
    @JmxAttribute(desc = "线程名")
    private String threadName;
    @Getter
    @JmxAttribute(desc = "浏览器实例Cookie")
    private String cookie;

    // 🔥 修复：改用原子类型，保证线程安全
    private final AtomicLong lastUsedTimestamp = new AtomicLong(0);
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Consumer<Page> handler;

    @JmxOperation(desc = "设置浏览器实例cookie", paramDesc = {"cookie作用域", "cookie信息(为空时代表清空Cookie)"})
    public void setCookie(String targetDomain, String cookie) {
        if (cookie.isEmpty()) cookie = null;
        this.cookie = cookie;
        if (cookie != null) {
            context.addCookies(BrowserUtil.parseCookieString(targetDomain, cookie));
        } else {
            context.clearCookies();
        }
    }

    public BrowserContent(BrowserContext context, String userAgent) {
        this.context = context;
        this.userAgent = userAgent;

        // 监听：新页面创建
        handler = page -> {
            // 更新最后使用时间
            lastUsedTimestamp.set(System.currentTimeMillis());
            // 计数+1
            counter.incrementAndGet();
            if (!Objects.equals(threadName, Thread.currentThread().getName())) {
                threadName = Thread.currentThread().getName();
            }
        };
        context.onPage(handler);
    }

    public void off() {
        context.offPage(handler);
    }

    // --- 提供外部获取方法 ---
    public long getLastUsedTimestamp() {
        return lastUsedTimestamp.get();
    }

    @JmxAttribute(name = "lastTime", desc = "上次使用时间")
    public String getLastUsedTime() {
        return TimeUtil.toTime(getLastUsedTimestamp());
    }

    @JmxAttribute(name = "pageCount", desc = "使用页面次数")
    public int getPageCount() {
        return counter.get();
    }

    public synchronized BrowserContext getContext() {
        return context;
    }
}
