package cn.zhangheng.douyin.util;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.common.util.UserAgentUtil;
import com.zhangheng.util.HttpURLConnectionUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.util.HashMap;
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
    private String cookie, userAgent;
    private final AtomicInteger count = new AtomicInteger(0);
    private static final UserAgentUtil userAgentUtil = new UserAgentUtil();
    private final Map<String, String> headers = new HashMap<>();

    public int getCount() {
        return count.get();
    }

    public DouYinUtils() {
        headers.put("Accept", "application/json,text/html,*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("sec-fetch-mode", "cors");
        headers.put("Referer", "https://live.douyin.com");
        refresh();
    }

    public static void main(String[] args) {
//        String roomID = "622216334529";
//        String roomID = "208823316033";
        String roomID = "381351302222";
        DouYinUtils utils = new DouYinUtils();
        System.out.println(utils.getCookie());
        String x = utils.fetchRoomPageBody(roomID);
//        System.out.println(x);
        System.out.println(extractRoomJson(x).toStringPretty());
        utils.refresh();
        System.out.println(utils.getCookie());

    }

    public String fetchCookie() {
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest("https://live.douyin.com/", headers);
            return HttpURLConnectionUtil.parseCookie(connection.getHeaderFields());
        } catch (Exception e) {
            log.error("刷新cookie错误:{}", ThrowableUtil.getAllCauseMessage(e));
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
        String cookie = getCookie() + "; __ac_nonce=0123407cc00a9e438deb4; msToken=" + msToken() ;
        headers.put("Cookie", cookie);
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
        headers.put("User-Agent", userAgent);
        String fetchCookie = fetchCookie();
        if (fetchCookie != null) {
            cookie = fetchCookie;
        } else {
            log.warn("获取cookie为null");
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
