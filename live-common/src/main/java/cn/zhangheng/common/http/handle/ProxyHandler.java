package cn.zhangheng.common.http.handle;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/28 星期日 09:46
 * @version: 1.0
 * @description:
 */
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProxyHandler extends MyHandler {

    // 超时设置（毫秒）
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    // 允许的协议
    private static final List<String> ALLOWED_PROTOCOLS = Arrays.asList("http", "https");

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        // 最终确保响应被关闭
        try  {
            String url = parseQuery(httpExchange).get("url");
            if (url == null || url.trim().isEmpty()) {
                sendErrorResponse(httpExchange, 400, "Missing 'url' parameter");
                return;
            }

            // 验证URL合法性
            URL targetUrl;
            try {
                targetUrl = new URL(url);
            } catch (MalformedURLException e) {
                sendErrorResponse(httpExchange, 400, "Invalid URL format: " + e.getMessage());
                return;
            }

            // 安全校验：只允许指定协议
            if (!ALLOWED_PROTOCOLS.contains(targetUrl.getProtocol())) {
                sendErrorResponse(httpExchange, 403, "Protocol not allowed: " + targetUrl.getProtocol());
                return;
            }

            HttpURLConnection connection = null;
            try {
                // 建立目标服务器连接
                connection = (HttpURLConnection) targetUrl.openConnection();

                // 配置连接参数
                connection.setUseCaches(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setInstanceFollowRedirects(false); // 让客户端处理重定向

                // 传递原始请求方法
                String method = httpExchange.getRequestMethod();
                connection.setRequestMethod(method);

                // 传递请求头
                copyRequestHeaders(httpExchange, connection);

                // 处理POST等有请求体的方法
                if (needsRequestBody(method)) {
                    connection.setDoOutput(true);
                    copyRequestBody(httpExchange, connection);
                }

                // 获取目标服务器响应状态
                int responseCode = connection.getResponseCode();
                // 获取正确的输入流（正常响应或错误响应）
                InputStream inputStream = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                // 发送响应状态码
                long contentLength = connection.getContentLengthLong();
                httpExchange.sendResponseHeaders(responseCode, contentLength);

                // 传递响应头
                copyResponseHeaders(connection, httpExchange);

                // 传输响应体
                copyStream(inputStream, httpExchange.getResponseBody());

            } catch (SocketTimeoutException e) {
                sendErrorResponse(httpExchange, 504, "Connection timed out: " + e.getMessage());
            } catch (IOException e) {
                sendErrorResponse(httpExchange, 502, "Bad gateway: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect(); // 确保连接关闭
                }
            }
        }finally {
            httpExchange.close();
        }
    }

    /**
     * 复制请求头到目标连接
     */
    private void copyRequestHeaders(HttpExchange source, HttpURLConnection target) {
        Map<String, List<String>> headers = source.getRequestHeaders();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            // 跳过不需要或不应该传递的头
            if ("Host".equalsIgnoreCase(key) ||
                    "Connection".equalsIgnoreCase(key) ||
                    "referer".equalsIgnoreCase(key) ||
                    "Content-Length".equalsIgnoreCase(key)) {
                continue;
            }
            for (String value : entry.getValue()) {
                target.addRequestProperty(key, value);
            }
        }
    }

    /**
     * 复制响应头到客户端
     */
    private void copyResponseHeaders(HttpURLConnection source, HttpExchange target) {
        Map<String, List<String>> headers = source.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            // 跳过null键（可能是状态行）和不需要的头
            if (key == null || "Connection".equalsIgnoreCase(key)) {
                continue;
            }
            for (String value : entry.getValue()) {
                target.getResponseHeaders().add(key, value);
            }
        }
    }

    /**
     * 复制请求体到目标连接
     */
    private void copyRequestBody(HttpExchange source, HttpURLConnection target) throws IOException {
        try (InputStream is = source.getRequestBody();
             OutputStream os = target.getOutputStream()) {
            copyStream(is, os);
        }
    }

    /**
     * 流数据复制工具方法
     */
    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        // 只写入实际读取的字节数，避免最后一次读取的冗余数据
        while ((bytesRead = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.flush();
    }

    /**
     * 判断HTTP方法是否需要请求体
     */
    private boolean needsRequestBody(String method) {
        return "POST".equalsIgnoreCase(method) ||
                "PUT".equalsIgnoreCase(method) ||
                "PATCH".equalsIgnoreCase(method);
    }

}

