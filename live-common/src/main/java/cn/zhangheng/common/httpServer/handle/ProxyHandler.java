package cn.zhangheng.common.httpServer.handle;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * 代理请求
 * 注意：不适合分片长链接的代理请求
 */
public class ProxyHandler extends MyHandler {
    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

    // 超时设置（视频传输适配）
    private static final int CONNECT_TIMEOUT = 10000;  // 10秒连接超时
    private static final int READ_TIMEOUT = 60000;     // 60秒读取超时（大视频适配）
    private static final List<String> ALLOWED_PROTOCOLS = Arrays.asList("http", "https");
    private static final int BUFFER_SIZE = 8192;       // 8KB缓冲提升视频传输效率


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Map<String, String> params = parseQuery(httpExchange);
        InputStream targetStream = null;
        OutputStream clientOs = null;
        HttpURLConnection targetConn = null;

        try {
            String url = params.get("url");
            if (url == null || url.trim().isEmpty()) {
                sendErrorResponse(httpExchange, 400, "Missing 'url' parameter");
                return;
            }

            // URL合法性校验
            URL targetUrl;
            try {
                targetUrl = new URL(url);
            } catch (MalformedURLException e) {
                log.warn("Invalid URL: {}", url, e);
                sendErrorResponse(httpExchange, 400, "Invalid URL format: " + e.getMessage());
                return;
            }

            // 协议白名单校验
            if (!ALLOWED_PROTOCOLS.contains(targetUrl.getProtocol())) {
                log.warn("Forbidden protocol: {}", targetUrl.getProtocol());
                sendErrorResponse(httpExchange, 403, "Protocol not allowed: " + targetUrl.getProtocol());
                return;
            }

            // 建立目标服务器连接
            targetConn = (HttpURLConnection) targetUrl.openConnection();
            configureConnection(targetConn, httpExchange.getRequestMethod(), targetUrl);

            // 解析客户端的 Range 头（如 Range: bytes=0-1023）
            List<String> rangeHeaders = httpExchange.getRequestHeaders().get("Range");
            if (rangeHeaders != null && !rangeHeaders.isEmpty()) {
                // 将 Range 头传递给目标服务器
                targetConn.setRequestProperty("Range", rangeHeaders.get(0));
            }

            // 复制请求头（支持Range头，修复Host头）
            copyRequestHeaders(httpExchange, targetConn, targetUrl);

            // 处理自定义请求头参数（header_前缀）
            handleCustomHeaders(params, targetConn);

            // 处理带请求体的方法
            handleRequestBody(httpExchange, targetConn);

            // 处理目标服务器响应
            int responseCode = targetConn.getResponseCode();
            log.debug("Target response code: {} for URL: {}", responseCode, targetConn.getURL());

            // 获取响应流（需手动管理流关闭）
            targetStream = getTargetInputStream(targetConn, responseCode);

            // 处理响应长度（支持分块传输和范围请求）
            long contentLength = targetConn.getContentLengthLong();
            if (responseCode == 206 || contentLength == -1) {
                contentLength = -1;  // 分块传输或范围响应不指定长度
            }
            // 复制响应头
            copyResponseHeaders(targetConn, httpExchange);

            // 发送响应状态
            httpExchange.sendResponseHeaders(responseCode, contentLength);


            // 传输视频流（需手动关闭流）
            clientOs = httpExchange.getResponseBody();
            copyStreamWithClientCheck(targetStream, clientOs,contentLength);

        } catch (SocketTimeoutException e) {
            log.error("Connection timed out for URL: {}", params.get("url"), e);
            sendErrorResponse(httpExchange, 504, "Connection timed out: " + e.getMessage());
        } catch (IOException e) {
            // 判断是否为客户端断开连接导致的流关闭异常
            if (isStreamClosedException(e)) {
                // 仅在调试环境输出详细日志，生产环境简化
                if (log.isDebugEnabled()) {
                    log.debug("Client closed connection (expected in video streaming)", e);
                } else {
                    log.info("Client closed connection during streaming");
                }
            } else {
                log.error("Gateway error for URL: {}", params.get("url"), e);
                sendErrorResponse(httpExchange, 502, "Bad gateway: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error in proxy handler", e);
            sendErrorResponse(httpExchange, 500, "Internal server error");
        } finally {
            // 优雅关闭资源
            closeQuietly(targetStream);
            closeQuietly(clientOs);
            if (targetConn != null) {
                targetConn.disconnect();
            }
            closeQuietly(httpExchange);
        }
    }

