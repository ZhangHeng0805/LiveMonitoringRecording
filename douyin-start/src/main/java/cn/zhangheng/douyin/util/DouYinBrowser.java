package cn.zhangheng.douyin.util;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static cn.zhangheng.douyin.util.DouYinBrowserFactory.TARGET_REQUEST_PREFIX;
import static cn.zhangheng.douyin.util.DouYinBrowserFactory.extractRoomInfo;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/21 星期日 05:02
 * @version: 1.0
 * @description: 抖音直播间信息获取工具类
 * 浏览器对象类，请求重复调用，需手动关闭浏览器
 */
public class DouYinBrowser implements Closeable {

    // 正则表达式模式（静态编译，提升性能）
//    private static final Pattern STATUS_STR_PATTERN = Pattern.compile("\\\\\"status_str\\\\\":\\\\\"([^\"]+)\\\\\"");
//    private static final Pattern NICKNAME_PATTERN = Pattern.compile("\\\\\"nickname\\\\\":\\\\\"([^\"]+)\\\\\"");
//    private static final Pattern AVATAR_PATTERN = Pattern.compile("\\\\\"url_list\\\\\":\\[\\\\\"([^\"]+)\\\\\"");
    private static final Logger log = LoggerFactory.getLogger(DouYinBrowser.class);

    // 线程安全的浏览器实例（volatile确保多线程可见性）
    private volatile PlaywrightBrowser browser;

    // 目标请求URL前缀（提取为常量，便于维护）
//    private static final String TARGET_REQUEST_PREFIX = "https://live.douyin.com/webcast/room/web/enter/";

    DouYinBrowser() {
        browser = new PlaywrightBrowser(Constant.User_Agent);
    }

    /**
     * 发起请求并提取直播间信息
     */
    public void request(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return;
        }

