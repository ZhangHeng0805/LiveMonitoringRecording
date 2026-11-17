package cn.zhangheng.record.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.*;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/12 星期五 04:43
 * @version: 1.0
 * @description:
 */

public class RequestUtils {
    private final static int connectTimeout = 15_000;
    private final static int readTimeout = 60_000;
    private final static Charset charset= StandardCharsets.UTF_8;

    public static HttpURLConnection getRequest(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("Connection", "keep-alive");
//        connection.setDoOutput(true);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }

    public static String responseAsString(HttpURLConnection connection, Charset charset) throws IOException {
        String contentEncoding = connection.getContentEncoding();
        try (InputStream rawIn = connection.getResponseCode() == HttpURLConnection.HTTP_OK
                ? connection.getInputStream()
                : connection.getErrorStream()) {
            InputStream in = "gzip".equalsIgnoreCase(contentEncoding) ? new GZIPInputStream(rawIn) : rawIn;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
                StringBuilder sb = new StringBuilder(8192);
                char[] cbuf = new char[4096];
                int len;
                while ((len = reader.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, len);
                }
                return sb.toString();
            }
        }
    }


    public static String responseAsString(HttpURLConnection connection) throws IOException {
        return responseAsString(connection, charset);
    }

    public static String parseCookie(HttpURLConnection connection) {
        return parseCookie(connection.getHeaderFields());
    }

    /**
     * 从响应头中提取所有Cookie，拼接成请求头可用的 Cookie 字符串
     *
     * @param headerFields connection.getHeaderFields()
     * @return name=value; name2=value2; ...
     */
    public static String parseCookie(Map<String, List<String>> headerFields) {
        List<String> setCookies = headerFields.get("Set-Cookie");
        if (setCookies == null || setCookies.isEmpty()) {
            return null;
        }

        StringBuilder cookieSb = new StringBuilder();
        for (String setCookie : setCookies) {
            // 截取 ; 前面的部分（只保留 key=value）
            int semicolonIndex = setCookie.indexOf(";");
            if (semicolonIndex > 0) {
                String kv = setCookie.substring(0, semicolonIndex);
                cookieSb.append(kv).append("; ");
            }
        }

        // 去掉最后多余的 ;
        if (cookieSb.length() > 0) {
            cookieSb.setLength(cookieSb.length() - 2);
        }
        return cookieSb.toString();
    }
    /**
     * 设置请求头
     */
    public static void setHeaders(HttpURLConnection conn, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 拼接表单参数
     */
    public static String buildParams(Map<String, String> params) throws Exception {
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
}
