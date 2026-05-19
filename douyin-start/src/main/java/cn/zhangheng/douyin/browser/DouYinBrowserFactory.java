package cn.zhangheng.douyin.browser;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.douyin.bean.DouYinRoom;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/23 星期二 22:34
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinBrowserFactory {

    public static final Pattern STATUS_STR_PATTERN = Pattern.compile("\\\\\"status_str\\\\\":\\\\\"([^\"]+)\\\\\"");
    public static final Pattern NICKNAME_PATTERN = Pattern.compile("\\\\\"nickname\\\\\":\\\\\"([^\"]+)\\\\\"");
    public static final Pattern AVATAR_PATTERN = Pattern.compile("\\\\\"url_list\\\\\":\\[\\\\\"([^\"]+)\\\\\"");
    public static final String TARGET_REQUEST_PREFIX = "https://live.douyin.com/webcast/room/web/enter/";

    // 1. 必须声明为 null，volatile 保证多线程可见性、禁止指令重排
    private static volatile DouYinBrowser browser = null;

    // 私有构造，禁止外部实例化
    private DouYinBrowserFactory() {
    }

    // 2. 标准双重检查锁（DCL）单例，线程安全
    public static DouYinBrowser getBrowser() {
        // 第一次检查：不加锁，提升性能
        if (browser == null) {
            // 加锁：保证同一时间只有一个线程初始化
            synchronized (DouYinBrowserFactory.class) {
                // 第二次检查：防止多线程同时进入第一层判断
                if (browser == null) {
                    browser = new DouYinBrowser();
                }
            }
        }
        return browser;
    }

    // 3. 安全关闭：必须和getBrowser用同一把锁，保证原子性
    public static boolean closeBrowser() {
        synchronized (DouYinBrowserFactory.class) {
            if (browser != null) {
                try {
                    browser.close();
                    return true;
                } catch (Exception e) {
                    log.warn("Failed to close browser", e);
                } finally {
                    browser = null;
                }
            }
        }
        return false;
    }

    /**
     * 提取直播间信息（独立方法，便于维护）
     */
    static Boolean extractRoomInfo(DouYinRoom room, Page page) {
        String title = safeGetTitle(page);

        if (title.contains("验证码")) {
            log.warn("浏览器触发验证码验证机制！");
            return false;
        }
        String pageSource = page.content();
        return extractRoomInfo(room, pageSource);
    }

    public static Boolean extractRoomInfo(DouYinRoom room, String pageSource) {
        if (pageSource == null) {
            log.warn("页面源码为空，无法提取房间信息");
            return false;
        }
        int index = pageSource.lastIndexOf("\\\"homeStore\\\":");
        if (index > 0) {
            pageSource = pageSource.substring(index);
        } else {
            if (pageSource.indexOf("<title>验证码") > 0) {
                log.warn("触发验证码验证机制！");
                return null;
            }
            log.warn("pageSource未获取到有效内容");
            return null;
        }
        // 提取直播状态
        room.setLiving(extractLivingStatus(pageSource));

        // 提取昵称（避免重复提取）
        if (room.getNickname() == null) {
            String nickname = extractNickname(pageSource);
            room.setNickname(nickname);
        }
        if (room.getAvatar() == null) {
            String avatar = UnicodeUtil.toString(extractStr(pageSource, AVATAR_PATTERN, null));
            room.setAvatar(avatar);
        }
        return true;
    }

    public static boolean extractLivingStatus(String pageSource) {
        String status = extractStr(pageSource, STATUS_STR_PATTERN, null);
        return "2".equals(status);
    }

    public static String extractNickname(String pageSource) {
        return extractStr(pageSource, NICKNAME_PATTERN,
                new HashSet<>(Collections.singletonList("$undefined")));
    }

    // 封装安全获取标题的方法（加重试）
    private static String safeGetTitle(Page page) {
        int retry = 5;
        while (retry > 0) {
            try {
                return page.title();
            } catch (Throwable e) {
                retry--;
                if (retry == 0) throw e;
                // 重试前等待上下文稳定
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return "";
    }

    static void setRoomCookie(DouYinRoom room, Page page, String roomUrl) throws MalformedURLException {
        BrowserContext context = page.context();
        if (StrUtil.isNotBlank(room.getCookie()) && context.cookies(roomUrl).isEmpty()) {
            String host = new URL(roomUrl).getHost();
            context.addCookies(BrowserUtil.parseCookieString(host, room.getCookie()));
            log.debug("{}设置cookie成功！", host);
        }
    }

    /**
     * 通用正则提取方法（复用逻辑）
     *
     * @param content       原始字符串
     * @param excludeValues 需要排除的值集合（如{"", "0", "null"}）
     * @return 第一个不在排除集合中的值；若所有值都被排除，返回null
     */
    public static String extractStr(String content, Pattern pattern, Set<String> excludeValues) {
        // 参数校验：避免空指针
        if (content == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(content);

        // 循环查找所有匹配项
        while (matcher.find()) {
            // 提取值并处理转义字符
            String value = matcher.group(1).trim();

            // 排除指定值
            if (excludeValues == null || !excludeValues.contains(value)) {
                return value; // 返回第一个有效匹配
            }
            // 若在排除集合中，继续查找下一个
        }

        // 所有匹配都被排除或无匹配
        return null;
    }

    static void getRequestApi(DouYinRoom room, Request request) {
        getRequestApi(room, request, TARGET_REQUEST_PREFIX);
    }

    static void getRequestApi(DouYinRoom room, Request request, String TARGET_REQUEST_PREFIX) {
        if (request == null) return;
        String url = request.url();
        // 匹配目标GET请求
        if (url.startsWith(TARGET_REQUEST_PREFIX)) {
            BrowserAPI browserApi = room.getBrowserApi();
            Map<String, String> headers = request.allHeaders();
            browserApi.setDataUrl(url);
            browserApi.setHeaders(headers);
            if (room.isLiving())
                log.debug(room.getId() + "-直播状态: 已开启\n===== 监听URL: {}\n===== 请求头: {}",
                        browserApi.getDataUrl(), browserApi.getHeaders());
        }
    }

    static void getResponseApi(DouYinRoom room, Response response) {
        String url = response.url();
        if (url.startsWith(TARGET_REQUEST_PREFIX)) {
            BrowserAPI browserApi = room.getBrowserApi();
            browserApi.setDataUrl(url);
            browserApi.setResponseBody(response.text());
        }
    }
}
