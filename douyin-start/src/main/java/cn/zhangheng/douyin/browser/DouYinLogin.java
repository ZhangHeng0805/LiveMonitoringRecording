package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.common.bean.Constant;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.zhangheng.file.TxtOperation;
import com.zhangheng.util.UserAgentUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/15 星期五 03:26
 * @version: 1.0
 * @description:
 */
public class DouYinLogin {

    public static void main(String[] args) {
        open();
    }

    public static void open() {
        String url1 = "https://www.douyin.com/";
        String url2 = "https://www.douyin.com/user/self";
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(Constant.User_Agent, false))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
            BrowserContext context = browser.newContext(contextOptions);
            Page page = context.newPage();
            System.out.println("正在打开抖音网页版...");
            System.out.println("请使用手机扫码登录");
            boolean isLoginSuccess = BrowserUtil.goToBySelector(url1, page, "span[data-e2e='live-avatar']", 120);

            if (!isLoginSuccess) {
                System.err.println("登录超时，未检测到登录状态，浏览器关闭！");
                browser.close();
                return;
            }
            System.out.println("===== 抖音登录成功 =====");
            System.out.println("===== 访问抖音个人主页 =====");

            boolean isUserSuccess = BrowserUtil.goToBySelector(url2, page, "div[data-e2e='user-info']", 30);
            String user;
            if (isUserSuccess) {
                user = getUser(page);
                System.out.println("登录用户：" + user);
            } else {
                System.err.println("获取用户超时，未检测到页面用户信息");
                user = System.currentTimeMillis() + "";
            }

            // 打印完整 Cookie
            List<Cookie> cookies = context.cookies();

            String cookieStr = BrowserUtil.toCookieStr(cookies);
            System.out.println("用户登录后cookie:" + cookieStr);
            try {
                File file = TxtOperation.creatTxtFile("cookie/douyin-" + user + ".txt");
                TxtOperation.writeTxtFile(cookieStr, file, "UTF-8", false);
                System.out.println("\n用户cookie信息已保存至：" + file.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 6. 关闭浏览器
            browser.close();
            System.out.println("\n登录信息 获取完成！");
        }
    }

//    public static boolean goTo(String url, Page page, String selector, int maxWaitTimeSec) {
//        boolean b = navigatePage(url, page, WaitUntilState.LOAD);
//        if (!b) return false;
//        boolean isLoginSuccess = false;
//        int count = 0;
//
//        while (count < maxWaitTimeSec) {
//            try {
//                // 检测已登录标识：头像/个人中心元素（抖音网页版已登录独有）
//                // 选择器可根据页面微调
//                page.waitForSelector(selector, new Page.WaitForSelectorOptions()
//                        .setTimeout(1500));
//                isLoginSuccess = true;
//                break;
//            } catch (Exception e) {
//                // 未登录，休眠继续轮询
//                count++;
//                try {
//                    TimeUnit.SECONDS.sleep(1);
//                } catch (InterruptedException ignored) {
//                }
//            }
//        }
//        return isLoginSuccess;
//    }




    public static String getUser(Page page) {
        String nickname = page.textContent("div[data-e2e='user-info'] div h1 span");
        String id = page.textContent("div[data-e2e='user-info'] p span");
        String x = nickname + "-" + id.substring(id.indexOf("：") + 1);
        return x;
    }
}
