package cn.zhangheng.record.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
        String encoding = connection.getContentEncoding();
        try (InputStream inputStream = connection.getResponseCode() == HttpURLConnection.HTTP_OK ? connection.getInputStream() : connection.getErrorStream();
             InputStream in = "gzip".equalsIgnoreCase(encoding) ? new GZIPInputStream(inputStream) : inputStream) {

            ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toString(charset.name());
        }
    }

    public static String responseAsString(HttpURLConnection connection) throws IOException {
        return responseAsString(connection, StandardCharsets.UTF_8);
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
}
