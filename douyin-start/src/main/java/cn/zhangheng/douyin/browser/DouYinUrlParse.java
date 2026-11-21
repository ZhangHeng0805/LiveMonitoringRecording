package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.factory.MyBrowser;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.douyin.util.DouYinUtil;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/19 星期六 19:49
 * @version: 1.0
 * @description:
 */
public class DouYinUrlParse {

    /** 抖音直播间页面地址：live.douyin.com/{数字ID}，用于从响应/URL 中提取跳转结果 */
    private static final Pattern LIVE_PAGE_PATTERN = Pattern.compile("live\\.douyin\\.com/\\d{5,20}");

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
        if (link == null || link.trim().isEmpty()) {
            return "";
        }
        MyBrowser myBrowser = PlaywrightBrowserFactory.getBrowser(DouYinUrlParse.class.getName(), 300_000, false);
        Page page = null;
        try {
            page = myBrowser.newPage(null);
            // 先挂响应对应监听：抖音跳到直播间时，第一个 live.douyin.com/{id} 的响应一出现就记下来，
            // 比等导航结束再轮询 page.url() 更早拿到地址
            final AtomicReference<String> liveUrlRef = new AtomicReference<>(null);
            page.onResponse(response -> {
                String respUrl = response.url();
                if (respUrl != null && liveUrlRef.get() == null && LIVE_PAGE_PATTERN.matcher(respUrl).find()) {
                    liveUrlRef.compareAndSet(null, respUrl);
                }
            });
            // COMMIT：网络响应一返回就结束导航等待，比 LOAD/DOMCONTENTLOADED 都快
            BrowserUtil.navigatePage(link, page, WaitUntilState.COMMIT, 10_000);

            String captured = liveUrlRef.get();
            if (captured != null) {
                return captured;
            }
            // 事件没抓到（例如跳转在导航完成前就结束了），再按地址变化等一段时间
            try {
                page.waitForURL(LIVE_PAGE_PATTERN, new Page.WaitForURLOptions().setTimeout(10_000));
            } catch (Throwable ignore) {
                // 超时/页面关闭，下面统一回退到当前地址
            }
            captured = liveUrlRef.get();
            if (captured != null) {
                return captured;
            }
            return page.url() == null ? "" : page.url();
        } finally {
            myBrowser.closePage(page);
        }
    }
}
