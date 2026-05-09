package cn.zhangheng.browser;

import com.microsoft.playwright.BrowserContext;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/03/30 星期一 18:57
 * @version: 1.0
 * @description:
 */
public class BrowserContent {
    private final BrowserContext context;

    // 🔥 修复：改用原子类型，保证线程安全
    private final AtomicLong lastUsedTimestamp = new AtomicLong(0);
    private final AtomicInteger counter = new AtomicInteger(0);

    public BrowserContent(BrowserContext context) {
        this.context = context;

        // 监听：新页面创建
        context.onPage(page -> {
            // 🔥 修复：页面真正加载/导航时才更新活跃状态（更准确）
            page.onLoad(p -> {
                // 更新最后使用时间
                lastUsedTimestamp.set(System.currentTimeMillis());
                // 计数+1
                counter.incrementAndGet();
            });
        });
    }

    // --- 提供外部获取方法 ---
    public long getLastUsedTimestamp() {
        return lastUsedTimestamp.get();
    }

    public int getPageCount() {
        return counter.get();
    }

    public synchronized BrowserContext getContext() {
        return context;
    }
}
