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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.getClientIP;

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
        Headers reqHeaders = httpExchange.getRequestHeaders();
        Headers respHeaders = httpExchange.getResponseHeaders();
        // 获取当前请求的前端Origin
        String origin = reqHeaders.getFirst("Origin");

        // Origin存在
        if (origin != null && origin.startsWith("http")) {
            // 开启凭证，支持跨域读写Cookie
            respHeaders.set("Access-Control-Allow-Origin", origin);
        } else {
            respHeaders.set("Access-Control-Allow-Origin", "*");
        }
        // 允许携带Cookie、凭证
        respHeaders.set("Access-Control-Allow-Credentials", "true");
        // 允许跨域请求的请求头
        respHeaders.set("Access-Control-Allow-Headers", "Content-Type");
        respHeaders.set("Vary", "Origin"); //告诉浏览器Origin是动态变化的
        if ("OPTIONS".equals(httpExchange.getRequestMethod())) {
            // 上面所有跨域头全部设置后
            httpExchange.sendResponseHeaders(204, -1);
            return false;
        }
        //TODO 是否过滤请求 true放行 false拦截
        return true;
    }

    protected abstract void request(HttpExchange httpExchange) throws IOException;


    protected String getIndexPath(HttpExchange httpExchange, String prefix) {
        URI requestURI = httpExchange.getRequestURI();
        String path = requestURI.getPath();
        return StrUtil.subAfter(path, prefix, true);
    }

    protected void handleThrowable(HttpExchange httpExchange, Throwable throwable) throws IOException {
        sendErrorResponse(httpExchange, throwable);
        log.error("{}请求[{}]发生异常:{}", getClientIP(httpExchange), httpExchange.getRequestURI().getPath(), ThrowableUtil.getAllCauseMessage(throwable));
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


}
