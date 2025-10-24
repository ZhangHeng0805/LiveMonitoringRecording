package cn.zhangheng.browser;

import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    private volatile Playwright playwright;
    private volatile Browser browser;

    // 线程安全的计数（替代int，避免多线程计数错误）
    @Getter
    private final AtomicInteger allPageCount = new AtomicInteger(0);

    @Setter
    private Boolean isPageClear; // 是否每次打开页面时重置状态，null时默认每10次清理

    @Setter
    private double navigateTimeoutMs = 30000;
    @Setter
    private double waitForLoadTimeoutMs = 10000;
    @Setter
    private LoadState loadState = LoadState.DOMCONTENTLOADED;

    private static final MyThreadLocal<BrowserContext> context = new MyThreadLocal<>(new MyThreadLocal.Listener<BrowserContext>() {
        @Override
        public void beforeClear(ConcurrentHashMap<String, BrowserContext> map) {
            for (Map.Entry<String, BrowserContext> entry : map.entrySet()) {
                try {
                    BrowserContext value = entry.getValue();
                    if (value.browser().isConnected()) {
                        value.close();
                        log.debug("{}清除BrowserContext，已关闭", entry.getKey());
                    }
                } catch (Throwable e) {
                    log.error("Error clearing browser context: {}", e.getMessage());
                }
            }
        }

        @Override
        public void removed(String threadId, BrowserContext value) {
            if (value != null && value.browser().isConnected()) {
                try {
                    value.close();
                    log.debug("线程[{}]的BrowserContext已关闭", threadId);
                } catch (Throwable e) {
                    log.error("Error closing browser context[{}]: {}", threadId, e.getMessage());
                }
            }
        }
    });

    private static final MyThreadLocal<AtomicInteger> pageCount = new MyThreadLocal<>();

    // 反自动化检测初始化脚本
    public static final String initScript =
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
                    "delete window.chrome;\n" +
                    "Object.defineProperty(navigator, 'plugins', { get: () => [\n" +
                    "  { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },\n" +
                    "  { name: 'Widevine Content Decryption Module', filename: 'widevinecdm.dll' }\n" +
                    "] });\n" +
                    "Object.defineProperty(navigator, 'mimeTypes', { get: () => [\n" +
                    "  { type: 'application/pdf', suffixes: 'pdf' }\n" +
                    "] });\n" +
                    "Object.defineProperty(screen, 'width', { get: () => 1920 });\n" +
                    "Object.defineProperty(screen, 'height', { get: () => 1080 });\n" +
                    "Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });\n" +
                    "Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });\n" +
                    "document.addEventListener('DOMContentLoaded', () => {\n" +
                    "  const event = new MouseEvent('click', { clientX: 100, clientY: 200, bubbles: true });\n" +
                    "  document.body.dispatchEvent(event);\n" +
                    "});";

    private final String userAgent;

    public PlaywrightBrowser(String userAgent) {
        this(userAgent,true);
    }

    public PlaywrightBrowser(String userAgent,boolean headless) {
        this.userAgent = userAgent;
        try {
            // 先初始化Playwright
            playwright = Playwright.create();
            // 再初始化浏览器（若失败，需关闭已创建的Playwright）
            browser = playwright.chromium().launch(getLaunchOptions(userAgent,headless));
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
    public static BrowserType.LaunchOptions getLaunchOptions(String userAgent,boolean headless) {
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
                .setSlowMo(50); // 轻微延迟，模拟真人操作（可选）
    }

    /**
     * 检查浏览器是否运行中
     */
    public boolean isRunning() {
        return browser != null && browser.isConnected();
    }

    /**
     * 创建新页面（自动处理浏览器断开重连）
     */
    public Page newPage() {
        // 双重检查：确保浏览器可用
        if (!isRunning()) {
            log.warn("浏览器已断开，尝试重新启动...");
            synchronized (this) {
                if (!isRunning()) {
                    // 安全关闭旧浏览器（若存在）
                    if (browser != null) {
                        try {
                            browser.close();
                        } catch (Exception e) {
                            log.warn("关闭旧浏览器失败", e);
                        }
                        browser = null;
                    }
                }
                // 重新创建浏览器
                try {
                    browser = playwright.chromium().launch(getLaunchOptions(userAgent,true));
                    log.info("浏览器重新启动成功");
                } catch (Exception e) {
                    throw new RuntimeException("重新启动浏览器失败", e);
                } finally {
                    context.clear();
                    pageCount.clear();
                }
            }
        }
        BrowserContext browserContext = context.get();
        boolean isContextValid = (browserContext != null)
                && browserContext.browser().equals(browser);
        if (!isContextValid) {
            if (browserContext != null) {
                try {
                    browserContext.close();
                } catch (Exception e) {
                    log.warn("关闭旧 Context 失败", e);
                }
            }
            // 创建新 Context 并覆盖 ThreadLocal
            browserContext = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(UserAgentUtil.getUser_Agent()));
            context.set(browserContext);
        }
        if (pageCount.get() == null) {
            pageCount.set(new AtomicInteger(1));
        } else {
            pageCount.get().incrementAndGet();
        }
        Page page = browserContext.newPage();
        // 创建新页面并注入反检测脚本
        page.addInitScript(initScript);
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

    /**
     * 页面导航（合并加载等待逻辑）
     */
    public Page navigatePage(String url, Page page) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        if (page == null) {
            throw new IllegalArgumentException("Page对象不能为空");
        }

        try {
            // 根据loadState决定导航等待策略，避免重复等待
            WaitUntilState waitUntil = (loadState == LoadState.DOMCONTENTLOADED)
                    ? WaitUntilState.DOMCONTENTLOADED
                    : WaitUntilState.LOAD;

            // 导航并等待指定状态
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(waitUntil)
                    .setTimeout(navigateTimeoutMs)
            );

            // 若需要更严格的状态（如NETWORKIDLE），额外等待一次
//            if (loadState == LoadState.DOMCONTENTLOADED) {
            page.waitForLoadState(loadState, new Page.WaitForLoadStateOptions()
                    .setTimeout(waitForLoadTimeoutMs)
            );
//            }
        } catch (Exception e) {
            if (!(e instanceof PlaywrightException && e.getMessage().startsWith("Object doesn't exist:"))) {
                log.error("页面导航失败:{} ,{} ", url, ThrowableUtil.getAllCauseMessage(e));
//            throw new RuntimeException("导航到" + url + "失败", e);
            }
        }
        return page;
    }


    public boolean waitForTargetRequest(Page page, String target_request_prefix, long navigateTimeoutMs) {
        AtomicReference<Boolean> flag = new AtomicReference<>(false);
        try {
            // 1. 定义请求匹配规则：Predicate<Request>
            Predicate<Request> requestPredicate = request ->
                    "GET".equalsIgnoreCase(request.method()) &&
                            request.url().startsWith(target_request_prefix);

            // 2. 定义请求匹配后的回调逻辑（Runnable）
            Runnable callback = () -> {
                flag.set(true);
                log.debug("已捕获目标请求！");
                // 这里可以执行你原本在“请求匹配后”要做的事，比如：
                // - 标记请求已捕获
                // - 记录请求信息
                // - 触发后续流程
            };

            // 3. 配置等待选项（超时配置毫秒）
            Page.WaitForRequestOptions waitOptions = new Page.WaitForRequestOptions()
                    .setTimeout(navigateTimeoutMs);

            // 4. 注册“匹配规则 + 回调 + 等待选项”
            page.waitForRequest(requestPredicate, waitOptions, callback);
            log.debug("已注册目标请求监听器，将在请求匹配时执行回调");
        } catch (Exception e) {
            log.warn("注册请求监听器时发生异常", e);
        }
        return flag.get();
    }

    public boolean waitForTargetResponse(Page page, String target_request_prefix, long navigateTimeoutMs) {
        AtomicReference<Boolean> flag = new AtomicReference<>(false);
        try {
            // 1. 定义响应匹配规则：Predicate<Request>
            Predicate<Response> requestPredicate = response ->
                    response.url().startsWith(target_request_prefix)&& StrUtil.isNotBlank(response.text());

            // 2. 定义响应匹配后的回调逻辑（Runnable）
            Runnable callback = () -> {
                flag.set(true);
                log.debug("已捕获目标响应！");
                // 这里可以执行你原本在“请求匹配后”要做的事，比如：
            };

            // 3. 配置等待选项（超时配置毫秒）
            Page.WaitForResponseOptions waitOptions = new Page.WaitForResponseOptions()
                    .setTimeout(navigateTimeoutMs);

            // 4. 注册“匹配规则 + 回调 + 等待选项”
            page.waitForResponse(requestPredicate, waitOptions, callback);
            log.debug("已注册目标响应监听器，将在响应匹配时执行回调");
        } catch (Exception e) {
            log.warn("注册响应监听器时发生异常", e);
        }
        return flag.get();
    }


    /**
     * 清理页面状态（cookies、权限、localStorage）
     */
    public void clearPageState(Page page) {
        if (page == null) return;
        try {
//            page.evaluate("localStorage.clear(); sessionStorage.clear();"); // 补充清理sessionStorage
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
            int allCount = allPageCount.incrementAndGet();
            int count = pageCount.get().get();
            // 根据isPageClear策略清理状态
            if (Boolean.TRUE.equals(isPageClear)) {
                clearPageState(page); // 强制清理
            } else if (isPageClear == null && count % 30 == 0) {
                clearPageState(page); // 每30次清理一次
            }
            if (!page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }
            if (count % 100 == 0) {
                // 每100次关闭一次
                closeContext();
            }
        } catch (Throwable e) {
            if (!(e instanceof PlaywrightException && e.getMessage().startsWith("Object doesn't exist:"))) {
                log.error("关闭页面失败：{}", e.getMessage());
            }
        }
    }

    public void clear() {
        context.clear();
        pageCount.clear();
    }

    public void closeContext() {
        //移除后监听会自动关闭BrowserContext
        context.remove();
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
                clear();
            }
        }

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
