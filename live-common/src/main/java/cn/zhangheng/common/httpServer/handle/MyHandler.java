package cn.zhangheng.common.httpServer.handle;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 04:06
 * @version: 1.0
 * @description:
 */
public abstract class MyHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);
    protected final Charset charset = StandardCharsets.UTF_8;

    protected String getIndexPath(HttpExchange httpExchange,String prefix) {
        URI requestURI = httpExchange.getRequestURI();
        String path = requestURI.getPath();
        return StrUtil.subAfter(path, prefix, true);
    }

    protected Map<String, String> parseQuery(HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getQuery());
    }

    // 解析URL查询参数
    protected Map<String, String> parseQuery(String query) {
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

    // 发送错误响应
    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        // 检查响应是否已发送
        if (exchange.getResponseCode() != -1) {
            log.warn("响应已发送，无法再发送错误信息: {}", message);
            return;
        }
        String response = "<html><head><title>" + Constant.Application + "</title></head><body><h1>" + statusCode + " - " + message + "</h1></body></html>";
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=" + charset.name());
        byte[] bytes = response.getBytes(charset);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
