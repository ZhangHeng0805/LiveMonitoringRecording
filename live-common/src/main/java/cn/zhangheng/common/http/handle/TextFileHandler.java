package cn.zhangheng.common.http.handle;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 03:51
 * @version: 1.0
 * @description:
 */
public class TextFileHandler extends MyHandler {

    private String response = "";
    private final String contextType;

    public TextFileHandler(String file) {
        this(file, "text/plain");
    }

    public TextFileHandler(String file, String contextType) {
        this.contextType = contextType;
        response = readText(file);
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
    public void handle(HttpExchange t) throws IOException {
//        Map<String, String> map = parseQuery(t.getRequestURI().getQuery());

        t.getResponseHeaders().set("Content-Type", contextType + "; charset=" + charset.name());
        // 计算字节长度时使用相同的字符集
        byte[] responseBytes = response.getBytes(charset);
        t.sendResponseHeaders(200, responseBytes.length);
        t.getResponseBody().write(responseBytes);
        t.getResponseBody().close();
    }
}
