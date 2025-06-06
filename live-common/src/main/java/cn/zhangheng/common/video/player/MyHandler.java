package cn.zhangheng.common.video.player;

import cn.zhangheng.common.bean.Constant;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    protected final Charset charset = StandardCharsets.UTF_8;

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
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    // 发送错误响应
    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = "<html><head><title>" + Constant.Application + "</title></head><body><h1>" + statusCode + " - " + message + "</h1></body></html>";
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=" + charset.name());
        byte[] bytes = response.getBytes(charset);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}
