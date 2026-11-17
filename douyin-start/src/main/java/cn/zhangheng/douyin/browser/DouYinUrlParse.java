package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.factory.MyBrowser;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.douyin.util.DouYinUtil;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/19 星期六 19:49
 * @version: 1.0
 * @description:
 */
public class DouYinUrlParse {

    public static void main(String[] args) throws IOException {
        String s = "9- #在抖音，记录美好生活#【央视纪录片频道直播】直播中 。复制下方链接，打开【抖音】，直接观看节目！ https://v.douyin.com/KmPe69CGQ1A/ 4@0.com :5pm";
        String link = DouYinUtil.extractDouyinLink(s);
        System.out.println(link);
        long sta = System.currentTimeMillis();
        System.out.println(parseDouyinShareLink(s));
        System.out.println("耗时：" + (System.currentTimeMillis() - sta));
    }

    public static String parseDouyinShareLink(String shareLink) {
        String link = DouYinUtil.extractDouyinLink(shareLink);
        if (link == null) {
            return "";
        }
        MyBrowser myBrowser = PlaywrightBrowserFactory.getBrowser(DouYinUrlParse.class.getName(), 60_000, false);
        Page page = null;
        try {
            page = myBrowser.newPage(null);
            BrowserUtil.navigatePage(link, page, WaitUntilState.LOAD);
            return page.url();
        } finally {
            myBrowser.closePage(page);
        }
    }
}
