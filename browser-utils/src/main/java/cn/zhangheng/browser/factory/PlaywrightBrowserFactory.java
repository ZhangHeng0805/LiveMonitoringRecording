package cn.zhangheng.browser.factory;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.zhangheng.browser.bean.BrowserContent;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import cn.zhangheng.common.jmx.JmxRegisterUtil;
import cn.zhangheng.common.util.UserAgentUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/06 星期五 18:21
 * @version: 1.0
 * @description:
 */
@Slf4j
public class PlaywrightBrowserFactory {

    private static Playwright playwright = null;
    private static volatile Browser browser = null;
    @Setter
    private static boolean headless = true;// 无头模式：生产环境建议true，仅在Browser重建时生效，运行中修改无效
    private static TimedCache<String, MyBrowser> browsersCache = null;
    //缓存版本号
    private static int cacheVersion = 0;
    private static UserAgentUtil userAgentUtil = null;
    // key粒度锁，防止同一个key并发创建多个BrowserContext
    private static final ConcurrentHashMap<String, Lock> keyLockMap = new ConcurrentHashMap<>();
    // Browser空闲延时关闭配置：所有context清空后，等待多久再关闭浏览器进程
    private static final long BROWSER_IDLE_DELAY_MS = 300_000; // 示例：空闲到期后关Chromium
    private static ScheduledExecutorService idleScheduler;
    private static ScheduledFuture<?> idleCloseTask;
    private static final AtomicLong count = new AtomicLong(0);
    private static ObjectName objectName = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isRunning()) {
                destroy();
            }
        }));
    }


    @JmxAttribute(desc = "请求次数")
    public long getCount() {
        return count.get();
    }

    @JmxAttribute(desc = "浏览器缓存统计")
    public Map<String, Integer> getCacheCount() {
        Map<String, Integer> data = new HashMap<>();
        if (browsersCache == null) return data;
        for (String key : browsersCache.keySet()) {
            MyBrowser myBrowser = browsersCache.get(key);
            BrowserContent content = myBrowser.getContent();
            data.put(key,content.getPageCount());
        }
        return data;
    }

    /**
     * 获取一个浏览器实例
     *
     * @param key          唯一标识，根据唯一标识可以复用
     * @param expirationMs 空闲过期时间，过期后自动关闭实例
     * @param forceRefresh 是否强制刷新实例
     * @return
     */
    public static MyBrowser getBrowser(String key, long expirationMs, boolean forceRefresh) {
        // 只要有人来拿任务上下文，说明有活，取消空闲关闭倒计时
        cancelIdleCloseTask();

        // DCL 检查Browser是否存活
        if (!isRunning()) {
            synchronized (PlaywrightBrowserFactory.class) {
                if (!isRunning()) {
                    // 旧Browser还连接着才关闭；已经断开直接重建
                    if (browser != null) {
                        closeBrowser();
                    }
                    init();
                }
            }
        }
        // key粒度锁，解决 check-then-act 并发问题
        Lock lock = keyLockMap.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {

            if (forceRefresh) {
                browsersCache.remove(key);
            }

            MyBrowser myBrowser = browsersCache.get(key);
            boolean isContextValid = false;
            if (myBrowser != null) {
                BrowserContext context = myBrowser.getContent().getContext();
                //判断浏览器是否一致
                if (context != null && context.browser().equals(browser)) {
                    isContextValid = true;
                }
            }
            if (forceRefresh || !isContextValid) {
                if (myBrowser != null) {
                    //关闭旧的
                    browsersCache.remove(key);
                }
                int[] vp = UserAgentUtil.PC_VIEWPORTS[RandomUtil.randomInt(UserAgentUtil.PC_VIEWPORTS.length)];
                String userAgent = userAgentUtil.get();
                Browser.NewContextOptions options = new Browser.NewContextOptions()
                        .setUserAgent(userAgent)
                        .setViewportSize(vp[0], vp[1]);
                BrowserContext context = browser.newContext(options);

                BrowserContent content = new BrowserContent(context,userAgent);
                myBrowser = new MyBrowser(content);
                browsersCache.put(key, myBrowser, expirationMs);
            }
            return myBrowser;
        } finally {
            lock.unlock();
            count.incrementAndGet();
        }
    }

    private static void init() {
        if (playwright == null) {
            // 先初始化Playwright
            playwright = Playwright.create();
        }
        if (browser == null || !browser.isConnected()) {
            // 再初始化浏览器（若失败，需关闭已创建的Playwright）
            browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(headless));
            log.info("Chromium浏览器进程启动成功");
        }
        if (idleScheduler == null || idleScheduler.isShutdown()) {
            idleScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "browser-idle-close-thread");
                t.setDaemon(true);
                return t;
            });
        }
        if (browsersCache == null) {
            cacheVersion++;
            final long currentVer = cacheVersion;
            browsersCache = CacheUtil.newTimedCache(60_000);
            //开启定时过期扫描
            browsersCache.schedulePrune(2_000);

            browsersCache.setListener((key, myBrowser) -> {
                // 不是当前最新版本的缓存，直接丢弃回调（旧缓存滞后prune回调拦截）
                if (currentVer != cacheVersion) {
                    return;
                }
                // ✅ 异步关闭，不在缓存锁里阻塞
                CompletableFuture.runAsync(() -> {
                    try {
                        myBrowser.close();
                        log.info("异步关闭BrowserContext成功！ key={}", key);
                    } catch (Throwable e) {
                        log.error("异步关闭BrowserContext异常, key={}", key, e);
                    }
                });
                // 移除锁，防止keyLockMap无限膨胀
                keyLockMap.remove(key);
                // 检查缓存是否已经空了
                if (browsersCache.isEmpty()) {
                    scheduleBrowserIdleClose();
                }
            });
        }
        if (userAgentUtil == null) {
            userAgentUtil = UserAgentUtil.create(true);
        }
        if (objectName == null) {
            try {
                objectName = JmxRegisterUtil.register(new PlaywrightBrowserFactory(), "PlaywrightBrowser");
            } catch (MalformedObjectNameException e) {
                log.warn("JMX注册失败!{}", ThrowableUtil.getAllCauseMessage(e));
            }
        }
    }

    /**
     * 调度空闲延时关闭Browser：缓存全部为空时调用
     */
    private static synchronized void scheduleBrowserIdleClose() {
        // 线程池已经关闭就直接返回，不再提交
        if (idleScheduler == null || idleScheduler.isShutdown()) {
            return;
        }
        if (idleCloseTask != null && !idleCloseTask.isDone()) {
            // 已有倒计时，不重复创建
            return;
        }
        log.info("所有上下文已回收，{}ms后关闭Chromium浏览器进程", BROWSER_IDLE_DELAY_MS);
        idleCloseTask = idleScheduler.schedule(() -> {
            synchronized (PlaywrightBrowserFactory.class) {
                // 二次校验：防止在等待期间又来了新任务
                if (browsersCache != null && browsersCache.isEmpty()) {
                    log.info("浏览器空闲超时，关闭Chromium进程");
                    destroy();
                }
            }
        }, BROWSER_IDLE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消空闲关闭倒计时：有新任务进来的时候调用
     */
    private static synchronized void cancelIdleCloseTask() {
        if (idleCloseTask != null && !idleCloseTask.isDone()) {
            idleCloseTask.cancel(false);
            idleCloseTask = null;
            log.debug("收到新任务，取消浏览器空闲关闭计时");
        }
    }

    private static void closeBrowser() {
        // 先关闭浏览器（会自动关闭所有页面和上下文）
        if (isRunning()) {
            try {
                browser.close();
            } catch (Throwable e) {
                if (e instanceof NegativeArraySizeException) {
                    log.warn("关闭浏览器失败:{}", ThrowableUtil.getAllCauseMessage(e));//忽略异常
                } else {
                    log.error("关闭浏览器失败", e);
                }
            } finally {
                browser = null; // 标记为null，避免重复操作
            }
        }
        browsersCache.clear();
    }

    @JmxOperation(desc = "销毁关闭浏览器")
    public static synchronized void destroy() {
        if (playwright == null && browser == null && browsersCache == null) {
            return;
        }
        cancelIdleCloseTask();
        // 先关闭浏览器（会自动关闭所有页面和上下文）
        closeBrowser();
        // 再关闭Playwright
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Throwable e) {
                log.error("关闭Playwright失败", e);
            } finally {
                playwright = null; // 标记为null
            }
        }
        browsersCache.cancelPruneSchedule();
        idleScheduler.shutdownNow();
        browsersCache = null;
        userAgentUtil = null;
        count.set(0);
        JmxRegisterUtil.unregister(objectName);
        objectName = null;
        log.info("销毁Chromium浏览器进程");
    }

    /**
     * 检查浏览器是否运行中
     */
    @JmxAttribute(desc = "是否运行")
    public static boolean isRunning() {
        return browser != null && browser.isConnected();
    }
}
