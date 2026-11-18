package cn.zhangheng.douyin.util;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.util.UserAgentUtil;
import cn.zhangheng.record.okhttp.OkHttpUtil;
import cn.zhangheng.record.util.HttpHeaderUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 04:51
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinAPITools {
    private String cookie;
    private final HttpHeaderUtil headerUtil;
    private final AtomicInteger count = new AtomicInteger(0);
    private static final UserAgentUtil userAgentUtil = UserAgentUtil.create(true);
    private int refreshCount;

    public int getCount() {
        return count.get();
    }

    public DouYinAPITools(String domain) {
        this.headerUtil = new HttpHeaderUtil(domain);
        refresh();
    }

    public String fetchCookie() {
        headerUtil.removeCookie();
        try (Response response = OkHttpUtil.getResponse(headerUtil.getDomain(), headerUtil.getHeaders())) {
            return OkHttpUtil.parseResponseCookie(response);
        } catch (Exception e) {
            log.error("刷新cookie错误:{}", ThrowableUtil.getAllCauseMessage(e));
        }
        return null;
    }

    public String fetchBody(String url) {
        String cookieStr = cookie + "; __ac_nonce=0123407cc00a9e438deb4; msToken=" + msToken();
        headerUtil.setCookie(cookieStr);
        try (Response response = OkHttpUtil.getResponse(url, headerUtil.getHeaders())) {
            return OkHttpUtil.streamSubstringByFlag(response, "\\\"homeStore\\\":", ",\\\"emojiList\\\":", 2);
//            if (!response.isSuccessful()) {
//                throw new IOException("http code:" + response.code());
//            }
//            return response.body() != null ? response.body().string() : null;
        } catch (IOException e) {
            log.error("请求失败！{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            count.incrementAndGet();
            if (getCount() % refreshCount == 0) {
                refresh();
            }
        }
        return "";
    }

    private void refresh() {
        refreshCount = RandomUtil.randomInt(50, 100);
        headerUtil.setUserAgent(userAgentUtil.get());
        headerUtil.setSecHeader();
        String fetchCookie = fetchCookie();
        if (fetchCookie != null) {
            cookie = fetchCookie;
        } else {
            log.warn("获取cookie为null");
        }
    }


    public static JSONObject extractRoomJson(String pageBody) {
        String sub1 = pageBody.substring(pageBody.lastIndexOf("\\\"roomStore\\\":") + 14);
        int endIndex = sub1.indexOf(",\\\"emojiList\\\":");
        String sub2 = sub1.substring(0, endIndex > 0 ? endIndex : sub1.length());
        String rep = sub2.replace("\\\"", "\"").replace("\\\"", "\"");
        String json = UnicodeUtil.toString(rep) + "}";
        return JSONUtil.parseObj(json);
    }

    private static String msToken() {
        return RandomUtil.randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", 182);
    }

    public static void main(String[] args) {
//        String roomID = "622216334529";
//        String roomID = "208823316033";
        String domain = "https://live.douyin.com/";
//        String domain = "https://www.douyin.com/";
//        String url = "https://www.douyin.com/note/7694202898867991537";
        String url = "https://live.douyin.com/381351302222";
        DouYinAPITools utils = new DouYinAPITools(domain);
        System.out.println(utils.cookie);
//        System.out.println(utils.headers);
        String x = utils.fetchBody(url);
        System.out.println(x.length());
        System.out.println(x);
        System.out.println(DouYinAPITools.extractRoomJson(x).toStringPretty());
//        System.out.println(extractRoomJson(x).toStringPretty());
//        utils.refresh();
//        System.out.println(utils.getCookie());

    }
}
