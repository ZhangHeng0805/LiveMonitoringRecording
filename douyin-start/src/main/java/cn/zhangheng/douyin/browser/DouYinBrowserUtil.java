package cn.zhangheng.douyin.browser;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.bean.BrowserAPI;
import cn.zhangheng.browser.factory.MyBrowser;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.douyin.bean.DouYinRoom;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.zhangheng.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.zhangheng.common.util.UserAgentUtil.PC_VIEWPORTS;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/07 星期六 10:21
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinBrowserUtil {
    public static final Pattern STATUS_STR_PATTERN = Pattern.compile("\\\\\"status_str\\\\\":\\\\\"([^\"]+)\\\\\"");
    public static final Pattern NICKNAME_PATTERN = Pattern.compile("\\\\\"nickname\\\\\":\\\\\"([^\"]+)\\\\\"");
    public static final Pattern AVATAR_PATTERN = Pattern.compile("\\\\\"url_list\\\\\":\\[\\\\\"([^\"]+)\\\\\"");
    public static final String TARGET_REQUEST_PREFIX = "https://live.douyin.com/webcast/room/web/enter/";


    public static void setRoomCookie(DouYinRoom room, Page page) throws MalformedURLException {
        BrowserContext context = page.context();
        if (StrUtil.isNotBlank(room.getCookie()) && context.cookies(room.getRoomUrl()).isEmpty()) {
            String host = new URL(room.getRoomUrl()).getHost();
            context.addCookies(BrowserUtil.parseCookieString(host, room.getCookie()));
        }
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

    /**
     * 提取直播间信息（独立方法，便于维护）
     */
    public static Boolean extractRoomInfo(DouYinRoom room, Page page) {
        String title = safeGetTitle(page);
        if (title.contains("验证码")) {
            log.warn("访问" + room.getRoomUrl() + "导致浏览器触发验证码验证机制！");
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
                log.warn("API触发验证码验证机制！");
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


    public static void getRequestApi(DouYinRoom room, Request request) {
        getRequestApi(room, request, TARGET_REQUEST_PREFIX);
    }

    public static void getRequestApi(DouYinRoom room, Request request, String TARGET_REQUEST_PREFIX) {
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

    public static void getResponseApi(DouYinRoom room, Response response) {
        String url = response.url();
        if (url.startsWith(TARGET_REQUEST_PREFIX)) {
            BrowserAPI browserApi = room.getBrowserApi();
            browserApi.setDataUrl(url);
            browserApi.setResponseBody(response.text());
        }
    }

    public static void navigateAfterHandle(MyBrowser browser) {
        Page page = browser.getPage();
        String title = page.title();
        if (!title.contains("验证码")) {
            if (!PlaywrightBrowserFactory.isHeadless()) {
                int count = browser.getContent().getPageCount();
                if (count % 10 == 0) {
                    PlaywrightBrowserFactory.setHeadless(true);
                    PlaywrightBrowserFactory.destroy();
                }
            }
        } else {
            //为了应对手动验证码
            if (PlaywrightBrowserFactory.isHeadless()) {
                PlaywrightBrowserFactory.setHeadless(false);
                PlaywrightBrowserFactory.destroy();
            } else {
                page.waitForFunction("keyword => document.title.includes(keyword)",
                        "抖音直播间",
                        new Page.WaitForFunctionOptions().setTimeout(15000));
            }
        }
    }

    public static String getDouyinFingerprintScript() {
        String initScript =
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
                        "if (!window.chrome) {\n" +
                        "  window.chrome = {\n" +
                        "    runtime: {},\n" +
                        "    webstore: {}\n" +
                        "  };\n" +
                        "}\n" +
                        "Object.defineProperty(navigator, 'plugins', {\n" +
                        "  get: () => [\n" +
                        "    { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: '' },\n" +
                        "    { name: 'Widevine Content Decryption Module', filename: 'widevinecdm.dll', description: '' }\n" +
                        "  ]\n" +
                        "});\n" +
                        "Object.defineProperty(navigator, 'mimeTypes', {\n" +
                        "  get: () => [\n" +
                        "    { type: 'application/pdf', suffixes: 'pdf', description: '' }\n" +
                        "  ]\n" +
                        "});\n" +
                        "Object.defineProperty(navigator, 'languages', { get: () => ['zh‑CN', 'zh', 'en'] });\n";
        // java层随机硬件参数
        int mem = new int[]{4, 8, 16}[RandomUtil.randomInt(3)];
        int cpuCore = new int[]{4, 6, 8, 12}[RandomUtil.randomInt(4)];

        String script = initScript + String.format("Object.defineProperty(navigator, 'deviceMemory', { get: () => %d });\n" +
                "Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => %d });", mem, cpuCore);
        return script;
    }

    /**
     * 一键构建抖音PC网页Context配置：随机UA + 随机分辨率 + zh-CN + Asia/Shanghai
     */
    public static Browser.NewContextOptions buildDouyinPcContextOptions(String ua) {
        int[] vp = PC_VIEWPORTS[new Random().nextInt(PC_VIEWPORTS.length)];
        return new Browser.NewContextOptions()
                .setUserAgent(ua)
                .setViewportSize(vp[0], vp[1])
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai");
    }
}
