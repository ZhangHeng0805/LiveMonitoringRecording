package cn.zhangheng.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    private final static int connectTimeout=15_000;
    private final static int readTimeout=30_000;

    public static HttpURLConnection getRequest(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
//        connection.setDoOutput(true);
        if (headers != null&& !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }
    public static String responseAsString(HttpURLConnection connection, Charset charset) throws IOException {
        String encoding = connection.getContentEncoding();
        try (InputStream inputStream = connection.getResponseCode() == HttpURLConnection.HTTP_OK ? connection.getInputStream() : connection.getErrorStream();
             InputStream in = "gzip".equalsIgnoreCase(encoding) ? new GZIPInputStream(inputStream) : inputStream;
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in, charset))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    public static String responseAsString(HttpURLConnection connection) throws IOException {
        return responseAsString(connection, StandardCharsets.UTF_8);
    }
}
