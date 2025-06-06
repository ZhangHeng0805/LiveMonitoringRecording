package cn.zhangheng.common.video.player;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.stream.StreamUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.xml.internal.ws.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 06:35
 * @version: 1.0
 * @description:
 */
public class StreamHandler extends MyHandler {
    private String type = "application/octet-stream";
    private final String prefix;

    public StreamHandler(String prefix, String type) {
        this.type = type;
        this.prefix = prefix;
    }

    public StreamHandler() {
        this.prefix = null;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        URI requestURI = httpExchange.getRequestURI();
        String file;
        if (prefix != null) {
            String path = requestURI.getPath();
            file = path.substring(1);
        } else {
            Map<String, String> map = parseQuery(requestURI.getQuery());
            file = map.get("file");
            if (file == null || file.isEmpty()) {
                sendErrorResponse(httpExchange, 400, "Missing file parameter");
                return;
            }
            type = MapUtil.getStr(map, "type", type);
        }
//        System.out.println(file);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(file)) {
            if (is == null) {
                sendErrorResponse(httpExchange, 404, file + ": File not found");
                return;
            }
            httpExchange.getResponseHeaders().set("Content-Type", type);
            httpExchange.sendResponseHeaders(200, is.available());
            try (OutputStream os = httpExchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }

    }

    // 辅助方法：通过读取流计算长度（适用于无法直接获取Path的情况）
    private long calculateStreamLength(InputStream inputStream) throws IOException {
        long length = 0;
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            length += bytesRead;
        }
        // 重置流以便后续读取（需流支持mark/reset）
        if (inputStream.markSupported()) {
            inputStream.reset();
        }
        return length;
    }
}
