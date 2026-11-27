package cn.zhangheng.douyin.browser;

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
import static cn.zhangheng.douyin.browser.DouYinBrowserUtil.TARGET_REQUEST_PREFIX;

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
    /**
     * 头像地址：从 avatar_thumb 之后找到第一个 http(s) 地址并截到引号/反斜杠/逗号为止。
     * 页面里 cover、房间封面等多个对象都带 url_list，只取页面第一个 url_list 会抓到别的图，
     * 甚至抓到字面量 "url_list" 这种脏值，因此必须锚定 avatar_thumb 且做 URL 校验。
     */
    public static final int AVATAR_SEARCH_WINDOW = 600;
    public static final String TARGET_REQUEST_PREFIX = "https://live.douyin.com/webcast/room/web/enter/";


    public static void setRoomCookie(DouYinRoom room, MyBrowser myBrowser) {
        try {
            if (myBrowser.getContent().getCookie() != null) return;
            String cookie = room.getCookie();
            if (StrUtil.isBlank(cookie)) {
                return;
            }
            for (String host : new String[]{"www.douyin.com", "www.iesdouyin.com", "douyin.com"}) {
                try {
                    myBrowser.getContent().setCookie(host, cookie);
                } catch (Exception ignored) {
                    // 单个域注入失败不影响其它域
                }
            }
        } catch (Exception e) {
            log.warn("注入抖音 Cookie 失败，继续无 Cookie 解析：{}", e.getMessage());
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
        if (title == null) {
            title = "";
        }
        if (title.contains("验证码")) {
            log.warn("访问" + room.getRoomUrl() + "导致浏览器触发验证码验证机制！");
            return false;
        }
        String pageSource = page.content();
        return extractRoomInfo(room, pageSource);
    }

    /**
     * 从页面源码里提取头像链接：定位 avatar_thumb，再取其后第一个 http(s) 地址。
     * 注意页面里是 JSON 转义后的文本（形如 \"avatar_thumb\":{\"url_list\":[\"https://...\"），
     * 所以遇到 \" 就是地址结束；另外 douyin 偶尔把斜杠转义成 \/，这里顺手还原。
     */
    public static String extractAvatarUrl(String pageSource) {
        if (StrUtil.isBlank(pageSource)) {
            return null;
        }
        int anchor = pageSource.indexOf("avatar_thumb");
        if (anchor < 0) {
            return null;
        }
        int start = pageSource.indexOf("http", anchor);
        if (start < 0 || start - anchor > AVATAR_SEARCH_WINDOW) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < pageSource.length()) {
            char c = pageSource.charAt(i);
            if (c == '\\') {
                // \/ 是转义斜杠，还原后继续；其它转义（\" 结束、\u0026 参数）直接截断
                if (i + 1 < pageSource.length() && pageSource.charAt(i + 1) == '/') {
                    sb.append('/');
                    i += 2;
                    continue;
                }
                break;
            }
            if (c == '"' || c == '\'' || c == ']' || c == '}' || c == ',' || Character.isWhitespace(c)) {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 校验并清洗头像地址：
     * 1) 去掉转义残留（尾部反斜杠、转义斜杠）；
     * 2) 含 unicode 转义时先反转义；
     * 3) 只接受 http(s) 开头且有主机的地址，其余一律视为无效（例如误抓到的 "url_list"）。
     */
    public static String normalizeAvatar(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String v = raw.trim().replace("\\/", "/");
        // 正则/字符串截取常把 JSON 转义尾部的反斜杠带进来（形如 xxx.jpeg?from=3067671334\），
        // 这种地址浏览器请求必 404，直接在第一个反斜杠处截断
        int firstBackslash = v.indexOf('\\');
        if (firstBackslash >= 0) {
            v = v.substring(0, firstBackslash).trim();
        }
        if (!(v.startsWith("http://") || v.startsWith("https://")) || !v.contains(".")) {
            return null;
        }
        return v;
    }

    public static Boolean extractRoomInfo(DouYinRoom room, String pageSource) {
        if (pageSource == null) {
            log.warn("页面源码为空，无法提取房间信息");
            return false;
        }
        int index = pageSource.lastIndexOf("\\\"roomStore\\\":");
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
        // 正则提取直播状态
        room.setLiving(extractLivingStatus(pageSource));

        // 正则提取昵称（避免重复提取）
        if (room.getNickname() == null) {
            String nickname = extractNickname(pageSource);
            room.setNickname(nickname);
        }
        // 已有头像若是脏数据（例如历史误抓的 "url_list"），按空处理，让本轮重新抓一次
        if (normalizeAvatar(room.getAvatar()) == null) {
            room.setAvatar(null);
        }
        if (StrUtil.isBlank(room.getAvatar())) {
            String avatar = normalizeAvatar(extractAvatarUrl(pageSource));
            if (avatar != null) {
                room.setAvatar(avatar);
            } else {
                log.debug("{} 未抽取到有效头像地址，本轮跳过头像更新", room.getId());
            }
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
            if (browserApi==null){
                browserApi=new BrowserAPI(TARGET_REQUEST_PREFIX);
                room.setBrowserApi(browserApi);
            }
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
                    PlaywrightBrowserFactory.closeBrowser();
                }
            }
        } else {
            //为了应对手动验证码
            if (PlaywrightBrowserFactory.isHeadless()) {
                PlaywrightBrowserFactory.setHeadless(false);
                PlaywrightBrowserFactory.closeBrowser();
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
