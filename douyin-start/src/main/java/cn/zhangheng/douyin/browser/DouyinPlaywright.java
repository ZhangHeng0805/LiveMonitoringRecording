package cn.zhangheng.douyin.browser; /**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/14 星期日 05:14
 * @version: 1.0
 * @description:
 */

import cn.zhangheng.browser.API;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.*;

/**
 * 浏览器静态类，单次请求，请求完成自动关闭浏览器
 */
public class DouyinPlaywright {


    private static final Logger log = LoggerFactory.getLogger(DouyinPlaywright.class);

    private DouyinPlaywright() {
    }

    public static void request(DouYinRoom room) {
        PlaywrightBrowser browser = null;
        try {
            browser = new PlaywrightBrowser(Constant.User_Agent,true);
            Page page = browser.newPage();
            API api = new API(TARGET_REQUEST_PREFIX);
            Consumer<Request> handler = request -> {
                // 过滤需要的接口（例如包含 "api"、"data" 等关键词的接口）
                getRequestApi(room, request, api);
            };
            page.onRequest(handler);

            // 4. 访问抖音页面
            browser.navigatePage(room.getRoomUrl(), page);

            // 5. 获取页面源码
            String pageSource = page.content();
//            System.out.println("页面源码长度: " + pageSource.length());
//            System.out.println(pageSource);
            // 6. 提取信息
            DouYinBrowserFactory.extractRoomInfo(room, pageSource);

            if (room.isLiving()) {
                // 等待一段时间，确保异步请求被捕获
                browser.waitForTargetRequest(page, TARGET_REQUEST_PREFIX, 10_000);
            }else {
//                try {
//                    TimeUnit.SECONDS.sleep(20);
//                } catch (InterruptedException ignored) {
//                }
            }
//            page.offRequest(handler);
        } finally {
            if (browser != null) {
                browser.close();
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
