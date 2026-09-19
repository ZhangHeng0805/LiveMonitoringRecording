package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.douyin.util.DouYinUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.UserAgentUtil;

import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/19 星期六 19:49
 * @version: 1.0
 * @description:
 */
public class DouYinUrlParse {
    public static void main(String[] args) {
        String s="9- #在抖音，记录美好生活#【央视纪录片频道直播】直播中 。复制下方链接，打开【抖音】，直接观看节目！ https://v.douyin.com/KmPe69CGQ1A/ 4@0.com :5pm";
        System.out.println(DouYinUtil.extractDouyinLink(null));
        long sta = System.currentTimeMillis();
        System.out.println(parseDouyinShareLink(s, UserAgentUtil.getRandomUser_Agent()));
        System.out.println("耗时："+(System.currentTimeMillis()-sta));
    }
    public static String parseDouyinShareLink(String shareLink, String userAgent) {
        String link = DouYinUtil.extractDouyinLink(shareLink);
        if (link == null) {return "";}
       try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(userAgent, true))
        ){
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(userAgent);
            BrowserContext context = browser.newContext(contextOptions);
            Page page = context.newPage();
//            Page page = browser.newPage();
            BrowserUtil.navigatePage(link, page, WaitUntilState.LOAD);
           String url = page.url();
           context.close();
           return url;
        }
    }
}