    private boolean isStreamClosedException(IOException e) {
        // 方式1：通过异常类名判断（最直接）
        String exceptionClassName = e.getClass().getName();
        if ("sun.net.httpserver.StreamClosedException".equals(exceptionClassName)) {
            return true;
        }

        // 方式2：通过堆栈信息辅助判断（防止类名变化）
        StackTraceElement[] stackTrace = e.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().equals("sun.net.httpserver.FixedLengthOutputStream") &&
                    element.getMethodName().equals("write")) {
                // 若异常发生在FixedLengthOutputStream的write方法，大概率是流已关闭
                return true;
            }
        }

        // 方式3：通过异常消息判断（兜底）
        String message = e.getMessage();
        return message != null && (message.contains("Stream closed") ||
                message.contains("closed") ||
                message.contains("Connection reset by peer"));
    }

    /**
     * 配置连接参数（JDK8兼容）
     */
    private void configureConnection(HttpURLConnection conn, String method, URL targetUrl) {
        conn.setUseCaches(false);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(false);
        try {
            conn.setRequestMethod(method);  // setRequestMethod可能抛异常
        } catch (ProtocolException e) {
            log.error("Invalid HTTP method: {}", method, e);
        }
        conn.setDoInput(true);
        conn.setDoOutput(needsRequestBody(method));
    }

    /**
     * 复制请求头（修复Host头，支持Range头）
     */
    private void copyRequestHeaders(HttpExchange source, HttpURLConnection target, URL targetUrl) {
        Map<String, List<String>> sourceHeaders = source.getRequestHeaders();
        String targetHost = targetUrl.getHost() + (targetUrl.getPort() != -1 ? ":" + targetUrl.getPort() : "");

        for (Map.Entry<String, List<String>> entry : sourceHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;

            // 跳过不需要传递的头
            if ("referer".equalsIgnoreCase(key)) {
                continue;
            }
            // 重写Host头为目标服务器Host
            if ("Host".equalsIgnoreCase(key)) {
                target.setRequestProperty(key, targetHost);
                continue;
            }
            // 传递其他头（包括Range头，支持断点续传）
            for (String value : entry.getValue()) {
                target.addRequestProperty(key, value);
            }
        }
    }

    /**
     * 处理自定义请求头（移除第三方工具依赖，兼容JDK8）
     */
    private void handleCustomHeaders(Map<String, String> params, HttpURLConnection target) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("header_")) {
                // JDK8使用String.substring处理前缀
                String headerName = key.substring("header_".length());
                target.setRequestProperty(headerName, entry.getValue());
            }
        }
    }

    /**
     * 处理请求体（JDK8手动关闭流）
     */
    private void handleRequestBody(HttpExchange source, HttpURLConnection target) throws IOException {
        if (!target.getDoOutput()) {
            return; // 不需要请求体的方法（如GET）直接返回
        }

        InputStream clientIn = null;
        OutputStream targetOut = null;
        try {
            // 获取客户端请求体输入流
            clientIn = source.getRequestBody();
            // 获取目标服务器输出流（用于发送请求体）
            targetOut = target.getOutputStream();

            // 获取客户端请求体长度（从Content-Length头获取）
            long contentLength = source.getRequestHeaders().getFirst("Content-Length") != null
                    ? Long.parseLong(source.getRequestHeaders().getFirst("Content-Length"))
                    : -1;

            // 调用带长度检测的流复制方法（第三个参数为客户端请求体长度）
            copyStreamWithClientCheck(clientIn, targetOut, contentLength);

        } catch (IOException e) {
            // 区分是客户端断开还是目标服务器异常
            if (isStreamClosedException(e)) {
                log.warn("Client closed connection while sending request body", e);
            } else {
                log.error("Failed to send request body to target server", e);
            }
            throw e; // 重新抛出异常，让上层handle方法统一处理
        } finally {
            // 静默关闭输入流（客户端请求体）
            closeQuietly(clientIn);
            // 处理目标服务器输出流（确保刷新并关闭）
            if (targetOut != null) {
                try {
                    targetOut.flush(); // 最后刷新一次，确保请求体发送完成
                } catch (IOException e) {
                    log.warn("Failed to flush request body to target server", e);
                } finally {
                    closeQuietly(targetOut);
                }
            }
        }
    }

    /**
     * 获取目标输入流（区分正常/错误流）
     */
    private InputStream getTargetInputStream(HttpURLConnection conn, int responseCode) throws IOException {
        if (responseCode >= 200 && responseCode < 300) {
            return conn.getInputStream();
        } else {
            InputStream errorStream = conn.getErrorStream();
            return errorStream != null ? errorStream : new ByteArrayInputStream(new byte[0]);
        }
    }

    /**
     * 复制响应头到客户端
     */
    private void copyResponseHeaders(HttpURLConnection source, HttpExchange target) {
        Map<String, List<String>> sourceHeaders = source.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : sourceHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;

            // 保留 Accept-ranges 头，告知客户端支持范围请求
            if ("Accept-ranges".equalsIgnoreCase(key)) {
                target.getResponseHeaders().add(key, String.join(",",entry.getValue()));
                continue;
            }

            // 过滤可能导致客户端异常的头
//            if ("Connection".equalsIgnoreCase(key) && "close".equalsIgnoreCase(entry.getValue().get(0))) {
//                continue;
//            }
            for (String value : entry.getValue()) {
                target.getResponseHeaders().add(key, value);
            }
        }
    }


    /**
     * 流复制（适配视频传输，JDK8兼容）
     */
    private void copyStreamWithClientCheck(InputStream is, OutputStream os, long expectedLength) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesWritten = 0;
        final long FLUSH_THRESHOLD = 64 * 1024;

        while ((bytesRead = is.read(buffer)) != -1) {
            try {
                os.write(buffer, 0, bytesRead);
                totalBytesWritten += bytesRead;

                if (totalBytesWritten >= FLUSH_THRESHOLD) {
                    os.flush();
                    totalBytesWritten = 0;
                }
            } catch (IOException e) {
                if (isStreamClosedException(e)) {
                    // 若已传输超过 90%，视为“接近完成”，减少告警级别
                    if (expectedLength > 0 && totalBytesWritten > expectedLength * 0.9) {
                        log.debug("Client closed connection (almost complete, written: {} of {})",
                                totalBytesWritten, expectedLength);
                    } else {
                        log.warn("Client closed connection (written: {} of {})",
                                totalBytesWritten, expectedLength);
                    }
                    throw e;
                }
                log.error("Failed to write to stream", e);
                throw e;
            }
        }

        // 最后一次刷新
        try {
            os.flush();
        } catch (IOException e) {
            if (!isStreamClosedException(e)) {
                log.warn("Failed to flush final data", e);
            }
        }
    }

    /**
     * 判断是否需要请求体
     */
    private boolean needsRequestBody(String method) {
        return "POST".equalsIgnoreCase(method) ||
                "PUT".equalsIgnoreCase(method) ||
                "PATCH".equalsIgnoreCase(method);
    }

    /**
     * 静默关闭资源
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.warn("Error closing resource", e);
            }
        }
    }

    /**
     * 静默关闭HttpExchange
     */
    private void closeQuietly(HttpExchange exchange) {
        if (exchange != null) {
            exchange.close();
        }
    }
}

