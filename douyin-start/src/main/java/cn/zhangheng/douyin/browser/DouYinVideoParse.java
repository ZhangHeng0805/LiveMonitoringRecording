package cn.zhangheng.douyin.browser;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.API;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.douyin.DouYinVideo;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.zhangheng.util.TimeUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 12:44
 * @version: 1.0
 * @description:
 */
public class DouYinVideoParse {


    public static void main(String[] args) {
//        String s = "1.76 复制打开抖音，看看【小兰花的作品】自我救赎的路上本就光怪陆离# 感觉至上  https://v.douyin.com/jvzZf1jio5w/ 10/01 t@R.xS hBT:/ ";
        String s = "9.46 复制打开抖音，看看【小兰花的作品】知足常乐# 感觉至上 # 运镜  https://v.douyin.com/reo-okMgTPs/ FHv:/ 01/31 N@J.vF ";
//        String s = "8.43 j@P.xf 12/24 trr:/ 这个运镜好好玩，大家也可以试试# 感觉至上  https://v.douyin.com/BgpRfDDUeyw/ 复制此链接，打开Dou音搜索，直接观看视频！";

        System.out.println(extractDouyinLink(s));
        System.out.println(JSONUtil.parseObj(parse(s)).toStringPretty());
    }

    public static DouYinVideo parse(String shareUrl) {
        API api = new API("https://www.douyin.com/aweme/v1/web/aweme/detail/");
        boolean success;
        try (PlaywrightBrowser browser = new PlaywrightBrowser(Constant.User_Agent)) {
            Page page = browser.newPage();
//            browser.clearPageState(page);
            page.onResponse(response -> {
                String url = response.url();
                if (url.startsWith(api.getUrlPrefix())) {
//                        System.out.println("响应：" + response.text());
                    api.setResponseBody(response.text());
                }
            });
            page.navigate(extractDouyinLink(shareUrl));

//            success = browser.waitForTargetRequest(page, api.getUrlPrefix(), 10_000);
            success = browser.waitForTargetResponse(page, api.getUrlPrefix(), 15_000);
        }
        if (success) {
//            String data = getData(api);
            return handleData(api.getResponseBody());
        } else {
            throw new RuntimeException("未解析到视频信息");
        }

    }

    private static String getData(API api) {
        String dataUrl = api.getDataUrl();
        if (dataUrl != null) {
            Map<String, String> headers = api.getHeaders();
            headers.entrySet().removeIf(next -> next.getKey().startsWith(":"));
            System.out.println(dataUrl);
            System.out.println(JSONUtil.parseObj(headers).toStringPretty());
            HttpURLConnection connection = null;
            try {
                connection = RequestUtils.getRequest(dataUrl, headers);
                return RequestUtils.responseAsString(connection);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private static cn.zhangheng.douyin.DouYinVideo handleData(String data) {
        cn.zhangheng.douyin.DouYinVideo video = new cn.zhangheng.douyin.DouYinVideo();
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
                    video.setVideoSpecifications(playAddr.getStr("height")+"*"+playAddr.getStr("width"));
                    video.setCover(entries.getJSONObject("dynamic_cover").getJSONArray("url_list").getStr(0));
                } else {
                    JSONObject entries = obj.getJSONObject("filter_detail");
                    if (entries != null) {
                        throw new RuntimeException(entries.getStr("notice") + "! " + entries.getStr("detail_msg"));
                    }
                }
            }
        } else {
            throw new RuntimeException("格式错误：" + data);
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
