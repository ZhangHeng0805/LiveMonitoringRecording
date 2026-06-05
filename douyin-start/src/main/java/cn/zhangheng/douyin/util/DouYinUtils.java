package cn.zhangheng.douyin.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.common.util.UserAgentUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 04:51
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinUtils {
    @Getter
    private String ttwid, userAgent;
    private final AtomicInteger count = new AtomicInteger(0);
    private static final UserAgentUtil userAgentUtil = new UserAgentUtil();

    public int getCount() {
        return count.get();
    }

    public DouYinUtils() {
        refresh();
    }

    public static void main(String[] args) {
//        String roomID = "622216334529";
//        String roomID = "208823316033";
        String roomID = "381351302222";
        DouYinUtils utils = new DouYinUtils();
        String x = utils.fetchRoomPageBody(roomID);
//        System.out.println(x);

        System.out.println(extractRoomJson(x).toStringPretty());
    }

    public String fetchTtwid() {
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest("https://live.douyin.com/", MapUtil.of("User-Agent", userAgent));
            String setCookies = connection.getHeaderField("Set-Cookie");
            for (String c : setCookies.split(";")) {
                List<HttpCookie> cookies = HttpCookie.parse(c);
                for (HttpCookie cookie : cookies) {
                    if ("ttwid".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.error("刷新ttwid错误:{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    public String fetchRoomPageBody(String liveId) {
        String domain = "https://live.douyin.com/";
        String url = domain + liveId;
        String cookie = "ttwid=" + ttwid + ";msToken=" + msToken() + "; __ac_nonce=0123407cc00a9e438deb4";
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "*/*");
        headers.put("Cookie", cookie);
        headers.put("Referer", domain);
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest(url, headers);
            count.incrementAndGet();
            return RequestUtils.responseAsString(connection);
        } catch (Exception e) {
            log.error("请求失败！{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (getCount() % 100 == 0) {
                refresh();
            }
        }
        return "";
    }

    private void refresh() {
        userAgent = userAgentUtil.get();
        String fetchTtwid = fetchTtwid();
        if (fetchTtwid != null) {
            ttwid = fetchTtwid;
        } else {
            log.warn("获取ttwid为null");
        }
    }

    public static JSONObject extractRoomJson(String pageBody) {
        String sub1 = pageBody.substring(pageBody.lastIndexOf("\\\"roomStore\\\":") + 14);
        String sub2 = sub1.substring(0, sub1.indexOf(",\\\"emojiList\\\":"));
        String rep = sub2.replace("\\\"", "\"").replace("\\\"", "\"");
        String json = UnicodeUtil.toString(rep) + "}";
        return JSONUtil.parseObj(json);
    }

    private static String msToken() {
        return RandomUtil.randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", 182);
    }
}
