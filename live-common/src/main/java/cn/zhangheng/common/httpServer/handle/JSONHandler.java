package cn.zhangheng.common.httpServer.handle;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/27 星期六 21:14
 * @version: 1.0
 * @description:
 */
@Slf4j
public abstract class JSONHandler extends MyHandler {
    protected final String prefix;

    public JSONHandler(String prefix) {
        this.prefix = prefix;
    }

    protected void responseJson(HttpExchange httpExchange, Object json) throws IOException {
        responseJson(httpExchange, JSONUtil.toJsonStr(json), 200);
    }

    protected void responseJson(HttpExchange httpExchange, String json, int responseCode) throws IOException {
        if (httpExchange.getResponseCode() != -1) {
            return;
        }
        String contentType = "application/json; charset=" + charset.name();
        Headers responseHeaders = httpExchange.getResponseHeaders();
        responseHeaders.set("Content-Type", contentType);
        byte[] bytes = json.getBytes(charset);
        try (InputStream is = new ByteArrayInputStream(bytes);
             OutputStream os = httpExchange.getResponseBody()) {
            httpExchange.sendResponseHeaders(responseCode, bytes.length);
            IoUtil.copy(is, os);
        } catch (Exception e) {
            log.error("响应JSON响应失败: {}, 错误: {}", json, ThrowableUtil.getAllCauseMessage(e));
            throw e;
        } finally {
            httpExchange.close();
        }
    }
}
