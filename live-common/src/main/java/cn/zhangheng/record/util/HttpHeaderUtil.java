package cn.zhangheng.record.util;

import cn.zhangheng.record.okhttp.OkHttpUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/17 星期二 20:58
 * @version: 1.0
 * @description:
 */

public class HttpHeaderUtil {
    @Getter
    private final String domain;
    @Getter
    private String cookie, userAgent;
    @Getter
    private final Map<String, String> headers = new HashMap<>();


    private final Pattern CHROME_VER_PATTERN = Pattern.compile("Chrome/(\\d+)");

    public HttpHeaderUtil(String domain) {
        this.domain = domain;
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("cache-control", "max-age=0");
        headers.put("Referer", domain);
    }

    public void setCookie(String cookie) {
        this.cookie = OkHttpUtil.cleanHeaderValue(cookie);
        headers.put("Cookie", this.cookie);
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = OkHttpUtil.cleanHeaderValue(userAgent);
        headers.put("User-Agent", this.userAgent);
    }

    public String removeCookie() {
        return headers.remove("Cookie");
    }

    public void setSecHeader() {
        if (userAgent == null) return;
        Matcher m = CHROME_VER_PATTERN.matcher(userAgent);
        String majorVer = "140";
        if (m.find()) {
            majorVer = m.group(1);
        }
        boolean isEdge = userAgent.contains("Edg/");
        boolean isMac = userAgent.contains("Macintosh");

        String secChUa;
        if (isEdge) {
            secChUa = "\"Chromium\";v=\"" + majorVer + "\", \"Not-A.Brand\";v=\"99\", \"Microsoft Edge\";v=\"" + majorVer + "\"";
        } else {
            secChUa = "\"Chromium\";v=\"" + majorVer + "\", \"Not-A.Brand\";v=\"99\", \"Google Chrome\";v=\"" + majorVer + "\"";
        }
        String platform = isMac ? "\"macOS\"" : "\"Windows\"";
        String secChUaMobile = "?0";
        headers.put("sec-ch-ua", secChUa);
        headers.put("sec-ch-ua-platform", platform);
        headers.put("sec-ch-ua-mobile", secChUaMobile);
    }

    public void removeSecHeader() {
        headers.remove("sec-ch-ua");
        headers.remove("sec-ch-ua-platform");
        headers.remove("sec-ch-ua-mobile");
    }
}
