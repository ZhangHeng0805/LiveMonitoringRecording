package cn.zhangheng.browser;

import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/21 星期日 03:47
 * @version: 1.0
 * @description: 使用真实浏览器发起请求
 */
public class PlaywrightBrowser implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowser.class);
    // 重量级资源，volatile确保多线程可见性
    private Playwright playwright;
    private volatile Browser browser;

    // 线程安全的计数（替代int，避免多线程计数错误）
    @Getter
    private final AtomicInteger allPageCount = new AtomicInteger(0);

    @Setter
    private Boolean isPageClear; // 是否每次打开页面时重置状态，null时默认每20次清理

    @Getter
    private Integer updateContextCounts; //更新BrowserContext的请求次数，请求次数达到时自动更换BrowserContext
    private AutoClearContextThread autoClearContextThread = null;

    public synchronized void startAutoClearContextThread(int interval) {
        if (autoClearContextThread == null) {
            autoClearContextThread = new AutoClearContextThread(interval, contents);
        }
    }

    public void stopAutoClearContextThread() {
        if (autoClearContextThread != null && autoClearContextThread.isRunning()) {
            autoClearContextThread.stop();
            autoClearContextThread = null;
        }
    }

    public void setUpdateContextCounts(Integer updateContextCounts) {
        if (updateContextCounts != null && updateContextCounts > 0) {
            this.updateContextCounts = Math.max(updateContextCounts, 10);
        } else {
            this.updateContextCounts = null;
        }
    }

    @Setter
    private double navigateTimeoutMs = 30000;

    private final MyThreadLocal<BrowserContent> contents = new MyThreadLocal<>(new MyThreadLocal.Listener<BrowserContent>() {
        @Override
        public void beforeClear(ConcurrentHashMap<String, BrowserContent> map) {
            for (Map.Entry<String, BrowserContent> entry : map.entrySet()) {
                try {
                    BrowserContext browserContext = entry.getValue().getContext();
                    if (browserContext.browser().isConnected()) {
                        browserContext.close();
                        log.debug("{}清除BrowserContext，已关闭", entry.getKey());
                    }
                } catch (Throwable e) {
                    log.error("Error clearing browser context: {}", e.getMessage());
                }
            }
        }

        @Override
        public void removed(String threadId, BrowserContent value) {
            BrowserContext valueContext = value.getContext();
            if (valueContext != null && valueContext.browser().isConnected()) {
                try {
                    valueContext.close();
                    log.debug("线程[{}]的BrowserContext已关闭", threadId);
                } catch (Throwable e) {
                    log.error("Error closing browser context[{}]: {}", threadId, e.getMessage());
                }
            }
        }
    });

    public Map<String, Integer> getAllCount() {
        Map<String, Integer> count = new HashMap<>();
        count.put("allPageCount", getAllPageCount().get());
        for (Map.Entry<String, BrowserContent> entry : contents.getAll().entrySet()) {
            count.put("[" + entry.getKey() + "]", entry.getValue().getPageCount());
        }
        return count;
    }


    // 反自动化检测初始化脚本