        Page page = null;
        try {
            checkAndInitBrowser();
            page = browser.newPage();

            BrowserContext context = page.context();
            if (StrUtil.isNotBlank(room.getCookie()) && context.cookies(roomUrl).isEmpty()) {
                String host = new URL(roomUrl).getHost();
                context.addCookies(PlaywrightBrowser.parseCookieString(host, room.getCookie()));
                log.debug("{}设置cookie成功！", host);
            }
//            log.debug("=== 对 {} 生效的 Cookie 共 {} 个 ===", roomUrl, context.cookies(roomUrl).size());

            // 注册请求监听器（提取目标请求信息）
            Consumer<Request> handler = request -> {
                String url = request.url();
                // 匹配目标GET请求
                if ("GET".equalsIgnoreCase(request.method()) && url.startsWith(TARGET_REQUEST_PREFIX)) {
                    room.setData_url(url);
                    // 获取并设置User-Agent
                    String userAgent = request.headers().get("user-agent");
                    room.setUser_agent(userAgent);
                    log.debug("直播状态: {}\n===== 监听URL: {}\n===== User-Agent: {}",
                            room.isLiving() ? "已开启" : "未开启", url, userAgent);
                }
            };
            page.onRequest(handler);
            // 导航到直播间页面
            browser.navigatePage(roomUrl, page);

            // 提取页面源码中的房间信息
            String pageSource = page.content();
            extractRoomInfo(room, pageSource);

            // 若直播中，等待目标请求完成（替代固定休眠，更高效）
            if (room.isLiving()) {
                waitForTargetRequest(page);
            }
            page.offRequest(handler);
        } catch (Throwable e) {
            log.error("处理直播间[" + roomUrl + "]时发生异常,{}", ThrowableUtil.getAllCauseMessage(e)); // 记录完整堆栈
//            closeContext();
        } finally {
            // 确保页面关闭，释放资源
            if (browser != null) {
                browser.closePage(page);
            }
        }
    }


    /**
     * 等待目标请求完成（最多等待5秒，避免无限阻塞）
     */
    private void waitForTargetRequest(Page page) {
        try {
            // 1. 定义请求匹配规则：Predicate<Request>
            Predicate<Request> requestPredicate = request ->
                    "GET".equalsIgnoreCase(request.method()) &&
                            request.url().startsWith(TARGET_REQUEST_PREFIX);

            // 2. 定义请求匹配后的回调逻辑（Runnable）
            Runnable callback = () -> {
                log.debug("已捕获目标请求！");
                // 这里可以执行你原本在“请求匹配后”要做的事，比如：
                // - 标记请求已捕获
                // - 记录请求信息
                // - 触发后续流程
            };

            // 3. 配置等待选项（超时 5000 毫秒）
            Page.WaitForRequestOptions waitOptions = new Page.WaitForRequestOptions()
                    .setTimeout(5000);

            // 4. 注册“匹配规则 + 回调 + 等待选项”
            page.waitForRequest(requestPredicate, waitOptions, callback);

            log.debug("已注册目标请求监听器，将在请求匹配时执行回调");
        } catch (Exception e) {
            log.warn("注册请求监听器时发生异常", e);
        }
    }

    /**
     * 检查并初始化浏览器（线程安全）
     */
    private synchronized void checkAndInitBrowser() {
        if (browser == null || !browser.isRunning()) {
            // 关闭旧实例（若存在）
            closeBrowser();
            // 初始化新浏览器
            try {
                browser = new PlaywrightBrowser(Constant.User_Agent);
                log.info("浏览器实例初始化成功");
            } catch (Exception e) {
                throw new RuntimeException("初始化浏览器失败", e);
            }
        }
    }

    /**
     * 关闭浏览器实例（线程安全）
     */
    private synchronized void closeBrowser() {
        if (browser != null) {
            try {
                browser.close();
                log.info("浏览器实例已关闭");
            } catch (Exception e) {
                log.warn("关闭浏览器时发生异常", e);
            } finally {
                browser = null;
            }
        }
    }

    public void closeContext() {
        if (browser != null) {
            browser.closeContext();
        }
    }

    public void clear() {
        if (browser != null) {
            browser.clear();
        }
    }

    /**
     * 提取直播间信息（状态、昵称等）
     */
    /*private static void extractRoomInfo(DouYinRoom room, String pageSource) {
        if (pageSource == null) {
            log.warn("页面源码为空，无法提取房间信息");
            return;
        }

        // 提取直播状态（"2"表示直播中）
        String status = extractStr(pageSource, STATUS_STR_PATTERN, new HashSet<>(Collections.singletonList("")));
        room.setLiving("2".equals(status));
//        log.debug("提取到直播状态: {}（{}）", status, room.isLiving() ? "直播中" : "未直播");

        // 仅在昵称未设置时提取（避免重复提取）
        if (room.getNickname() == null) {
            String nickname = extractStr(pageSource, NICKNAME_PATTERN,
                    new HashSet<>(Collections.singletonList("$undefined")));
            room.setNickname(nickname);
            log.debug("提取到主播昵称: {}", nickname);
        }
        if (room.getAvatar()==null){
            String avatar = UnicodeUtil.toString(extractStr(pageSource, AVATAR_PATTERN, null));
            room.setAvatar(avatar);
        }
    }*/

    /**
     * 通用正则提取方法（处理转义字符和排除无效值）
     */
    /*public static String extractStr(String content, Pattern pattern, Set<String> excludeValues) {
        if (content == null || pattern == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            // 提取并处理可能的转义字符（如\" -> "）
            String value = matcher.group(1)
                    .trim()
                    .replace("\\\\\"", "\""); // 处理转义的双引号

            // 排除无效值
            if (excludeValues == null || !excludeValues.contains(value)) {
                return value;
            }
        }
        return null;
    }*/

    /**
     * 关闭资源（实现Closeable，支持try-with-resources）
     */
    @Override
    public void close() {
        closeBrowser();
    }
}
