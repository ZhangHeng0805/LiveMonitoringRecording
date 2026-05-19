package cn.zhangheng.douyin.browser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.browser.UserAgentUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.douyin.bean.DouYinVideo;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 12:44
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinVideoParse {

    private static final BrowserAPI browserApi = new BrowserAPI("https://www.douyin.com/aweme/v1/web/aweme/detail/");


    public static void main(String[] args) {
//        String s = "1.76 复制打开抖音，看看【小兰花的作品】自我救赎的路上本就光怪陆离# 感觉至上  https://v.douyin.com/jvzZf1jio5w/ 10/01 t@R.xS hBT:/ ";
        String s = "0.28 Z@z.Ty :1pm 03/22 xsR:/ 那个……可以认识一下吗# 感觉至上 # 男生  https://v.douyin.com/HFnMsnbv_yI/ 复制此链接，打开Dou音搜索，直接观看视频！";
//        String s = "3.33 复制打开抖音，看看【吃不胖鸭丫的图文作品】旅行者，樱花瓣落得轻，风里裹着甜香～——我刚理完璃... https://v.douyin.com/T_5owcnkwak/ 10/27 BTL:/ q@R.KW ";
//        String s = "7.64 kCU:/ 09/28 i@p.QK 一起来看日出吧@小兰花 # 小兰花 # 看日出 # 直播截图  https://v.douyin.com/sX4ZhOA7M3Q/ 复制此链接，打开Dou音搜索，直接观看视频！";
//        String s = "8.43 j@P.xf 12/24 trr:/ 这个运镜好好玩，大家也可以试试# 感觉至上  https://v.douyin.com/BgpRfDDUeyw/ 复制此链接，打开Dou音搜索，直接观看视频！";
        Setting setting = new Setting();
        System.out.println(extractDouyinLink(s));
        System.out.println(JSONUtil.parseObj(
                parse1(s
                , setting
        )).toStringPretty());
    }

    public static DouYinVideo parse(String shareUrl) {
        return parse1(shareUrl, null);
    }

    public static DouYinVideo parse1(String shareUrl, Setting setting) {
        boolean success = false;
        String link = extractDouyinLink(shareUrl);
        if (link == null) {
            return null;
        }
        Page page = null;
        boolean headless = setting == null || !Objects.equals(setting.getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(Constant.User_Agent, headless))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
            BrowserContext context = browser.newContext(contextOptions);
            page = context.newPage();
            BrowserUtil.navigatePage(link, page, WaitUntilState.DOMCONTENTLOADED);
            Response response;
            try {
                response = BrowserUtil.waitForTargetResponse(page, browserApi.getUrlPrefix(), 10_000);
            } catch (Exception e) {
                log.info("页面刷新，重新监听请求！");
                page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                response = BrowserUtil.waitForTargetResponse(page, browserApi.getUrlPrefix(), 15_000);
            }
            browserApi.setResponseBody(response.text());
            success = true;
        } catch (Exception e) {
            log.error("获取抖音视频信息失败！{}", e.getMessage());
        } finally {
            if (page != null && !page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }
        }
        if (success) {
            String data = browserApi.getResponseBody();
            return handleData(data);
        } else {
            throw new RuntimeException("未解析到视频信息");
        }
    }


    public static DouYinVideo parse(String shareUrl, Setting setting) {
        boolean success = false;
        String link = extractDouyinLink(shareUrl);
        if (link == null) {
            return null;
        }
        PlaywrightBrowser browser = null;
        try {
            browser = new PlaywrightBrowser(Constant.User_Agent);
            Page page = browser.newPage();
            //设置cookie
            if (setting != null) {
                BrowserContext context = page.context();
                String cookie = setting.parseCookie(setting.getCookieDouYin());
                if (StrUtil.isNotBlank(cookie) && context.cookies(link).isEmpty()) {
                    String host = null;
                    try {
                        host = new URL(link).getHost();
                    } catch (MalformedURLException ignored) {
                    }
                    if (host != null) {
                        context.addCookies(BrowserUtil.parseCookieString(host, cookie));
                        log.debug("设置cookie成功！");
                    }
                }
            }
//            page.onRequest(request -> {
//                String url = request.url();
//                if (url.startsWith(api.getUrlPrefix())) {
//                    api.setDataUrl(url);
//                    api.setHeaders(request.allHeaders());
//                }
//            });
            page.onResponse(response -> {
                String url = response.url();
                if (url.startsWith(browserApi.getUrlPrefix())) {
                    browserApi.setResponseBody(response.text());
                }
            });

            browser.navigatePage(link, page);

            checkVideoSelectors(page);

//            success = browser.waitForTargetRequest(page, api.getUrlPrefix(), 10_000);
//            success = browser.waitForTargetResponse(page, api.getUrlPrefix(), 10_000);
            success = StrUtil.isNotBlank(browserApi.getResponseBody());
        } catch (Throwable e) {
            log.error(ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (browser != null) {
                browser.close();
            }
        }
        if (success) {
//            String data = getData(api);
            String data = browserApi.getResponseBody();
            return handleData(data);
        } else {
            throw new RuntimeException("未解析到视频信息");
        }

    }

    private static void checkVideoSelectors(Page page) {
        // 抖音直播间核心元素（优先级从高到低，可根据实际情况调整）
        String[] liveSelectors = {
                ".xg-video-container",
        };
        for (String selector : liveSelectors) {
            try {
//                    System.out.println("等待核心元素加载：" + selector);
                page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.VISIBLE) // 必须可见，而非仅存在
                        .setTimeout(5_000));
                break; // 找到任意一个核心元素即可
            } catch (TimeoutError e) {
                log.warn("元素 " + selector + " 加载超时，尝试下一个...");
            }
        }
    }

    private static String getData(BrowserAPI browserApi) {
        String dataUrl = browserApi.getDataUrl();
        if (dataUrl != null) {
            Map<String, String> headers = browserApi.getHeaders();
            headers.remove("accept-encoding");
//            System.out.println(dataUrl);
//            System.out.println(JSONUtil.parseObj(headers).toStringPretty());
            HttpURLConnection connection = null;
            try {
                connection = RequestUtils.getRequest(dataUrl, headers);
                return RequestUtils.responseAsString(connection);
            } catch (IOException e) {
                log.error(ThrowableUtil.getAllCauseMessage(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private static DouYinVideo handleData(String data) {
        DouYinVideo video = new DouYinVideo();
        JSONConfig jsonConfig = new JSONConfig();
        jsonConfig.setIgnoreError(true);
        if (JSONUtil.isTypeJSON(data)) {
            JSONObject obj = JSONUtil.parseObj(data, jsonConfig);
//            System.out.println(obj);
            if (obj.getInt("status_code") == 0) {
                JSONObject awemeDetail = obj.getJSONObject("aweme_detail");
                if (awemeDetail != null) {
                    video.setTitle(awemeDetail.getStr("preview_title"));
                    video.setCreateTime(TimeUtil.toTime(new Date(awemeDetail.getLong("create_time") * 1000)));
                    JSONObject author = awemeDetail.getJSONObject("author");
                    video.setAuthor(author.getStr("nickname"));
                    video.setSignature(author.getStr("signature"));
                    video.setAvatar(author.getJSONObject("avatar_thumb").getJSONArray("url_list").getStr(0));
                    JSONObject statistics = awemeDetail.getJSONObject("statistics");
                    video.setLike_count(statistics.getLong("digg_count"));
                    video.setCollect_count(statistics.getInt("collect_count"));
                    video.setComment_count(statistics.getInt("comment_count"));
                    video.setRecommend_count(statistics.getInt("recommend_count"));
                    video.setShare_count(statistics.getInt("share_count"));
                    video.setAdmire_count(statistics.getInt("admire_count"));
                    JSONObject music = awemeDetail.getJSONObject("music");
                    if (music != null) {
                        video.setAudioUrl(music.getJSONObject("play_url").getJSONArray("url_list").getStr(0));
                    }
                    JSONObject entries = awemeDetail.getJSONObject("video");
                    JSONObject playAddr = entries.getJSONObject("play_addr");
                    JSONArray urlList = playAddr.getJSONArray("url_list");
                    video.setVideoUrl(urlList.getStr(urlList.size() - 1));
                    video.setSize(playAddr.getLong("data_size"));
                    video.setVideoSpecifications(playAddr.getStr("height") + "*" + playAddr.getStr("width"));
                    video.setCover(entries.getJSONObject("dynamic_cover").getJSONArray("url_list").getStr(0));
                } else {
                    JSONObject entries = obj.getJSONObject("filter_detail");
                    if (entries != null) {
                        throw new RuntimeException(entries.getStr("notice") + "! " + entries.getStr("detail_msg"));
                    }
                }
            }
        } else {
            throw new RuntimeException("解析格式错误：" + data);
        }
        return video;
    }

    /**
     * 从字符串中提取抖音链接
     *
     * @param input 包含抖音链接的字符串
     * @return 提取到的抖音链接，若未找到则返回null
     */
    public static String extractDouyinLink(String input) {
        // 正则表达式：匹配以https://v.douyin.com/开头，后面跟非空白字符的链接
//        String regex = "https://v\\.douyin\\.com/[\\w\\-]+/?";
        String regex = "https://[\\w\\.]+douyin\\.com/[\\w\\-/?&=]+";

        // 编译正则表达式
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        // 查找匹配的链接
        if (matcher.find()) {
            return matcher.group();
        }

        // 未找到匹配的链接
        return null;
    }
}
