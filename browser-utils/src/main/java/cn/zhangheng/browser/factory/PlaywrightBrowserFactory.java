package cn.zhangheng.browser.factory;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.zhangheng.browser.bean.BrowserContent;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import cn.zhangheng.common.jmx.JmxRegisterUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import com.zhangheng.util.UserAgentUtil;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.management.MalformedObjectNameException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/06 星期五 18:21
 * @version: 1.0
 * @description:
 */
@Slf4j
public class PlaywrightBrowserFactory {
    private static final String JmxType = "PlaywrightBrowser";
    private static volatile Playwright playwright = null;
    private static volatile Browser browser = null;
    @Getter
    @JmxAttribute(desc = "是否浏览器无头模式（是否隐藏浏览器）")
    private static volatile boolean headless = true;// 无头模式：生产环境建议true，仅在Browser重建时生效，运行中修改无效
    private static volatile TimedCache<String, MyBrowser> browsersCache = null;
    //缓存版本号
    @Getter
    @JmxAttribute(desc = "版本号")
    private static volatile int cacheVersion = 0;
    private static volatile UserAgentUtil userAgentUtil = null;
    // key 粒度锁：同一个 key 的上下文创建串行；不同 key 之间互不阻塞（不再用类锁串行所有房间）
    private static final ConcurrentHashMap<String, Object> KEY_LOCKS = new ConcurrentHashMap<>();
    // Hutool TimedCache 不是线程安全容器：所有缓存读写走这把短锁，锁内不做任何网络/进程调用
    private static final Object CACHE_LOCK = new Object();
    // 仅串行“新建上下文”这一瞬间：Playwright 建 context 内部状态较重，避免并发创建失败；
    // 已有缓存的取用不经过这把锁，所以不会退回全局串行
    private static final Object CONTEXT_LOCK = new Object();
    // Browser空闲延时关闭配置：所有context清空后，等待多久再关闭浏览器进程
    private static final long BROWSER_IDLE_DELAY_MS = 600_000; // 空闲到期后关Chromium
    // 空闲关闭任务的独立锁：避免和类锁/缓存锁形成交叉等待
    private static final Object IDLE_LOCK = new Object();
    private static ScheduledExecutorService idleScheduler;
    private static ScheduledFuture<?> idleCloseTask;
    //总计数
    private static final AtomicLong count = new AtomicLong(0);
    @Getter
    @JmxAttribute(desc = "浏览器Cookie信息")
    private static String cookie;
    // Browser 进程启动时间（毫秒），0 表示当前没有运行中的浏览器
    @Getter
    @JmxAttribute(desc = "浏览器启动时间")
    private static volatile long browserStartMs = 0;
    // 空闲关闭浏览器进程的计划时间（毫秒），0 表示没有倒计时
    @Getter
    @JmxAttribute(desc = "空闲关闭倒计时(秒)")
    private static volatile long idleCloseAtMs = 0;

    @JmxOperation(desc = "设置浏览器所有实例的cookie", paramDesc = {"cookie信息(为空时代表清空Cookie)"})
    public static void setAllCookie(String cookie) {
        if (cookie.isEmpty()) cookie = null;
        PlaywrightBrowserFactory.cookie = cookie;
        String targetDomain = ".douyin.com";//目前作用域是抖音
        // 先快照 key，真正设置 Cookie 时不持缓存锁（Playwright 调用可能慢）
        for (String key : cacheKeys()) {
            MyBrowser myBrowser = cacheGet(key);
            if (myBrowser == null) continue;
            BrowserContent content = myBrowser.getContent();
            try {
                if (cookie != null) {
                    content.setCookie(targetDomain, cookie);
                } else {
                    content.getContext().clearCookies();
                }
            } catch (Throwable e) {
                log.warn("设置浏览器Cookie失败, key={}: {}", key, ThrowableUtil.getAllCauseMessage(e));
            }
        }
    }

