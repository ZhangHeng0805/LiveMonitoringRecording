package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.RoomFileModel;
import cn.zhangheng.lmr.util.FilePageUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.file.FileUtil;
import com.zhangheng.file.FiletypeUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/31 星期日 19:17
 * @version: 1.0
 * @description:
 */
@Slf4j
public class FileResourcesHandler extends JSONHandler {

    public FileResourcesHandler(String prefix) {
        super(prefix);
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        return super.filter(httpExchange);
//        Map<String, String> cookies = getRequestCookies(httpExchange);
//        String session_id = cookies.get("session_id");
//        String token = cookies.get("token");
//        if (session_id == null || token == null) return false;
//        if (!JWTUtil.checkToken(token)) return false;
//        return session_id.equals(JWTUtil.getSessionID(token));
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        Map<String, String> query = parseQuery(httpExchange);
        Message<Object> message = new Message<>();
        RoomFileModel model = checkRoomKey(query, message);
        if (model != null) {
            String path = query.getOrDefault("path", "./");
            Room room = model.getMain().getRoom();
            String basePathStr = Constant.Application + "/" + room.getPlatform().getName() + "/[" + FileUtil.filterFileName(room.getNickname()) + "]/";
            Path targetPath = Paths.get(basePathStr, path);
            if (Files.exists(targetPath)) {
                if (Files.isDirectory(targetPath)) {
                    try {
                        Predicate<FilePageUtils.FileResult> filePredicate = null;
                        if (!"127.0.0.1".equals(getClientIP(httpExchange))) {
                            filePredicate = r -> {
                                if ("file".equals(r.getType())) {
                                    String name = r.getName();
                                    return !name.endsWith(".mp4") &&
                                            !name.endsWith(".flv") &&
                                            !name.endsWith("视频转换.log") &&
                                            !name.endsWith("视频下载.log")
                                            ;
                                }
                                return true;
                            };
                        }
                        int pageNum = Integer.parseInt(query.getOrDefault("pageNum", "1"));
                        int pageSize = Integer.parseInt(query.getOrDefault("pageSize", "100"));
                        FilePageUtils.FilePageResult filePageResult = FilePageUtils.getFilePage(targetPath, pageNum, pageSize, filePredicate);
                        message.setData(filePageResult);
                    } catch (Exception e) {
                        message.setCode(1);
                        message.setTitle(ThrowableUtil.getAllCauseMessage(e));
                        message.setMessage(ThrowableUtil.toString(e));
                    }
                } else {
                    responseFile(httpExchange, targetPath.toFile());
                    return;
                }
            } else {
                message.setCode(1);
                message.setTitle("路径不存在！");
                message.setMessage(targetPath + "路径不存在");
            }
        }
        responseJson(httpExchange, message);
    }

    public void responseFile(HttpExchange httpExchange, File file) throws IOException {
        log.debug("{} 请求[{}]:{}", getClientIP(httpExchange), httpExchange.getRequestURI().getPath(), httpExchange.getRequestHeaders().getFirst("User-Agent"));
        if (!file.exists()) {
            httpExchange.sendResponseHeaders(404, -1);
            return;
        }

        long fileLength = file.length();

        Headers headers = httpExchange.getResponseHeaders();
        String fileName = file.getName();
        String fileContentType = FiletypeUtil.getFileContentType(fileName);
        // ====================== 智能 Content-Type ======================
        if (fileContentType.startsWith("video/") || fileContentType.startsWith("audio/") || fileContentType.startsWith("image/")) {
            headers.set("Content-Type", fileContentType);
        } else {
            headers.set("Content-Type", fileContentType + "; charset=utf-8");
        }

        // ====================== 基础响应头 ======================
        headers.set("Connection", "close");
        headers.set("Access-Control-Allow-Origin", "*");
        // 文件名编码
        String encodedFileName = URLEncoder.encode(fileName, charset.name()).replace("+", "%20");
        String dis = "application/octet-stream".equals(fileContentType) ? "attachment" : "inline";
        headers.set("Content-Disposition", dis + "; filename*=UTF-8''" + encodedFileName);

        // ====================== 输出流 ======================
        httpExchange.sendResponseHeaders(200, fileLength);

        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()));
             OutputStream out = httpExchange.getResponseBody()
        ) {
            IoUtil.copy(bis, out);
        } finally {
            try {
                httpExchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    private RoomFileModel checkRoomKey(Map<String, String> query, Message msg) {
        String key = query.get("key");
        if (StrUtil.isBlank(key)) {
            msg.setCode(1);
            msg.setMessage("直播间标识不能为空！");
            return null;
        }
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("直播间标识[" + key + "]不存在！");
            return null;
        }
        return model;
    }

}
