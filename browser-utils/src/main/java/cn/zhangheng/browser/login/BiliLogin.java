package cn.zhangheng.browser.login;

import cn.zhangheng.browser.BrowserUtil;
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
public class BiliLogin {

    public static void main(String[] args) {
        open();
    }

    public static void open() {
        String url1 = "https://www.bilibili.com/";
//        String url2 = "https://www.douyin.com/user/self";
        String userAgent = UserAgentUtil.getRandomUser_Agent();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(userAgent, false))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(userAgent);
            BrowserContext context = browser.newContext(contextOptions);
            Page page = context.newPage();
            System.out.println("正在打开Bilibili网页版...");
            System.out.println("请使用手机扫码登录,登录完成后将鼠标移动至账号头像处");
            boolean isLoginSuccess = BrowserUtil.goToBySelector(url1, page, "div.bili-avatar", 120);

            if (!isLoginSuccess) {
                System.err.println("登录超时，未检测到登录状态，浏览器关闭！");
                browser.close();
                return;
            }
            System.out.println("===== Bilibili登录成功 =====");
            String userURl = page.getAttribute("a[class='header-entry-avatar']", "href");

            userURl = userURl.startsWith("//") ? "https:" + userURl : userURl;

            System.out.println("===== 访问Bilibili个人主页 =====");
            System.out.println(userURl);

            boolean isUserSuccess = BrowserUtil.goToBySelector(userURl, page, "div[class='nickname']", 30);
            String user;
            if (isUserSuccess) {
                user = getUser(page);
                System.out.println("\n登录用户：" + user);
            } else {
                System.err.println("\n获取用户超时，未检测到页面用户信息");
                user = System.currentTimeMillis() + "";
            }

            // 打印完整 Cookie
            List<Cookie> cookies = context.cookies();

            String cookieStr = BrowserUtil.toCookieStr(cookies);
            System.out.println("用户登录后cookie:" + cookieStr);
            try {
                File file = TxtOperation.creatTxtFile("cookie/Bili-" + user + ".txt");
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


    public static String getUser(Page page) {
        String url = page.url();
        String id = url.substring(url.lastIndexOf("/") + 1);
        String nickname = page.textContent("div[class='nickname']");
        String x = nickname + "-" + id;
        return x;
    }
}
