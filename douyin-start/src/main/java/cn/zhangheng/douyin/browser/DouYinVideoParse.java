package cn.zhangheng.douyin.browser;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.douyin.bean.work.DouYinWork;
import cn.zhangheng.douyin.bean.work.DouYinWorkCounter;
import cn.zhangheng.douyin.util.DouYinUtil;
import cn.zhangheng.record.bean.Constant;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.bean.work.Author;
import cn.zhangheng.record.bean.work.Work;
import cn.zhangheng.record.util.RequestUtils;
import cn.zhangheng.record.util.UserAgentUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 12:44
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinVideoParse {

    private static final String urlVideoPrefix = "https://www.douyin.com/aweme/v1/web/aweme/detail/";
    private static final String urlImagePrefix = "https://www.douyin.com/aweme/v1/web/mix/aweme/";
    private static final String urlImagePrefix2 = "https://www.douyin.com/aweme/v1/web/aweme/post/";
    private static final TimedCache<String, DouYinWork> timedCache = CacheUtil.newTimedCache(1000 * 60 * 10);

    public static void main(String[] args) throws IOException {
//        String s = "1.76 复制打开抖音，看看【小兰花的作品】自我救赎的路上本就光怪陆离# 感觉至上  https://v.douyin.com/jvzZf1jio5w/ 10/01 t@R.xS hBT:/ ";
//        String s = "0.28 Z@z.Ty :1pm 03/22 xsR:/ 那个……可以认识一下吗# 感觉至上 # 男生  https://v.douyin.com/HFnMsnbv_yI/ 复制此链接，打开Dou音搜索，直接观看视频！";
        String s = "5.87 素衫自有凌云气 锐目全无畏世心   https://v.douyin.com/ksjaDSGgWrg/ 复制此链接，打开抖音搜索，直接观看视频！ M@W.mQ :0pm dAg:/ 03/12 ";
//        String s = "6.12 复制打开抖音，看看【总掉萌萌泪的图文作品】便利店后面吸烟的两人。# 短发 # 日系 # 社畜... https://v.douyin.com/gi6Twv6Zb60/ H@v.FH 06/10 Rxs:/ :7pm ";
//        String s = "3.33 复制打开抖音，看看【吃不胖鸭丫的图文作品】旅行者，樱花瓣落得轻，风里裹着甜香～——我刚理完璃... https://v.douyin.com/T_5owcnkwak/ 10/27 BTL:/ q@R.KW ";
//        String s = "7.64 kCU:/ 09/28 i@p.QK 一起来看日出吧@小兰花 # 小兰花 # 看日出 # 直播截图  https://v.douyin.com/sX4ZhOA7M3Q/ 复制此链接，打开Dou音搜索，直接观看视频！";
//        String s = "8.43 j@P.xf 12/24 trr:/ 这个运镜好好玩，大家也可以试试# 感觉至上  https://v.douyin.com/BgpRfDDUeyw/ 复制此链接，打开Dou音搜索，直接观看视频！";
//        Setting setting = Setting.getInstance();
//        setting.setBrowserHeadless(false);
        long sta = System.currentTimeMillis();
        String link = DouYinUtil.extractDouyinLink(s);
        System.out.println(link);
//        String finalUrl = DouYinUtil.getFinalUrl(link);
//        System.out.println(finalUrl);
//        System.out.println(DouYinUtil.extractNoteId(finalUrl));
        System.out.println(JSONUtil.parseObj(
                parse(s, null, null, false)).toStringPretty());
        System.out.println("耗时:" + (System.currentTimeMillis() - sta));
    }

    public static DouYinWork parse(String shareUrl, String userAgent) {
        return parse(shareUrl, null, userAgent, false);
    }

    public static DouYinWork parse(String shareUrl, String userAgent, boolean isRefresh) {
        return parse(shareUrl, null, userAgent, isRefresh);
    }

    public static DouYinWork parse(String shareUrl, Setting setting, String userAgent, boolean isRefresh) {
        boolean success = false;
        String link = DouYinUtil.extractDouyinLink(shareUrl);
        if (link == null) {
            return null;
        }
        if (isRefresh) timedCache.remove(link);
        DouYinWork work = timedCache.get(link);
        if (work != null) {
            return work;
        }
        if (userAgent == null) {
            userAgent = UserAgentUtil.getRandomPc();
        }
        BrowserAPI browserApi = new BrowserAPI(null);
        JSONObject htmlJson = null;
        boolean headless = setting == null || !Objects.equals(setting.getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(headless))
        ) {
            BrowserContext context = browser.newContext(DouYinUtil.buildDouyinPcContextOptions(userAgent));
            Page page = context.newPage();
            page.addInitScript(DouYinUtil.getDouyinFingerprintScript());
            BrowserUtil.navigatePage(link, page, WaitUntilState.DOMCONTENTLOADED);
            htmlJson = extractHtmlJson(page.content());
            Response response;
            try {
                response = BrowserUtil.waitForTargetResponse(page, 10_000, urlVideoPrefix, urlImagePrefix, urlImagePrefix2);
            } catch (Exception e) {
                log.info("页面刷新，重新监听响应！");
                page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                response = BrowserUtil.waitForTargetResponse(page, 15_000, urlVideoPrefix, urlImagePrefix, urlImagePrefix2);
            }
            browserApi.setDataUrl(response.url());
            browserApi.setHeaders(response.request().headers());
            browserApi.setResponseBody(response.text());
            success = true;
        } catch (Exception e) {
            log.error("获取抖音作品信息失败！{}", e.getMessage());
        }
        if (success) {
            if (browserApi.getDataUrl().startsWith(urlVideoPrefix)) {
                work = handleVideoData(browserApi.getResponseBody());
            } else {
                work = handleImageData(browserApi.getResponseBody(), htmlJson);
            }
            timedCache.put(link, work);
            return work;
        } else {
            throw new RuntimeException("未解析到作品信息");
        }
    }


    public static String request(BrowserAPI browserAPI) {
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest(browserAPI.getDataUrl(), browserAPI.getHeaders());
            return RequestUtils.responseAsString(connection);
        } catch (Exception e) {
            log.error("请求失败！{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return "";
    }

    public static DouYinWork parse2(String shareUrl, String userAgent) {
        String link = DouYinUtil.extractDouyinLink(shareUrl);
        if (link == null) {
            return null;
        }
        if (userAgent == null) {
            userAgent = UserAgentUtil.getRandomPc();
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(true))
        ) {
            BrowserContext context = browser.newContext(DouYinUtil.buildDouyinPcContextOptions(userAgent));
            Page page = context.newPage();
            page.addInitScript(DouYinUtil.getDouyinFingerprintScript());
            BrowserUtil.navigatePage(link, page, WaitUntilState.LOAD);
            String content = page.content();
//            System.out.println(content);
            JSONObject entries = extractHtmlJson(content);
            System.out.println(entries);
            return handleImageData(entries);
        } catch (Exception e) {
            log.error("获取抖音作品信息失败！{}", e.getMessage());
        }
        return null;
    }


    public static JSONObject extractHtmlJson(String html) {
        String sub1 = html.substring(html.indexOf("{\\\"awemeId\\\":"));
        String sub2 = sub1.substring(0, sub1.indexOf("]\\n\"])</script>"));
        String sub3 = sub2.replace("\\\"", "\"")
                .replace("\\\\\"", "\\\"")
                .replace("\"$undefined\"", "null");
        JSONObject entries = JSONUtil.parseObj(sub3);
//        System.out.println(entries.toStringPretty());
        return entries;
    }

    private static DouYinWork handleImageData(JSONObject htmlJson) {
        DouYinWork work = new DouYinWork(Work.Type.Image);
        JSONObject aweme = htmlJson.getJSONObject("aweme");
        if (aweme == null) return work;
        if (aweme.getInt("statusCode") == 0) {
            JSONObject detail = aweme.getJSONObject("detail");
            work.setTitle(detail.getStr("desc"));
            work.setCreateTime(TimeUtil.toTime(detail.getLong("createTime") * 1000));
            JSONObject authorInfo = detail.getJSONObject("authorInfo");
            Author author = new Author();
            author.setNickname(authorInfo.getStr("nickname"));
            author.setAvatar(authorInfo.getStr("avatarUri"));
            author.setFollower_count(authorInfo.getInt("followerCount"));
            work.setAuthor(author);
            JSONObject stats = detail.getJSONObject("stats");
            DouYinWorkCounter counter = new DouYinWorkCounter();
            counter.setLike_count(stats.getLong("diggCount"));
            counter.setCollect_count(stats.getInt("collectCount"));
            counter.setComment_count(stats.getInt("commentCount"));
            counter.setRecommend_count(stats.getInt("recommendCount"));
            counter.setShare_count(stats.getInt("shareCount"));
            work.setCounter(counter);
            String audio = detail.getJSONObject("music").getJSONObject("playUrl").getJSONArray("urlList").getStr(0);
            work.setAudioUrl(audio);
            JSONArray imagesObj = detail.getJSONArray("images");
            List<DouYinWork.Image> images = new ArrayList<>();
            List<DouYinWork.Video> videos = new ArrayList<>();
            for (int i = 0; i < imagesObj.size(); i++) {
                JSONObject img = imagesObj.getJSONObject(i);
                DouYinWork.Image image = new DouYinWork.Image();
                image.setUrl(img.getJSONArray("urlList").getStr(0));
                image.setSpecifications(img.getInt("width"), img.getInt("height"));
                images.add(image);
                JSONObject vid = img.getJSONObject("video");
                if (vid != null) {
                    DouYinWork.Video video = new DouYinWork.Video();
                    video.setUrl(vid.getStr("playApi"));
                    video.setSize(vid.getLong("dataSize"));
                    video.setSpecifications(vid.getInt("width"), vid.getInt("height"));
                    video.setCover(vid.getStr("cover"));
                    videos.add(video);
                }
            }
            work.setImages(images);
            if (!videos.isEmpty()) {
                work.setVideos(videos);
            }
        }
        return work;
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

    private static DouYinWork handleVideoData(String data) {
        DouYinWork video = new DouYinWork(Work.Type.Video);
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
                    video.parseAuthor(author);
                    JSONObject statistics = awemeDetail.getJSONObject("statistics");
                    video.parseCounter(statistics);
                    JSONObject music = awemeDetail.getJSONObject("music");
                    if (music != null) {
                        video.setAudioUrl(music.getJSONObject("play_url").getJSONArray("url_list").getStr(0));
                    }

                    List<DouYinWork.Image> images = new ArrayList<>();
                    JSONObject entries = awemeDetail.getJSONObject("video");
                    DouYinWork.Image image1 = new DouYinWork.Image();
                    DouYinWork.Image image2 = new DouYinWork.Image();
                    JSONObject cover = entries.getJSONObject("cover");
                    image1.setUrl(cover.getJSONArray("url_list").getStr(0));
                    image1.setSpecifications(cover.getInt("width"), cover.getInt("height"));
                    JSONObject dynamicCover = entries.getJSONObject("dynamic_cover");
                    image2.setUrl(dynamicCover.getJSONArray("url_list").getStr(0));
                    image2.setSpecifications(dynamicCover.getInt("width"), dynamicCover.getInt("height"));
                    images.add(image1);
                    images.add(image2);
                    video.setImages(images);

                    List<DouYinWork.Video> videos = new ArrayList<>();
                    JSONObject playAddr = entries.getJSONObject("play_addr");
                    Long size = playAddr.getLong("data_size");
                    String specifications = playAddr.getInt("width") + " × " + playAddr.getInt("height");
                    JSONArray urlList = playAddr.getJSONArray("url_list");
                    for (int i = 0; i < urlList.size(); i++) {
                        DouYinWork.Video vid = new DouYinWork.Video();
                        vid.setSpecifications(specifications);
                        vid.setSize(size);
                        vid.setUrl(urlList.getStr(i));
                        vid.setCover(image2.getUrl());
                        videos.add(vid);
                    }
                    video.setVideos(videos);
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

    private static DouYinWork handleImageData(String data, JSONObject htmlJson) {
        DouYinWork work = handleImageData(htmlJson);
        JSONConfig jsonConfig = new JSONConfig();
        jsonConfig.setIgnoreError(true);
        if (JSONUtil.isTypeJSON(data)) {
            JSONObject obj = JSONUtil.parseObj(data, jsonConfig);
//            System.out.println(obj);
            if (obj.getInt("status_code") == 0) {
                JSONArray awemeList = obj.getJSONArray("aweme_list");
                if (awemeList != null && !awemeList.isEmpty()) {
                    JSONObject aweme_list = null;
                    String awemeId = htmlJson.getStr("awemeId");
                    for (int i = 0; i < awemeList.size(); i++) {
                        if (awemeId.equals(awemeList.getJSONObject(i).getStr("aweme_id"))) {
                            aweme_list = awemeList.getJSONObject(i);
                            break;
                        }
                    }
                    if (aweme_list == null) return work;
                    JSONObject author = aweme_list.getJSONObject("author");
                    JSONObject statistics = aweme_list.getJSONObject("statistics");
                    work.parseAuthor(author);
                    work.parseCounter(statistics);
                    work.setTitle(aweme_list.getStr("preview_title"));
                    work.setCreateTime(TimeUtil.toTime(new Date(aweme_list.getLong("create_time") * 1000)));
                    JSONObject music = aweme_list.getJSONObject("music");
                    if (music != null && work.getAudioUrl() == null) {
                        work.setAudioUrl(music.getJSONObject("play_url").getJSONArray("url_list").getStr(0));
                    }
                    if (work.getImages() != null) return work;
                    JSONArray imagesObj = aweme_list.getJSONArray("images");
                    if (imagesObj != null && !imagesObj.isEmpty()) {
                        List<DouYinWork.Image> images = new ArrayList<>();
                        List<DouYinWork.Video> videos = new ArrayList<>();
                        for (int i = 0; i < imagesObj.size(); i++) {
                            JSONObject img = imagesObj.getJSONObject(i);
                            DouYinWork.Image image = new DouYinWork.Image();
                            image.setUrl(img.getJSONArray("url_list").getStr(0));
                            image.setSpecifications(img.getInt("width"), img.getInt("height"));
                            images.add(image);
                            JSONObject vid = img.getJSONObject("video");
                            if (vid != null) {
                                DouYinWork.Video video = new DouYinWork.Video();
                                JSONObject playAddr = vid.getJSONObject("play_addr");
                                video.setUrl(playAddr.getJSONArray("url_list").getStr(0));
                                video.setSize(playAddr.getLong("data_size"));
                                video.setSpecifications(playAddr.getInt("width"), playAddr.getInt("height"));
                                video.setCover(vid.getJSONObject("cover").getJSONArray("url_list").getStr(0));
                                videos.add(video);
                            }
                            work.setImages(images);
                            if (!videos.isEmpty()) {
                                work.setVideos(videos);
                            }
                        }

                    }
                }
            }
        } else {
            throw new RuntimeException("解析格式错误：" + data);
        }
        return work;
    }

}
