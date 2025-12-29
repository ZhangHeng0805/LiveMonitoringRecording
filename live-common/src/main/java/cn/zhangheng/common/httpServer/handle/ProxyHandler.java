package cn.zhangheng.common.httpServer.handle;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支持长连接分片文件的代理请求处理器（JDK1.8兼容，修复302重定向+字节数不匹配异常）
 */
public class ProxyHandler extends MyHandler {
    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

    // 超时设置
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 300000;
    private static final List<String> ALLOWED_PROTOCOLS = Arrays.asList("http", "https");
    private static final int BUFFER_SIZE = 8192;
    // 重定向响应码集合（HTTP规范：无响应体或响应体可忽略）
    private static final List<Integer> REDIRECT_CODES = Arrays.asList(301, 302, 303, 307, 308);

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Map<String, String> params = parseQuery(httpExchange);
        InputStream targetStream = null;
        OutputStream clientOs = null;
        HttpURLConnection targetConn = null;
        boolean isConnectionClosed = false;
        // 标记：是否已发送响应头（避免重复发送）
        boolean isResponseSent = false;

        try {
            String url = params.get("url");
            if (url == null || url.trim().isEmpty()) {
                sendErrorResponse(httpExchange, 400, "Missing 'url' parameter");
                isResponseSent = true;
                return;
            }

            URL targetUrl;
            try {
                targetUrl = new URL(url);
            } catch (MalformedURLException e) {
                log.warn("Invalid URL: {}", url, e);
                sendErrorResponse(httpExchange, 400, "Invalid URL format: " + e.getMessage());
                isResponseSent = true;
                return;
            }

            if (!ALLOWED_PROTOCOLS.contains(targetUrl.getProtocol())) {
                log.warn("Forbidden protocol: {}", targetUrl.getProtocol());
                sendErrorResponse(httpExchange, 403, "Protocol not allowed: " + targetUrl.getProtocol());
                isResponseSent = true;
                return;
            }

            targetConn = (HttpURLConnection) targetUrl.openConnection();
            configureConnection(targetConn, httpExchange.getRequestMethod(), targetUrl);

            // 传递Range头
            List<String> rangeHeaders = httpExchange.getRequestHeaders().get("Range");
            if (rangeHeaders != null && !rangeHeaders.isEmpty()) {
                String rangeValue = rangeHeaders.get(0);
                targetConn.setRequestProperty("Range", rangeValue);
                log.debug("Forward Range header to target: {}", rangeValue);
            }

            copyRequestHeaders(httpExchange, targetConn, targetUrl);
            handleCustomHeaders(params, targetConn);

            // 处理请求体（若连接已关闭则终止）
            if (!isConnectionClosed) {
                handleRequestBody(httpExchange, targetConn, isConnectionClosed);
            }
            if (isConnectionClosed) {
                log.debug("Connection closed before stream copy, exit");
                return;
            }

            // 获取目标响应信息
            int responseCode = targetConn.getResponseCode();
            log.debug("Target response code: {} for URL: {}", responseCode, targetConn.getURL());

            // 关键修复1：对重定向/无响应体响应码特殊处理，强制设置contentLength为-1
            long contentLength = getAdaptedContentLength(targetConn, responseCode);

            // 复制响应头
            copyResponseHeaders(targetConn, httpExchange);

            // 发送响应状态（标记已发送）
            httpExchange.sendResponseHeaders(responseCode, contentLength);
            isResponseSent = true;

            // 关键修复2：重定向响应码无需写入响应体，直接跳过流传输
            if (REDIRECT_CODES.contains(responseCode)) {
                log.debug("Redirect response ({}), skip stream transfer", responseCode);
                return;
            }

            // 获取响应流
            targetStream = getTargetInputStream(targetConn, responseCode);

            // 传输流数据（仅非重定向响应执行）
            clientOs = httpExchange.getResponseBody();
            copyStreamWithLongConnectionSupport(targetStream, clientOs, contentLength, isConnectionClosed);

        } catch (SocketTimeoutException e) {
            log.error("Connection timed out for URL: {}", params.get("url"), e);
            if (!isConnectionClosed && !isResponseSent) {
                sendErrorResponse(httpExchange, 504, "Connection timed out: " + e.getMessage());
                isResponseSent = true;
            }
        } catch (IOException e) {
            if (isStreamClosedException(e)) {
                isConnectionClosed = true;
                if (log.isDebugEnabled()) {
                    log.debug("Client closed connection (normal in long connection streaming)", e);
                } else {
                    log.info("Client closed connection during long connection streaming");
                }
            } else {
                if (!isConnectionClosed && !isResponseSent) {
                    log.error("Gateway error for URL: {}", params.get("url"), e);
                    sendErrorResponse(httpExchange, 502, "Bad gateway: " + e.getMessage());
                    isResponseSent = true;
                }
            }
        } catch (Exception e) {
            if (!isConnectionClosed && !isResponseSent) {
                log.error("Unexpected error in proxy handler", e);
                sendErrorResponse(httpExchange, 500, "Internal server error");
                isResponseSent = true;
            }
        } finally {
            // 关键修复3：资源关闭优化
            // 1. 关闭目标流（无字节数限制，直接关闭）
            closeQuietly(targetStream);
            // 2. 客户端输出流：仅非重定向、非连接关闭场景，且已获取流时，才尝试关闭（避免触发异常）
            if (clientOs != null && !isConnectionClosed && !REDIRECT_CODES.contains(getResponseCodeSafely(targetConn))) {
                try {
                    // 重定向响应已跳过写入，无需flush；非重定向响应已完成写入，flush后关闭
                    if (!isConnectionClosed) {
                        clientOs.flush();
                    }
                } catch (IOException e) {
                    if (!isStreamClosedException(e)) {
                        log.warn("Failed to flush client output stream", e);
                    }
                } finally {
                    closeQuietly(clientOs);
                }
            }
            // 3. 断开目标连接
            if (targetConn != null) {
                targetConn.disconnect();
            }
            // 4. 关闭HttpExchange（最后执行）
            closeQuietly(httpExchange);
        }
    }

    /**
     * 关键修复：适配分片/分块/重定向的响应长度计算（强制重定向响应长度为-1）
     */
    private long getAdaptedContentLength(HttpURLConnection conn, int responseCode) {
        // 重定向响应码：强制返回-1，避免传递无效固定长度
        if (REDIRECT_CODES.contains(responseCode)) {
            return -1;
        }
        long contentLength = conn.getContentLengthLong();
        // 分片响应/分块传输：返回-1
        if (responseCode == 206 || contentLength == -1 || "chunked".equalsIgnoreCase(conn.getHeaderField("Transfer-Encoding"))) {
            return -1;
        }
        // 正常响应：返回实际长度
        return contentLength;
    }

    /**
     * 安全获取响应码（避免空指针）
     */
    private int getResponseCodeSafely(HttpURLConnection conn) {
        if (conn == null) {
            return -1;
        }
        try {
            return conn.getResponseCode();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * 流复制（JDK1.8兼容，无isClosed()依赖）
     */
    private void copyStreamWithLongConnectionSupport(InputStream is, OutputStream os, long expectedLength, boolean isConnectionClosed) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesWritten = 0;
        final long FLUSH_THRESHOLD = 64 * 1024;

        while (!isConnectionClosed && (bytesRead = is.read(buffer)) != -1) {
            try {
                os.write(buffer, 0, bytesRead);
                totalBytesWritten += bytesRead;

                if (totalBytesWritten >= FLUSH_THRESHOLD) {
                    os.flush();
                    totalBytesWritten = 0;
                }
            } catch (IOException e) {
                if (isStreamClosedException(e)) {
                    isConnectionClosed = true;
                    String logMsg = expectedLength > 0
                            ? String.format("Client closed connection (written: %d of %d)", totalBytesWritten, expectedLength)
                            : String.format("Client closed connection (written: %d bytes, chunked transfer)", totalBytesWritten);
                    log.debug(logMsg);
                    throw e;
                }
                log.error("Failed to write stream data", e);
                throw e;
            }
        }

        // 仅非连接关闭场景，执行最终flush
        if (!isConnectionClosed) {
            try {
                os.flush();
            } catch (IOException e) {
                if (!isStreamClosedException(e)) {
                    log.warn("Failed to flush final stream data", e);
                }
            }
        }
    }

    /**
     * 判断是否为流关闭异常（JDK1.8精准匹配）
     */
    private boolean isStreamClosedException(IOException e) {
        if ("sun.net.httpserver.StreamClosedException".equals(e.getClass().getName())) {
            return true;
        }

        StackTraceElement[] stackTrace = e.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().equals("sun.net.httpserver.FixedLengthOutputStream")
                    && element.getMethodName().equals("write")) {
                return true;
            }
        }

        String message = e.getMessage();
        return message != null && (message.contains("Stream closed")
                || message.contains("Connection reset by peer")
                || message.contains("Broken pipe"));
    }

    /**
     * 配置连接参数（支持长连接）
     */
    private void configureConnection(HttpURLConnection conn, String method, URL targetUrl) {
        conn.setUseCaches(false);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Connection", "Keep-Alive");
        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            log.error("Invalid HTTP method: {}", method, e);
        }
        conn.setDoInput(true);
        conn.setDoOutput(needsRequestBody(method));
    }

    /**
     * 复制请求头
     */
    private void copyRequestHeaders(HttpExchange source, HttpURLConnection target, URL targetUrl) {
        Map<String, List<String>> sourceHeaders = source.getRequestHeaders();
        String targetHost = targetUrl.getHost() + (targetUrl.getPort() != -1 ? ":" + targetUrl.getPort() : "");

        for (Map.Entry<String, List<String>> entry : sourceHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;

            if ("Referer".equalsIgnoreCase(key) || "User-Agent".equalsIgnoreCase(key)
                    || "Connection".equalsIgnoreCase(key)) {
                continue;
            }

            if ("Host".equalsIgnoreCase(key)) {
                target.setRequestProperty(key, targetHost);
                continue;
            }

            for (String value : entry.getValue()) {
                target.addRequestProperty(key, value);
            }
        }
    }

    /**
     * 处理自定义请求头
     */
    private void handleCustomHeaders(Map<String, String> params, HttpURLConnection target) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("header_")) {
                String headerName = key.substring("header_".length());
                target.setRequestProperty(headerName, entry.getValue());
                log.debug("Add custom header: {} = {}", headerName, entry.getValue());
            }
        }
    }

    /**
     * 处理请求体
     */
    private void handleRequestBody(HttpExchange source, HttpURLConnection target, boolean isConnectionClosed) throws IOException {
        if (!target.getDoOutput()) {
            return;
        }

        InputStream clientIn = null;
        OutputStream targetOut = null;
        try {
            clientIn = source.getRequestBody();
            targetOut = target.getOutputStream();

            long contentLength = -1;
            String contentLengthStr = source.getRequestHeaders().getFirst("Content-Length");
            if (contentLengthStr != null) {
                contentLength = Long.parseLong(contentLengthStr);
            }

            copyStreamWithLongConnectionSupport(clientIn, targetOut, contentLength, isConnectionClosed);

        } catch (IOException e) {
            if (isStreamClosedException(e)) {
                isConnectionClosed = true;
                log.warn("Client closed connection while sending request body", e);
            } else {
                log.error("Failed to send request body to target server", e);
            }
            throw e;
        } finally {
            closeQuietly(clientIn);
            if (targetOut != null) {
                try {
                    targetOut.flush();
                } catch (IOException e) {
                    log.warn("Failed to flush request body", e);
                } finally {
                    closeQuietly(targetOut);
                }
            }
        }
    }

    /**
     * 获取目标输入流
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
     * 复制响应头
     */
    private void copyResponseHeaders(HttpURLConnection source, HttpExchange target) {
        Map<String, List<String>> sourceHeaders = source.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : sourceHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;

            if ("Connection".equalsIgnoreCase(key) && "close".equalsIgnoreCase(entry.getValue().get(0))
                    || "Transfer-Encoding".equalsIgnoreCase(key) && "chunked".equalsIgnoreCase(entry.getValue().get(0))
                    || "Server".equalsIgnoreCase(key) || "Date".equalsIgnoreCase(key)) {
                continue;
            }

            if ("Accept-Ranges".equalsIgnoreCase(key) || "Content-Range".equalsIgnoreCase(key)) {
                target.getResponseHeaders().put(key, entry.getValue());
                continue;
            }

            for (String value : entry.getValue()) {
                target.getResponseHeaders().add(key, value);
            }
        }
    }

    /**
     * 判断是否需要请求体
     */
    private boolean needsRequestBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    /**
     * 静默关闭Closeable资源
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // 忽略流关闭异常（尤其是已触发字节数不匹配时）
                log.debug("Error closing resource (ignorable)", e);
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