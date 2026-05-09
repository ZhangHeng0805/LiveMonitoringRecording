package cn.zhangheng.douyin;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;

import java.net.HttpCookie;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 04:51
 * @version: 1.0
 * @description:
 */
public class DouYinUtils {

    public static void main(String[] args) {
//        String roomID = "622216334529";
//        String roomID = "208823316033";
        String roomID = "381351302222";
        String x = fetchRoomPageBody(roomID);
//        System.out.println(x);

        System.out.println(extractRoomJson(x));
    }

    public static String fetchTtwid() {
        HttpRequest request = HttpRequest
                .get("https://live.douyin.com/")
                .header("User-Agent", Constant.User_Agent);

        try (HttpResponse resp = request.execute()) {
            String setCookies = resp.header("Set-Cookie");
            for (String c : setCookies.split(";")) {
                List<HttpCookie> cookies = HttpCookie.parse(c);
                for (HttpCookie cookie : cookies) {
                    if ("ttwid".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String fetchRoomPageBody(String liveId) {
        String url = "https://live.douyin.com/" + liveId;
        String cookie = "ttwid=" + fetchTtwid() + ";msToken=" + msToken() + "; __ac_nonce=0123407cc00a9e438deb4";

        HttpRequest request = HttpRequest.get(url)
                .header("User-Agent", Constant.User_Agent)
                .header("Cookie", cookie);
        try (HttpResponse resp = request.execute()) {
            String body = resp.body();
            System.out.println(DouYinBrowserFactory.extractNickname(body));
            System.out.println(DouYinBrowserFactory.extractLivingStatus(body));
            return body;
        }
    }

    public static JSONObject extractRoomJson(String pageBody) {
        String sub1 = pageBody.substring(pageBody.lastIndexOf("\\\"roomStore\\\":")+14);
        String sub2 = sub1.substring(0, sub1.indexOf(",\\\"emojiList\\\":"));
        String rep = sub2.replace("\\\"", "\"").replace("\\\"", "\"");
        String json = UnicodeUtil.toString(rep)+"}";
        return JSONUtil.parseObj(json);
    }

    private static String msToken() {
        return RandomUtil.randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", 182);
    }
}
