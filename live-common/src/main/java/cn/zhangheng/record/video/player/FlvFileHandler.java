package cn.zhangheng.record.video.player;

import cn.zhangheng.record.httpServer.handle.MyHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.util.Map;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 03:56
 * @version: 1.0
 * @description:
 */
public class FlvFileHandler extends MyHandler {

    private final String FILE_DIR;

    public FlvFileHandler(String fileDir) {
        FILE_DIR = fileDir;
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        Map<String, String> params = parseQuery(httpExchange);
        String fileName = params.get("file");
        if (fileName == null || fileName.isEmpty()) {
            sendErrorResponse(httpExchange, 400, "Missing file parameter");
            return;
        }
        // 构建完整文件路径
        File file = new File(FILE_DIR + fileName);

        // 检查文件是否存在且是FLV文件
        if (!file.exists() || !file.isFile() || !fileName.toLowerCase().endsWith(".flv")) {
            sendErrorResponse(httpExchange, 404, fileName + " - File not found or not a FLV file");
            return;
        }
        // 设置响应头
        httpExchange.getResponseHeaders().set("Content-Type", "video/x-flv");

        // 发送文件内容
        long fileLength = file.length();
        httpExchange.sendResponseHeaders(200, fileLength);

        try (OutputStream os = httpExchange.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }


}
