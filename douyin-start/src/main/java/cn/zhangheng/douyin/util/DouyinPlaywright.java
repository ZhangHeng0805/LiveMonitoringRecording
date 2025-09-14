package cn.zhangheng.douyin.util; /**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/14 星期日 05:14
 * @version: 1.0
 * @description:
 */

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.RandomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DouyinPlaywright {

    // 复用Playwright和Browser实例（重量级资源，避免频繁创建销毁）
//    private static Playwright playwright;
//    private static Browser browser;
    private static final Pattern STATUS_STR_PATTERN = Pattern.compile("\\\\\"status_str\\\\\":\\\\\"([^\"]+)\\\\\"");
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("\\\\\"nickname\\\\\":\\\\\"([^\"]+)\\\\\"");
    private static final Logger log = LoggerFactory.getLogger(DouyinPlaywright.class);

    // 静态初始化：创建Playwright和Browser（程序启动时执行一次）
//    static {
//        try {
//            playwright = Playwright.create();
//            // 浏览器配置（复用实例，仅初始化一次）
//            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
//                    .setHeadless(true)
//                    .setArgs(Arrays.asList(
//                            "--disable-blink-features=AutomationControlled",
//                            "--user-agent=" + Constant.User_Agent,
//                            "--no-sandbox", // 提升容器环境兼容性
//                            "--disable-gpu" // 禁用GPU加速，减少资源占用
//                    ));
//            browser = playwright.chromium().launch(launchOptions);
//            // 注册JVM关闭钩子，确保程序退出时释放资源
//            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                if (browser != null) browser.close();
//                if (playwright != null) playwright.close();
//            }));
//        } catch (Exception e) {
//            throw new RuntimeException("初始化Playwright失败", e);
//        }
//    }

    /**
     * 可重复调用的直播间信息初始化方法
     * 每次调用创建独立Page，避免状态污染
     */
//    public static void init(DouYinRoom room) {
//        if (room == null || room.getRoomUrl() == null) {
//            DouyinPlaywright.log.error("房间信息或URL为空，初始化失败");
//            return;
//        }
//
//        Page page = null;
//        try {
//            page = browser.newPage();
//
//            // 禁用自动化特征（无论是否直播都需要设置）
//            String initScript = "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
//                    "delete window.chrome;\n" +
//                    "if (window.chrome && window.chrome.loadTimes) delete window.chrome.loadTimes;";
//            page.addInitScript(initScript);
//
//            // 1. 先导航并提取直播状态（判断是否需要监听）
//            page.navigate(room.getRoomUrl());
//            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
//            String pageSource = page.content();
//            extractRoomInfo(room, pageSource); // 先确定是否直播
//
//            // 2. 仅当直播时（isLiving()为true）才监听请求
//            if (room.isLiving()) {
//                DouyinPlaywright.log.debug("直播间正在直播，开始监听请求...");
//                listenToEnterRequest(page, room); // 注册监听器并等待请求
//                // 直播中额外等待（确保异步数据加载）
//                TimeUnit.SECONDS.sleep(2);
//            } else {
//                DouyinPlaywright.log.debug("直播间未在直播，无需监听请求");
//                // 非直播状态：仅提取必要信息，不等待请求
//                if (room.getNickname() == null) {
//                    String nickname = extractStr(pageSource, NICKNAME_PATTERN,
//                            new HashSet<>(Collections.singletonList("$undefined")));
//                    room.setNickname(nickname != null ? nickname : "未知昵称");
//                }
//            }
//
//        } catch (Exception e) {
//            DouyinPlaywright.log.error("初始化直播间失败: " + room.getRoomUrl(), e);
//        } finally {
//            if (page != null) {
//                try {
//                    page.close();
//                } catch (Exception e) {
//                    DouyinPlaywright.log.warn("关闭Page失败", e);
//                }
//            }
//        }
//    }

    /**
     * 仅在直播时调用：注册请求监听器并等待目标请求
     */
//    private static void listenToEnterRequest(Page page, DouYinRoom room) throws InterruptedException {
//        CountDownLatch enterLatch = new CountDownLatch(1);
//
//        // 注册请求监听器（仅直播时执行）
//        page.onRequest(request -> {
//            String url = request.url();
//            if (url.startsWith("https://live.douyin.com/webcast/room/web/enter/")) {
//                room.setData_url(url);
//                room.setUser_agent(request.headers().get("user-agent"));
//                DouyinPlaywright.log.debug("===== 捕获直播请求 URL: {}\n===== 用户代理: {}", url, room.getUser_agent());
//                enterLatch.countDown();
//            }
//        });
//
//        // 等待目标请求（最多5秒）
//        boolean requestCaptured = enterLatch.await(5, TimeUnit.SECONDS);
//        if (!requestCaptured) {
//            DouyinPlaywright.log.warn("直播状态下未捕获到enter接口请求，可能影响数据准确性");
//        }
//    }

    /**
     * 提取直播间信息（独立方法，便于维护）
     */
    private static void extractRoomInfo(DouYinRoom room, String pageSource) {
        // 提取直播状态
        String status = extractStr(pageSource, STATUS_STR_PATTERN, null);
        room.setLiving("2".equals(status));

        // 提取昵称（避免重复提取）
        if (room.getNickname() == null) {
            String nickname = extractStr(pageSource, NICKNAME_PATTERN,
                    new HashSet<>(Collections.singletonList("$undefined")));
            room.setNickname(nickname);
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

    public static void init(DouYinRoom room) {
        try (Playwright playwright = Playwright.create();
             // 2. 启动Chrome浏览器
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true) // 无头模式：true不显示窗口，false显示
                     .setArgs(Arrays.asList(
                             "--disable-blink-features=AutomationControlled",//设置反检测
                             "--user-agent=" + Constant.User_Agent,
                             "--no-sandbox", // 提升容器环境兼容性
                             "--disable-gpu" // 禁用GPU加速，减少资源占用
                     )))) {
            Page page = browser.newPage();

            // 3. 禁用自动化特征（关键：避免被抖音检测）
            String initScript =
                    // 基础反检测
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
                            "delete window.chrome;\n" +
                            // 模拟真实浏览器插件和mime类型
                            "Object.defineProperty(navigator, 'plugins', { get: () => [\n" +
                            "  { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer' },\n" +
                            "  { name: 'Widevine Content Decryption Module', filename: 'widevinecdm.dll' }\n" +
                            "] });\n" +
                            "Object.defineProperty(navigator, 'mimeTypes', { get: () => [\n" +
                            "  { type: 'application/pdf', suffixes: 'pdf' }\n" +
                            "] });\n" +
                            // 模拟真实屏幕和设备信息
                            "Object.defineProperty(screen, 'width', { get: () => 1920 });\n" +
                            "Object.defineProperty(screen, 'height', { get: () => 1080 });\n" +
                            "Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });\n" + // 模拟8GB内存
                            "Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });\n" + // 模拟8核CPU
                            // 模拟用户行为痕迹（如点击）
                            "document.addEventListener('DOMContentLoaded', () => {\n" +
                            "  const event = new MouseEvent('click', { clientX: 100, clientY: 200, bubbles: true });\n" +
                            "  document.body.dispatchEvent(event);\n" +
                            "});";
            page.addInitScript(initScript);

            // 清理页面缓存（每次导航前执行）
            page.context().clearCookies();
//            page.context().clearPermissions();
            // 监听所有请求（获取请求参数）
            page.onRequest(request -> {
                // 过滤需要的接口（例如包含 "api"、"data" 等关键词的接口）
                String url = request.url();
                if ("GET".equalsIgnoreCase(request.method()) && url.startsWith("https://live.douyin.com/webcast/room/web/enter/")) {
                    room.setData_url(url);
                    // 获取请求头
                    String user_agent = request.headers().get("user-agent");
                    room.setUser_agent(user_agent);
                    DouyinPlaywright.log.debug("直播{}开启\n===== 直播监听 URL: {}\n===== 用户代理: {}", room.isLiving() ? "已" : "未", url, user_agent);
                }
            });

            // 4. 访问抖音页面
            page.navigate(room.getRoomUrl(), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(30000)
            );
            // 等待页面加载完成
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));

            // 5. 获取页面源码
            String pageSource = page.content();
//            System.out.println("页面源码长度: " + pageSource.length());
//            System.out.println(pageSource);
            // 6. 提取信息
            extractRoomInfo(room, pageSource);


            if (room.isLiving()) {
                // 等待一段时间，确保异步请求被捕获
                try {
                    int random = RandomUtil.createRandom(2, 5);
                    TimeUnit.SECONDS.sleep(random);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }


    public static void main(String[] args) {
        // 1. 初始化Playwright（自动下载浏览器）
        try (Playwright playwright = Playwright.create()) {
            // 2. 启动Chrome浏览器（设置反检测）
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true) // 无头模式：true不显示窗口，false显示
                    .setArgs(Collections.singletonList("--disable-blink-features=AutomationControlled")));
            Page page = browser.newPage();

            // 3. 禁用自动化特征（关键：避免被抖音检测）
            String initScript =
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
                            "delete window.chrome;\n" +
                            "if (window.chrome && window.chrome.loadTimes) {\n" +
                            "    delete window.chrome.loadTimes;\n" +
                            "}";
            page.addInitScript(initScript);

            // 1. 监听所有请求（获取请求参数）
            page.onRequest(request -> {
                // 过滤需要的接口（例如包含 "api"、"data" 等关键词的接口）
                String url = request.url();
                if (url.contains("https://live.douyin.com/webcast/room/web/enter/")) {
                    System.out.println("\n===== 请求 URL: " + url);
                    System.out.println("请求方法: " + request.method());

                    // 获取请求头
                    System.out.println("请求头: " + request.headers().get("user-agent"));

                    // 获取 POST 请求的表单数据
                    if ("POST".equals(request.method())) {
                        System.out.println("POST 数据: " + request.postData());
                    }
                }
            });

            // 2. 监听所有响应（获取接口返回数据）
//            page.onResponse(response -> {
//                String url = response.url();
//                if (url.contains("https://live.douyin.com/webcast/room/web/enter/")) {
//                    System.out.println("\n===== 响应 URL: " + url);
//                    System.out.println("响应状态: " + response.status());
//
//                    try {
//                        // 获取响应体（JSON 格式）
//                        String responseBody = response.text();
//                        // 只打印前 500 字符（避免输出过长）
////                        if (responseBody.length() > 500) {
////                            responseBody = responseBody.substring(0, 500) + "...";
////                        }
//                        System.out.println("响应数据: " + responseBody);
//                    } catch (Exception e) {
//                        System.out.println("获取响应体失败: " + e.getMessage());
//                    }
//                }
//            });

            // 4. 访问抖音页面
            page.navigate("https://live.douyin.com/208823316033");
            // 等待页面加载完成
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // 5. 获取页面源码
            String pageSource = page.content();
//            System.out.println("页面源码长度: " + pageSource.length());
//
//            System.out.println(pageSource);
            // 6. 提取信息（示例：room_id_str）
            String name = extractStr(pageSource, NICKNAME_PATTERN, new HashSet<>(Collections.singletonList("$undefined")));
            String status = extractStr(pageSource, STATUS_STR_PATTERN, null);

            boolean isLiving = status.equals("2");
            System.out.println(name + ":" + status);


            if (isLiving) {
                // 等待一段时间，确保异步请求被捕获
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ignored) {
                }
            }
            // 关闭浏览器
            browser.close();
        }
    }


}
