package cn.zhangheng.common.httpServer.util;

import cn.hutool.core.util.StrUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.util.CusAccessObjectUtil;
import com.zhangheng.util.FormatUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:35
 * @version: 1.0
 * @description:
 */
@Slf4j
public class HandlerUtils {
    public static String getSessionID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getRequestCookie(HttpExchange exchange, String key, String defaultValue) {
        return getRequestCookies(exchange).getOrDefault(key, defaultValue);
    }

    public static void setResponseCookie(HttpExchange httpExchange, String key, String value, long expireSec) {
        String origin = httpExchange.getRequestHeaders().getFirst("Origin");
        boolean isHttps = origin != null && origin.startsWith("https");
        StringBuilder cookieSb = new StringBuilder(key).append("=").append(value);
        cookieSb.append("; HttpOnly")// 禁止JS访问，防XSS
                .append("; Path=/")// 全路径生效
        ;
        // HTTPS环境：开启跨域Cookie
        if (isHttps) {
            cookieSb.append("; SameSite=None; Secure");
        } else {
            // 本地HTTP服务：用Lax，去掉Secure，本地才能读写Cookie
            cookieSb.append("; SameSite=Lax");
        }
        if (expireSec > 0) {
            cookieSb.append("; Max-Age=").append(expireSec);// 过期时间（单位秒）
        }
        httpExchange.getResponseHeaders().add("Set-Cookie", cookieSb.toString());
    }

    public static Map<String, String> getRequestCookies(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        List<String> cookieList = exchange.getRequestHeaders().get("Cookie");
        // 空值判断
        if (cookieList == null || cookieList.isEmpty()) {
            return map;
        }
        for (String cookieStr : cookieList) {
            if (cookieStr == null || cookieStr.isEmpty()) {
                continue;
            }
            // 按 ; 分割多个 cookie
            String[] cookies = cookieStr.split(";");
            for (String cookie : cookies) {
                // 去除前后空格
                String trimCookie = cookie.trim();
                if (trimCookie.isEmpty()) continue;

                // 按 = 分割 key 和 value，只分割成两段（防止 value 里有 =）
                String[] keyValue = trimCookie.split("=", 2);
                String key = keyValue[0].trim();

                // value 可能为空
                String value = keyValue.length > 1 ? keyValue[1].trim() : "";

                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    public static String parseRequestBodyStr(HttpExchange exchange, Charset charset) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in, charset))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    public static String getRequestUserAgent(HttpExchange httpExchange) {
        return httpExchange.getRequestHeaders().getFirst("User-Agent");
    }

    public static Map<String, String> parseQuery(HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getQuery(), StandardCharsets.UTF_8);
    }

    // 解析URL查询参数
    public static Map<String, String> parseQuery(String query, Charset charset) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), charset.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), charset.name());
                    result.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    log.warn("Failed to decode query parameter: {}", pair, e);
                }
            }
        }
        return result;
    }

    public static String getClientIP(HttpExchange exchange) {
        String ip = null;
        Headers requestHeaders = exchange.getRequestHeaders();

        // 1. 依次从代理头获取真实IP
        for (String header : CusAccessObjectUtil.HEADERS_TO_TRY) {
            ip = requestHeaders.getFirst(header);
            if (StrUtil.isNotEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // 取逗号分割第一个IP（多级代理场景）
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return trimIpPort(ip);
            }
        }

        // 2. 头部无IP，从连接远端地址读取
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        }

        // 3. 本地IPv6回环统一转为127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }

        return trimIpPort(ip);
    }

    /**
     * 剥离IP后面附带的端口，纯净返回IP地址
     * 兼容格式：192.168.1.1:1234  /  [2409:xxxx]:8005  /  纯IP
     */
    private static String trimIpPort(String ipStr) {
        if (StrUtil.isBlank(ipStr)) {
            return ipStr;
        }
        // IPv6带方括号格式 [ipv6]:port
        if (ipStr.startsWith("[")) {
            int endBracketIndex = ipStr.indexOf(']');
            if (endBracketIndex > 0) {
                return ipStr.substring(1, endBracketIndex);
            }
        }
        // IPv4 / 无括号IPv6 按冒号分割端口
        int colonIndex = ipStr.indexOf(':');
        // IPv4只有一个冒号才是端口分割
        if (colonIndex > 0 && FormatUtil.isIpv4(ipStr)) {
            return ipStr.substring(0, colonIndex);
        }
        // IPv6本身含多个冒号，直接原样返回
        return ipStr;
    }
}
