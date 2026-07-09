package cn.zhangheng.common.httpServer.handle;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import cn.zhangheng.common.httpServer.util.JWTUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.util.Scanner;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 03:51
 * @version: 1.0
 * @description:
 */
public class TextFileHandler extends MyHandler {

//    private static final Logger log = LoggerFactory.getLogger(TextFileHandler.class);
    private final String file;
    private final String contextType;


    public TextFileHandler(String file) {
        this(file, "text/plain");
    }

    public TextFileHandler(String file, String contextType) {
        this.contextType = contextType;
        this.file = file;
    }

    private String readText(String file) {
        // 构建包含视频URL的HTML响应
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file)) {
            if (inputStream == null) {
                throw new IOException("无法找到 " + file + " 文件");
            }
            // 使用 Scanner 读取流内容
            try (Scanner scanner = new Scanner(inputStream, charset.name())) {
                return scanner.useDelimiter("\\A").next();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        String path = httpExchange.getRequestURI().getPath();
        if ("/favicon.ico".equals(path)) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("img/favicon.ico")) {
                Headers responseHeaders = httpExchange.getResponseHeaders();
                responseHeaders.set("Content-Type", "application/octet-stream");
                if (is == null) {
                    throw new NoSuchFileException(path);
                }
                httpExchange.sendResponseHeaders(200, is.available());
                try (OutputStream os = httpExchange.getResponseBody()) {
                    IoUtil.copy(is, os);
                }
                return true;
            }
        }

        return true;
    }


    @Override
    public void request(HttpExchange t) throws IOException {
//        String clientIP = getClientIP(t);
//        log.debug("{} 请求[{}]:{}", clientIP, t.getRequestURI().getPath(), t.getRequestHeaders().getFirst("User-Agent"));
        t.getResponseHeaders().set("Content-Type", contextType + "; charset=" + charset.name());
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
             OutputStream outputStream = t.getResponseBody()) {
            if (inputStream == null) {
                throw new NoSuchFileException(file);
            }
            t.sendResponseHeaders(200, inputStream.available());
            IoUtil.copy(inputStream, outputStream);
        }
    }
}