//    public static final String initScript =
//            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
//                    "delete window.chrome;\n" +
//                    "Object.defineProperty(navigator, 'plugins', { get: () => [\n" +
//                    "  { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },\n" +
//                    "  { name: 'Widevine Content Decryption Module', filename: 'widevinecdm.dll' }\n" +
//                    "] });\n" +
//                    "Object.defineProperty(navigator, 'mimeTypes', { get: () => [\n" +
//                    "  { type: 'application/pdf', suffixes: 'pdf' }\n" +
//                    "] });\n" +
//                    "Object.defineProperty(screen, 'width', { get: () => 1920 });\n" +
//                    "Object.defineProperty(screen, 'height', { get: () => 1080 });\n" +
//                    "Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });\n" +
//                    "Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });\n" +
//                    "document.addEventListener('DOMContentLoaded', () => {\n" +
//                    "  const event = new MouseEvent('click', { clientX: 100, clientY: 200, bubbles: true });\n" +
//                    "  document.body.dispatchEvent(event);\n" +
//                    "});";

    private final String userAgent;
    private final boolean headless;
    private final UserAgentUtil userAgentUtil;

    public PlaywrightBrowser(String userAgent) {
        this(userAgent, true);
    }

    public PlaywrightBrowser(String userAgent, boolean headless) {
        this.userAgent = userAgent;
        this.headless = headless;
        this.userAgentUtil = new UserAgentUtil();
        try {
            // 先初始化Playwright
            this.playwright = Playwright.create();
            // 再初始化浏览器（若失败，需关闭已创建的Playwright）
            this.browser = playwright.chromium().launch(getLaunchOptions(userAgent, headless));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (isRunning()) {
                    //程序关闭，自动关闭浏览器
                    log.debug("程序关闭，自动关闭浏览器");
                    close();
                }
            }));
        } catch (Exception e) {
            log.error("初始化Playwright/Browser失败", e);
            // 清理已创建的资源
            close();
            throw new RuntimeException("初始化Playwright失败", e);
        }
    }

    /**
     * 浏览器启动配置
     */
    public static BrowserType.LaunchOptions getLaunchOptions(String userAgent, boolean headless) {
        return new BrowserType.LaunchOptions()
                .setHeadless(headless) // 无头模式：生产环境建议true
                .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--user-agent=" + userAgent,
                        "--no-sandbox",
                        "--disable-gpu",
                        "--disable-dev-shm-usage", // 解决容器环境内存限制问题
                        "--remote-debugging-port=0" // 禁用远程调试端口，避免安全风险
                ))
                .setSlowMo(100); // 轻微延迟，模拟真人操作（可选）
    }

    public BrowserType.LaunchOptions getLaunchOptions(boolean headless) {
        return getLaunchOptions(userAgent, headless);
    }

    public BrowserType.LaunchOptions getLaunchOptions() {
        return getLaunchOptions(userAgent, headless);
    }

    /**
     * 检查浏览器是否运行中
     */
    public boolean isRunning() {
        return browser != null && browser.isConnected();
    }

    public Page newPage() {
        return newPage(userAgentUtil.get());
    }

    /**
     * 创建新页面（自动处理浏览器断开重连）
     */
    public Page newPage(String userAgent) {
        // 双重检查：确保浏览器可用
        if (!isRunning()) {
            log.warn("浏览器已断开，尝试重新启动...");
            synchronized (this) {
                if (!isRunning()) {
                    // 安全关闭旧浏览器（若存在）
                    if (browser != null) {
                        try {
                            browser.close();
                            log.debug("关闭旧浏览器成功");
                        } catch (Exception e) {
                            log.warn("关闭旧浏览器失败", e);
                        }
                        browser = null;
                    }
                }
                // 重新创建浏览器
                try {
                    browser = playwright.chromium().launch(getLaunchOptions());
                    log.info("浏览器重新启动成功");
                } catch (Exception e) {
                    throw new RuntimeException("重新启动浏览器失败", e);
                } finally {
                    contents.clear();
                }
            }
        }
        boolean isContextValid = false;
        BrowserContext browserContext = null;
        if (contents.get() != null) {
            browserContext = contents.get().getContext();
            isContextValid = (browserContext != null)
                    && browserContext.browser().equals(browser);
        }
//        String name = Thread.currentThread().getName();
//        log.debug(name + "---" + isContextValid);
        if (!isContextValid) {
            if (browserContext != null) {
                try {
                    browserContext.close();
                    log.debug("关闭旧 Context 成功");
                } catch (Exception e) {
                    log.warn("关闭旧 Context 失败", e);
                }
            }
            // 创建新 Context 并覆盖 ThreadLocal
            Browser.NewContextOptions options = new Browser.NewContextOptions()
                    .setUserAgent(userAgent)
                    .setViewportSize(1920, 1080) // 模拟PC端分辨率
                    .setJavaScriptEnabled(true)
                    .setIgnoreHTTPSErrors(true)
                    // 禁用录像/截图缓存
                    .setRecordVideoDir(null)
                    .setRecordHarPath(null)
                    // 限制缓存大小
//                    .setExtraHTTPHeaders(MapUtil.of("Cache-Control", "no-cache"))
                    ;

            synchronized (this) {
                browserContext = browser.newContext(options);
            }
            BrowserContent content = new BrowserContent(browserContext);
            contents.set(content);
        }
        Page page = browserContext.newPage();
        // 创建新页面并注入反检测脚本
//        page.addInitScript(initScript);
        return page;
    }

    // 解析 Cookie 字符串为 Playwright 的 Cookie 对象
    public static List<Cookie> parseCookieString(String targetDomain, String cookieStr) {
        // 例如：访问抖音网页版填 ".douyin.com"（带点表示所有子域名生效），访问其他网站需替换
//        String targetDomain = ".douyin.com";

        // 拆分Cookie字符串并批量创建Cookie对象
        List<Cookie> cookieList = new ArrayList<>();
        // 按 ";" 拆分（处理可能的空格差异，用trim()去除首尾空格）
        String[] cookiePairs = cookieStr.split(";");
        for (String pair : cookiePairs) {
            pair = pair.trim(); // 去除空格（如拆分后可能有 " store-region-src=uid"）
            if (!pair.contains("=")) {
                continue; // 跳过空值或非key=value格式的内容
            }

            // 拆分name和value（最多拆1次，避免value中包含"="）
            String[] keyValue = pair.split("=", 2);
            String cookieName = keyValue[0].trim();
            String cookieValue = keyValue[1].trim();

            // 创建Cookie对象（旧版本：直接给字段赋值）
            Cookie cookie = new Cookie(cookieName, cookieValue);
//            cookie.name = cookieName;       // Cookie名称
//            cookie.value = cookieValue;     // Cookie值
            cookie.domain = targetDomain;   // 必选：绑定的域名
            cookie.path = "/";              // 必选：生效路径（默认"/"，表示整个域名）
            cookie.httpOnly = false;        // 可选：是否仅HTTP访问（根据实际情况调整）
            cookie.secure = true;           // 可选：HTTPS网站需设为true，HTTP设为false（抖音是HTTPS）
            // cookie.expires = ...;       // 可选：过期时间（不设置则为会话Cookie，关闭浏览器失效）

            cookieList.add(cookie); // 加入列表
        }

        return cookieList;
    }

    public Page navigatePage(String url, Page page) {
        return navigatePage(url, page, WaitUntilState.LOAD);
    }

    /**
     * 页面导航（合并加载等待逻辑）
     */
    public Page navigatePage(String url, Page page, WaitUntilState state) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        if (page == null) {
            throw new IllegalArgumentException("Page对象不能为空");
        }

        try {
            // 导航并等待指定状态
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(state)  // 导航时直接等待目标状态
                    .setTimeout(navigateTimeoutMs));


        } catch (Exception e) {
            if (!(e instanceof PlaywrightException && e.getMessage().startsWith("Object doesn't exist:"))) {
                log.error("页面导航失败:{} ,{} ", url, ThrowableUtil.getAllCauseMessage(e));
            }
        }
        return page;
    }


    public boolean waitForTarget(BrowserAPI api, long navigateTimeoutMs) {
        long deadline = System.currentTimeMillis() + navigateTimeoutMs;
        // 轮询间隔
        long checkInterval = 2000;
        while (System.currentTimeMillis() < deadline) {
            long updateTime = api.getUpdateTimes();
            long currentTime = System.currentTimeMillis();
            long abs = Math.abs(currentTime - updateTime);
            if (abs < navigateTimeoutMs) {
                log.debug("已捕获目标接口！");
                return true;
            }
            log.debug("---未捕获：{}", abs);

            try {
                TimeUnit.MILLISECONDS.sleep(checkInterval);
            } catch (InterruptedException ignored) {
            }
        }
        log.warn("未捕获接口目标，等待超时！");
        return false;
    }


    public Request waitForTargetRequest(Page page, String target_request_prefix, long navigateTimeoutMs) {

        try {
            // 1. 定义请求匹配规则：Predicate<Request>
            Predicate<Request> requestPredicate = request ->
                    request.url().startsWith(target_request_prefix);

            // 2. 定义请求匹配后的回调逻辑（Runnable）
            Runnable callback = () -> {
                log.debug("已捕获目标请求！");
            };

            // 3. 配置等待选项（超时配置毫秒）
            Page.WaitForRequestOptions waitOptions = new Page.WaitForRequestOptions()
                    .setTimeout(navigateTimeoutMs);

            // 4. 注册“匹配规则 + 回调 + 等待选项”
            return page.waitForRequest(requestPredicate, waitOptions, callback);
//            log.debug("已注册目标请求监听器，将在请求匹配时执行回调");
        } catch (Exception e) {
            log.warn("注册请求监听器时发生异常：{}", ThrowableUtil.getAllCauseMessage(e));
            throw e;
        }

    }

    public WebSocket waitForTargetWebSocket(Page page, String target_request_prefix, long navigateTimeoutMs) {
        try {
            Predicate<WebSocket> predicate = webSocket -> webSocket.url().startsWith(target_request_prefix);
            Runnable callback = () -> {
                log.debug("已捕获目标WebSocket！");
            };
            Page.WaitForWebSocketOptions options = new Page.WaitForWebSocketOptions()
                    .setPredicate(predicate)
                    .setTimeout(navigateTimeoutMs);
            return page.waitForWebSocket(options, callback);
        } catch (Exception e) {
            log.warn("注册WebSocket监听器时发生异常：{}", ThrowableUtil.getAllCauseMessage(e));
            throw e;
        }
    }

    public Response waitForTargetResponse(Page page, String target_request_prefix, long navigateTimeoutMs) {
        try {
            // 1. 定义响应匹配规则：Predicate<Request>
            Predicate<Response> requestPredicate = response ->
                    response.url().startsWith(target_request_prefix) && StrUtil.isNotBlank(response.text());

            // 2. 定义响应匹配后的回调逻辑（Runnable）
            Runnable callback = () -> {
                log.debug("已捕获目标响应！");
                // 这里可以执行你原本在“请求匹配后”要做的事，比如：
            };

            // 3. 配置等待选项（超时配置毫秒）
            Page.WaitForResponseOptions waitOptions = new Page.WaitForResponseOptions()
                    .setTimeout(navigateTimeoutMs);

            // 4. 注册“匹配规则 + 回调 + 等待选项”
            return page.waitForResponse(requestPredicate, waitOptions, callback);
//            log.debug("已注册目标响应监听器，将在响应匹配时执行回调");
        } catch (Exception e) {
            log.warn("注册响应监听器时发生异常", e);
            throw e;
        }
    }


    /**
     * 清理页面状态（cookies、权限、localStorage）
     */
    public void clearPageState(Page page) {
        if (page == null) return;
        try {
//            page.evaluate("localStorage.threadLocalClear(); sessionStorage.threadLocalClear();"); // 补充清理sessionStorage
            page.context().clearCookies();
        } catch (Exception e) {
            log.warn("清理页面状态失败", e);
        }
    }

    /**
     * 关闭页面并根据策略清理状态
     */
    public void closePage(Page page) {
        if (page == null) return;
        try {
            // 计数递增（线程安全）
            allPageCount.incrementAndGet();
            BrowserContent content = contents.get();
            int count = content.getPageCount();
            // 根据isPageClear策略清理状态
            if (Boolean.TRUE.equals(isPageClear)) {
                clearPageState(page); // 强制清理
            } else if (isPageClear == null && count % 20 == 0) {
                clearPageState(page); // 每20次清理一次
            }
            if (!page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }

            if (count % 5 == 0) {
                try {
                    BrowserContext context = content.getContext();
                    List<Page> pages = context.pages();
                    if (pages != null && !pages.isEmpty()) {
                        pages.stream()
                                .filter(p -> "about:blank".equals(p.url()))
                                .forEach(p -> {
                                    try {
                                        p.close();
                                        log.info("成功关闭空白僵尸页：{}", p);
                                    } catch (Exception e) {
                                        log.error("关闭空白僵尸页[{}]异常：{}", p, ThrowableUtil.getAllCauseMessage(e));
                                    }
                                });
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (updateContextCounts != null && count % updateContextCounts == 0) {
                closeContext();
            }
        } catch (Throwable e) {
            if (!(e instanceof PlaywrightException && e.getMessage().startsWith("Object doesn't exist:"))) {
                log.error("关闭页面失败：{}", e.getMessage());
            }
        }
    }

    public void threadLocalClear() {
        contents.clear();
    }

    public void closeContext() {
        //移除后监听会自动关闭BrowserContext
        contents.remove();
    }

    /**
     * 关闭所有资源（线程安全）
     */
    @Override
    public synchronized void close() {
        // 先关闭浏览器（会自动关闭所有页面和上下文）
        if (browser != null) {
            try {
                browser.close();
//                log.debug("浏览器已关闭");
            } catch (Exception e) {
                log.warn("关闭浏览器失败", e);
            } finally {
                browser = null; // 标记为null，避免重复操作
                threadLocalClear();

            }
        }
        stopAutoClearContextThread();
        // 再关闭Playwright
        if (playwright != null) {
            try {
                playwright.close();
//                log.debug("Playwright已关闭");
            } catch (Exception e) {
                log.warn("关闭Playwright失败", e);
            } finally {
                playwright = null; // 标记为null
            }
        }
    }
}