    static {
        try {
            JmxRegisterUtil.register(new PlaywrightBrowserFactory(), JmxType);
        } catch (MalformedObjectNameException e) {
            log.warn("JMX注册失败!{}", ThrowableUtil.getAllCauseMessage(e));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isRunning()) {
                destroy();
            }
        }));
    }

    @JmxOperation(desc = "设置浏览器无头模式", paramDesc = {"是否隐藏浏览器"})
    public static synchronized void setHeadless(boolean headless) {
        PlaywrightBrowserFactory.headless = headless;
    }

    @JmxAttribute(name = "count", desc = "总请求次数")
    public long getCount() {
        return count.get();
    }

    /**
     * 静态快照用的上下文统计（JMX 的 getCacheCount 也复用这里）
     */
    private static List<Counter> collectCounters() {
        List<Counter> list = new ArrayList<>();
        for (String key : cacheKeys()) {
            MyBrowser myBrowser = cacheGet(key);
            if (myBrowser == null) continue;
            BrowserContent content = myBrowser.getContent();
            Counter counter = new Counter();
            counter.setKey(key);
            counter.setCount(content.getPageCount());
            counter.setThreadName(content.getThreadName());
            counter.setLastTime(content.getLastUsedTimestamp());
            counter.setUserAgent(content.getUserAgent());
            String cookieValue = content.getCookie();
            counter.setCookieConfigured(cookieValue != null && !cookieValue.isEmpty());
            list.add(counter);
        }
        return list;
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
        // 只有 Playwright/Browser/缓存不可用时才进类锁重建，正常取上下文不再所有房间全局排队
        ensureReady();

        synchronized (lockOf(key)) {
            try {
                if (forceRefresh) {
                    cacheRemove(key);
                }

                MyBrowser myBrowser = cacheGet(key);
                boolean isContextValid = false;
                Browser currentBrowser = browser;
                if (currentBrowser == null) {
                    throw new IllegalStateException("浏览器正在关闭或重建，请稍后重试");
                }
                if (myBrowser != null) {
                    BrowserContext context = myBrowser.getContent().getContext();
                    //判断浏览器是否一致
                    if (context != null && context.browser().equals(currentBrowser)) {
                        isContextValid = true;
                    }
                }
                if (forceRefresh || !isContextValid) {
                    if (myBrowser != null) {
                        //关闭旧的
                        cacheRemove(key);
                    }

                    UserAgentUtil uaUtil = userAgentUtil;
                    if (uaUtil == null) {
                        throw new IllegalStateException("浏览器正在关闭或重建，请稍后重试");
                    }
                    String userAgent = uaUtil.get();
                    Browser.NewContextOptions options = new Browser.NewContextOptions()
                            .setUserAgent(userAgent)
                            .setLocale("zh-CN")
                            .setTimezoneId("Asia/Shanghai");

                    BrowserContext context;
                    synchronized (CONTEXT_LOCK) {
                        context = currentBrowser.newContext(options);
                    }

                    BrowserContent content = new BrowserContent(context, userAgent);
                    myBrowser = new MyBrowser(content);
                    cachePut(key, myBrowser, expirationMs);
                    try {
                        JmxRegisterUtil.register(content, JmxType, "name", key);
                    } catch (MalformedObjectNameException e) {
                        log.warn("JMX注册失败!{}", ThrowableUtil.getAllCauseMessage(e));
                    }
                }
                return myBrowser;
            } finally {
                count.incrementAndGet();
            }
        }
    }

    /**
     * key 粒度锁对象：锁数量与房间数一致，不做回收（回收会让并发线程拿到不同锁）
     */
    private static Object lockOf(String key) {
        return KEY_LOCKS.computeIfAbsent(key == null ? "" : key, k -> new Object());
    }

    /**
     * 确保 Playwright / Browser / 缓存可用。
     * 快路径只读 volatile 标记；只有真正需要重建时才进类锁，避免所有房间共用一把全局锁串行。
     */
    private static void ensureReady() {
        if (isRunning() && browsersCache != null && userAgentUtil != null) {
            return;
        }
        synchronized (PlaywrightBrowserFactory.class) {
            if (isRunning() && browsersCache != null && userAgentUtil != null) {
                return;
            }
            // 旧 Browser 已断开，或缓存/UA 状态不完整：统一清掉再重建
            if (browser != null || browsersCache != null) {
                closeBrowser();
            }
            init();
        }
    }

    private static MyBrowser cacheGet(String key) {
        synchronized (CACHE_LOCK) {
            return browsersCache == null ? null : browsersCache.get(key);
        }
    }

    private static void cachePut(String key, MyBrowser myBrowser, long expirationMs) {
        synchronized (CACHE_LOCK) {
            if (browsersCache != null) {
                browsersCache.put(key, myBrowser, expirationMs);
            }
        }
    }

    private static void cacheRemove(String key) {
        synchronized (CACHE_LOCK) {
            if (browsersCache != null) {
                browsersCache.remove(key);
            }
        }
    }

    private static List<String> cacheKeys() {
        synchronized (CACHE_LOCK) {
            if (browsersCache == null || browsersCache.isEmpty()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(browsersCache.keySet());
        }
    }

    private static boolean cacheIsEmpty() {
        synchronized (CACHE_LOCK) {
            return browsersCache == null || browsersCache.isEmpty();
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
            browserStartMs = System.currentTimeMillis();
            log.info("浏览器进程启动成功");
        }
        if (idleScheduler == null || idleScheduler.isShutdown()) {
            idleScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "browser-idle-close-thread");
                t.setDaemon(true);
                return t;
            });
        }
        synchronized (CACHE_LOCK) {
            if (browsersCache == null) {
                cacheVersion++;
                final long currentVer = cacheVersion;
                TimedCache<String, MyBrowser> cache = CacheUtil.newTimedCache(60_000);
                cache.setListener((key, myBrowser) -> {
                    // 不是当前最新版本的缓存，直接丢弃回调（旧缓存滞后prune回调拦截）
                    if (currentVer != cacheVersion) {
                        return;
                    }
                    // ✅ 异步关闭，不在缓存锁里阻塞
                    CompletableFuture.runAsync(() -> {
                        try {
                            JmxRegisterUtil.unregister(JmxRegisterUtil.Domain, JmxType, "name", key);
                            myBrowser.close();
                            log.info("异步关闭BrowserContext成功！ key={}", key);
                        } catch (Throwable e) {
                            log.error("异步关闭BrowserContext异常, key={}", key, e);
                        }
                    });
                    // 检查缓存是否已经空了
                    if (cacheIsEmpty()) {
                        scheduleBrowserIdleClose();
                    }
                });
                //开启定时过期扫描
                cache.schedulePrune(2_000);
                browsersCache = cache;
            }
        }
        if (userAgentUtil == null) {
            userAgentUtil = UserAgentUtil.create(true, false);
        }

    }

    /**
     * 调度空闲延时关闭Browser：缓存全部为空时调用
     */
    private static void scheduleBrowserIdleClose() {
        synchronized (IDLE_LOCK) {
            // 线程池已经关闭就直接返回，不再提交
            if (idleScheduler == null || idleScheduler.isShutdown()) {
                return;
            }
            if (idleCloseTask != null && !idleCloseTask.isDone()) {
                // 已有倒计时，不重复创建
                return;
            }
            log.info("所有上下文已回收，{}ms后关闭Browser浏览器", BROWSER_IDLE_DELAY_MS);
            idleCloseAtMs = System.currentTimeMillis() + BROWSER_IDLE_DELAY_MS;
            idleCloseTask = idleScheduler.schedule(() -> {
                idleCloseAtMs = 0;
                synchronized (PlaywrightBrowserFactory.class) {
                    // 二次校验：防止在等待期间又来了新任务
                    if (cacheIsEmpty()) {
                        log.info("浏览器空闲超时，关闭Browser");
                        closeBrowser();
                    }
                }
            }, BROWSER_IDLE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 取消空闲关闭倒计时：有新任务进来的时候调用
     */
    private static void cancelIdleCloseTask() {
        synchronized (IDLE_LOCK) {
            idleCloseAtMs = 0;
            if (idleCloseTask != null && !idleCloseTask.isDone()) {
                idleCloseTask.cancel(false);
                idleCloseTask = null;
            }
        }
    }

    @JmxOperation(desc = "关闭浏览器")
    public static void closeBrowser() {
        if (browser == null && browsersCache == null) {
            return;
        }
        cancelIdleCloseTask();
        // 先关闭浏览器（会自动关闭所有页面和上下文）
        if (isRunning()) {
            try {
                browser.close();
                log.info("关闭浏览器");
            } catch (Throwable e) {
                if (e instanceof NegativeArraySizeException) {
                    log.warn("关闭浏览器失败:{}", ThrowableUtil.getAllCauseMessage(e));//忽略异常
                } else {
                    log.error("关闭浏览器失败", e);
                }
            } finally {
                browser = null; // 标记为null，避免重复操作
                browserStartMs = 0;
            }
        }
        synchronized (CACHE_LOCK) {
            if (browsersCache != null) {
                browsersCache.clear();
                browsersCache.cancelPruneSchedule();
            }
            browsersCache = null;
        }
        if (idleScheduler != null) {
            idleScheduler.shutdownNow();
        }
        browserStartMs = 0;
        userAgentUtil = null;
    }

    @JmxOperation(desc = "销毁浏览器进程")
    public static synchronized void destroy() {
        if (playwright == null && browser == null && browsersCache == null) {
            return;
        }
        // 先关闭浏览器（会自动关闭所有页面和上下文）
        closeBrowser();
        // 最后关闭Playwright
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Throwable e) {
                log.error("关闭Playwright失败", e);
            } finally {
                playwright = null; // 标记为null
            }
        }
        log.info("销毁Chromium浏览器进程");
    }

    /**
     * 检查浏览器是否运行中
     */
    @JmxAttribute(desc = "是否运行")
    public static boolean isRunning() {
        return browser != null && browser.isConnected();
    }

    @Data
    public static class Counter {
        @JmxAttribute(desc = "标识")
        private String key;
        @JmxAttribute(desc = "计数")
        private int count;
        @JmxAttribute(desc = "线程名")
        private String threadName;
        @JmxAttribute(desc = "上次使用时间")
        private String lastTime;
        @JmxAttribute(desc = "浏览器UA")
        private String userAgent;
        @JmxAttribute(desc = "是否设置了Cookie")
        private boolean cookieConfigured;

        public void setLastTime(long lastTimestamp) {
            this.lastTime = TimeUtil.toTime(lastTimestamp);
        }
    }

    /**
     * 浏览器组件运行快照：供面板「浏览器组件」界面展示。
     * 只读当前状态，不会触发浏览器启动（没启动时 running=false、路径为空）。
     */
    public static Map<String, Object> statusSnapshot(boolean isDesensitize) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean running = isRunning();
        data.put("running", running);
        data.put("headless", headless);
        data.put("cacheVersion", cacheVersion);
        data.put("totalRequests", count.get());
        List<Counter> counters = collectCounters();
        data.put("cacheSize", counters.size());
        data.put("contexts", counters);
        data.put("browserStartTime", browserStartMs > 0 ? TimeUtil.toTime(browserStartMs) : null);
        data.put("browserUptimeSec",
                browserStartMs > 0 ? Math.max(0, (System.currentTimeMillis() - browserStartMs) / 1000) : 0);
        data.put("idleCloseRemainSec",
                idleCloseAtMs > 0 ? Math.max(0, (idleCloseAtMs - System.currentTimeMillis()) / 1000) : 0);
        data.put("idleCloseDelaySec", BROWSER_IDLE_DELAY_MS / 1000);
        data.put("playwrightVersion", isDesensitize ? "***" : playwrightVersion());
        data.put("chromiumPath", isDesensitize ? "***" : chromiumExecutablePath());
        return data;
    }

    private static volatile String playwrightVersionCache = null;

    private static String playwrightVersion() {
        if (playwrightVersionCache != null) {
            return playwrightVersionCache;
        }
        try {
            Package pkg = Playwright.class.getPackage();
            String version = pkg == null ? null : pkg.getImplementationVersion();
            playwrightVersionCache = version == null ? "未知" : version;
        } catch (Throwable e) {
            playwrightVersionCache = "未知";
        }
        return playwrightVersionCache;
    }

    private static volatile String chromiumPathCache = null;

    private static String chromiumExecutablePath() {
        if (chromiumPathCache != null) {
            return chromiumPathCache;
        }
        try {
            Playwright pw = playwright;
            if (pw == null) {
                return null;
            }
            chromiumPathCache = pw.chromium().executablePath();
        } catch (Throwable e) {
            log.debug("读取Chromium路径失败：{}", ThrowableUtil.getAllCauseMessage(e));
            return null;
        }
        return chromiumPathCache;
    }

    /**
     * 重启浏览器：销毁 Playwright/Chromium 后立即按当前 headless 配置重新启动
     */
    @JmxOperation(desc = "重启浏览器（关闭后立即重新启动）")
    public static synchronized void restart() {
        destroy();
        init();
        log.info("浏览器已重启，headless={}", headless);
    }
}
