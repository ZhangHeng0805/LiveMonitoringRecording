package cn.zhangheng.browser;

import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/15 星期五 21:00
 * @version: 1.0
 * @description:
 */
@Slf4j
public class BrowserUtil {
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

    //

    /**
     * 解析 Cookie 字符串为 Playwright 的 Cookie 对象
     * @param targetDomain 绑定的域名
     * @param cookieStr
     * @return
     */
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
     * 将Cookie集合转为字符串
     * @param cookies
     * @return
     */
    public static String toCookieStr(List<Cookie> cookies) {
        StringBuilder cookieStr = new StringBuilder();
        for (int i = 0; i < cookies.size(); i++) {
            Cookie cookie = cookies.get(i);
            cookieStr.append(cookie.name).append("=").append(cookie.value);
            if (i < cookies.size() - 1) {
                cookieStr.append("; ");
            }
        }
        return cookieStr.toString();
    }


    /**
     * 页面导航（合并加载等待逻辑）
     */
    public static boolean navigatePage(String url, Page page, WaitUntilState state,double navigateTimeoutMs) {
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
                    .setTimeout(navigateTimeoutMs)
                    .setReferer(url)
            );
            return true;
        } catch (TimeoutError e1) {
            log.error("页面导航超时:{}", url);
        } catch (PlaywrightException e2) {
            log.error("页面导航失败:{} ,{} ", url, ThrowableUtil.toString(e2,128));
        } catch (Throwable e) {
            log.error("页面导航失败:{}", e.getMessage());
        }
        return false;
    }

    public static boolean navigatePage(String url, Page page, WaitUntilState state) {
        return navigatePage(url,page,state,30_000);
    }


    public static Request waitForTargetRequest(Page page, String target_request_prefix, long navigateTimeoutMs) {
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

    public static WebSocket waitForTargetWebSocket(Page page, String target_wss, long navigateTimeoutMs) {
        try {
            Predicate<WebSocket> predicate = webSocket -> webSocket.url().indexOf(target_wss) > 0;
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

    public static Response waitForTargetResponse(Page page, String target_request_prefix, long navigateTimeoutMs) {
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
            log.warn("注册响应监听器时发生异常:{}", ThrowableUtil.getAllCauseMessage(e));
            throw e;
        }
    }

    public static boolean goToBySelector(String url, Page page, String selector, int maxWaitTimeSec) {
        boolean b = BrowserUtil.navigatePage(url, page, WaitUntilState.LOAD);
        if (!b) return false;
        boolean isLoginSuccess = false;
        int count = 0;

        while (count < maxWaitTimeSec) {
            try {
                // 检测已存在元素标识
                // 选择器可根据页面微调
                page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                        .setTimeout(1500));
                isLoginSuccess = true;
                break;
            } catch (Exception e) {
                // 未登录，休眠继续轮询
                count++;
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return isLoginSuccess;
    }

}
