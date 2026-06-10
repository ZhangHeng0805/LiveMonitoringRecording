package cn.zhangheng.common.httpServer.handle;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zhangheng.util.CusAccessObjectUtil;
import com.zhangheng.util.EncryptUtil;
import com.zhangheng.util.FormatUtil;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 04:06
 * @version: 1.0
 * @description:
 */
public abstract class MyHandler implements HttpHandler {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    protected static final Charset charset = StandardCharsets.UTF_8;

    protected String getIndexPath(HttpExchange httpExchange, String prefix) {
        URI requestURI = httpExchange.getRequestURI();
        String path = requestURI.getPath();
        return StrUtil.subAfter(path, prefix, true);
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            if (filter(httpExchange)) {
                if (httpExchange.getResponseCode() != -1) {
                    return;
                }
                request(httpExchange);
            } else {
                if (httpExchange.getResponseCode() != -1) {
                    return;
                }
                httpExchange.sendResponseHeaders(403, -1);
            }
        } catch (Throwable e) {
            handleThrowable(httpExchange, e);
        } finally {
            httpExchange.close();
        }
    }

    protected boolean filter(HttpExchange httpExchange) throws IOException {
        //TODO 是否过滤请求 true放行 false拦截
        return true;
    }

    protected abstract void request(HttpExchange httpExchange) throws IOException;


    protected String getSessionID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    protected void handleThrowable(HttpExchange httpExchange, Throwable throwable) throws IOException {
        log.error("{}请求[{}]发生异常:{}", getClientIP(httpExchange), httpExchange.getRequestURI().getPath(), ThrowableUtil.getAllCauseMessage(throwable));
        sendErrorResponse(httpExchange, throwable);
    }

    protected Map<String, String> parseQuery(HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getQuery());
    }

    protected String getRequestCookie(HttpExchange exchange, String key) {
        return getRequestCookies(exchange).get(key);
    }

    protected void setResponseCookie(HttpExchange httpExchange, String key, String value) {
        httpExchange.getResponseHeaders().add("Set-Cookie", key + "=" + value +
                "; HttpOnly" +          // 禁止JS访问，防XSS
                "; Path=/" +            // 全路径生效
                "; Max-Age=3600" +   // 1小时过期（单位秒）
                "; SameSite=Lax"        // 防CSRF
        );
    }

    protected Map<String, String> getRequestCookies(HttpExchange exchange) {
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

    protected String parseRequestBodyStr(HttpExchange exchange) throws IOException {
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

    protected String getRequestUserAgent(HttpExchange httpExchange) {
        return httpExchange.getRequestHeaders().getFirst("User-Agent");
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

    protected void sendResponseString(HttpExchange exchange, int code, String body) {
        // 检查响应是否已发送
        if (exchange.getResponseCode() != -1) {
            return;
        }
        byte[] bytes = body.getBytes(charset);
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes);
             OutputStream os = exchange.getResponseBody()) {
            exchange.sendResponseHeaders(code, bytes.length);
            IoUtil.copy(is, os);
        } catch (Exception e) {
            log.error("响应失败: {}, 错误: {}", body, ThrowableUtil.getAllCauseMessage(e));
        } finally {
            exchange.close();
        }
    }

    // 发送错误响应
    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        // 检查响应是否已发送
        if (exchange.getResponseCode() != -1) {
            return;
        }
        String response = "<html><head><title>" + Constant.Application + "</title></head><body>" +
                "<h3>StatusCode:" + statusCode + "</h3>" +
                "<div>Error:" + message + "</div>" +
                "</body></html>";

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=" + charset.name());
        sendResponseString(exchange, statusCode, response);
    }

    // 发送错误响应
    protected void sendErrorResponse(HttpExchange exchange, Throwable e) throws IOException {
        sendErrorResponse(exchange, 500, ThrowableUtil.toString(e));
    }


    protected String getClientIP(HttpExchange exchange) {
        String ip = null;
        Headers requestHeaders = exchange.getRequestHeaders();
        for (String header : CusAccessObjectUtil.HEADERS_TO_TRY) {
            ip = requestHeaders.getFirst(header);
            if (StrUtil.isNotEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                boolean ipv4 = FormatUtil.isIpv4(ip);
                if (ipv4) {
                    return ip;
                }
                if (ip.indexOf(44) > 0) {
                    return ip.substring(0, ip.indexOf(44));
                }
                return ip;
            }
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : (FormatUtil.isIpv4(ip) ? ip : ip.substring(0, ip.indexOf(44)));
    }

}
