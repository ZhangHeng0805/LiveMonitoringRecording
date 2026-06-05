package cn.zhangheng.common.util;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/04 星期四 15:19
 * @version: 1.0
 * @description:
 */

import lombok.Data;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpURLConnection 封装工具类
 * 支持 GET/POST（表单/JSON）、获取响应头、响应体、响应码
 */
public class HttpUtils {

    // 超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 60_000;
    private static final Charset charset = StandardCharsets.UTF_8;

    // ==================== GET 请求 ====================
    public static HttpResponse get(String url) throws Exception {
        return get(url, null);
    }

    public static HttpResponse get(String url, Map<String, String> headers) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        // 设置请求头
        setHeaders(conn, headers);

        return getHttpResponse(conn);
    }

    // ==================== POST 表单请求 ====================
    public static HttpResponse postForm(String url, Map<String, String> params) throws Exception {
        return postForm(url, params, null);
    }

    public static HttpResponse postForm(String url, Map<String, String> params, Map<String, String> headers) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setDoOutput(true); // 允许输出流

        // 默认表单头
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        setHeaders(conn, headers);

        // 拼接参数
        String paramStr = buildParams(params);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(paramStr.getBytes());
            os.flush();
        }

        return getHttpResponse(conn);
    }

    // ==================== POST JSON 请求 ====================
    public static HttpResponse postJson(String url, String json) throws Exception {
        return postJson(url, json, null);
    }

    public static HttpResponse postJson(String url, String json, Map<String, String> headers) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setDoOutput(true);

        // 默认JSON头
        conn.setRequestProperty("Content-Type", "application/json; charset=" + charset.name());
        setHeaders(conn, headers);

        try (OutputStream os = conn.getOutputStream()) {

            os.write(json.getBytes(charset));
            os.flush();
        }

        return getHttpResponse(conn);
    }

    // ==================== 工具方法 ====================

    /**
     * 设置请求头
     */
    private static void setHeaders(HttpURLConnection conn, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 拼接表单参数
     */
    private static String buildParams(Map<String, String> params) throws Exception {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(URLEncoder.encode(entry.getKey(), charset.name()))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), charset.name()))
                    .append("&");
        }
        return sb.substring(0, sb.length() - 1);
    }

    /**
     * 获取响应（状态码 + 响应头 + 响应体）
     */
    private static HttpResponse getHttpResponse(HttpURLConnection conn) throws Exception {
        HttpResponse resp = new HttpResponse();
        resp.setCode(conn.getResponseCode());
        resp.setHeaders(conn.getHeaderFields());

        // 读取响应体
        try {
            resp.setBody(RequestUtils.responseAsString(conn));
        } finally {
            conn.disconnect();
        }

        return resp;
    }


    // ==================== 响应结果实体 ====================
    @Data
    public static class HttpResponse {
        private int code;                // 响应码
        private Map<String, List<String>> headers; // 响应头
        private String body;             // 响应体

        // 获取单个响应头
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            return values == null ? null : values.get(0);
        }

        public String getCookie() {
            return RequestUtils.parseCookie(headers);
        }

    }
}
