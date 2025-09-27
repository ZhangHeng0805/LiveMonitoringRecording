package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.http.handle.MyHandler;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
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

    protected JSONHandler(String prefix) {
        this.prefix = prefix;
    }

    protected  void responseJson(HttpExchange httpExchange, Object json) throws IOException {
        responseJson(httpExchange, JSONUtil.toJsonStr(json), 200);
    }

    protected void responseJson(HttpExchange httpExchange, String json, int responseCode) throws IOException {
//        System.out.println(json);
        String contentType = "application/json; charset=" + charset.name();
        httpExchange.getResponseHeaders().set("Content-Type", contentType);
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(charset);
        httpExchange.sendResponseHeaders(responseCode, bytes.length);
        try (OutputStream os = httpExchange.getResponseBody()) {
            os.write(bytes);
        } catch (Exception e) {
            log.error("响应JSON响应失败: {}, 错误: {}", json, ThrowableUtil.getAllCauseMessage(e));
        } finally {
            httpExchange.close();
        }
    }
}
