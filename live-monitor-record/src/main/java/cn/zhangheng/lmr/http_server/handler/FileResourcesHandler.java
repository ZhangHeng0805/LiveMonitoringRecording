package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.common.util.RoomUtils;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.http_server.util.CheckUtils;
import cn.zhangheng.lmr.util.FilePageUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.file.FileUtil;
import com.zhangheng.file.FiletypeUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.getClientIP;
import static cn.zhangheng.common.httpServer.util.HandlerUtils.parseQuery;

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
        super.filter(httpExchange);
        return CheckUtils.checkCookie(httpExchange);
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        Map<String, String> query = parseQuery(httpExchange);
        Message<Object> message = new Message<>();
        RoomFileModel model = checkRoomKey(query, message);
        if (model != null) {
            String path = query.getOrDefault("path", "./");
            Room room = model.getMain().getRoom();
            String basePathStr = RoomUtils.getBasePathStr(room);
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

    public void responseFile1(HttpExchange httpExchange, File file) throws IOException {
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
//        headers.set("Access-Control-Allow-Origin", "*");
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
        }
    }

    private static final SimpleDateFormat GMT_DATE_FORMAT;
    private static final int BUFFER_SIZE = 1024 * 64; // 64KB 大缓冲区，优化大文件IO
    static {
        GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    public void responseFile(HttpExchange httpExchange, File file) throws IOException {
        Path filePath = file.toPath();
        Headers reqHeaders = httpExchange.getRequestHeaders();
        Headers respHeaders = httpExchange.getResponseHeaders();
        String uriPath = httpExchange.getRequestURI().getPath();
        String ua = reqHeaders.getFirst("User-Agent");
        log.debug("{} 请求[{}]:{}", getClientIP(httpExchange), uriPath, ua);

        if (!file.exists() || file.isDirectory()) {
            httpExchange.sendResponseHeaders(404, -1);
            httpExchange.close();
            return;
        }

        long totalSize = file.length();
        BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
        long lastModifyTime = attr.lastModifiedTime().toMillis();
        String lastModifyGMT = GMT_DATE_FORMAT.format(new Date(lastModifyTime));
        String etag = "\"" + totalSize + "-" + lastModifyTime + "\"";

        // 协商缓存：304 无需返回文件
        String ifModifiedSince = reqHeaders.getFirst("If-Modified-Since");
        String ifNoneMatch = reqHeaders.getFirst("If-None-Match");
        boolean cacheHit = false;
        if (StrUtil.isNotBlank(ifNoneMatch) && ifNoneMatch.equals(etag)) {
            cacheHit = true;
        } else if (StrUtil.isNotBlank(ifModifiedSince)) {
            try {
                Date reqDate = GMT_DATE_FORMAT.parse(ifModifiedSince);
                if (reqDate.getTime() >= lastModifyTime) {
                    cacheHit = true;
                }
            } catch (Exception ignored) {}
        }
        if (cacheHit) {
            respHeaders.set("ETag", etag);
            respHeaders.set("Last-Modified", lastModifyGMT);
            respHeaders.set("Cache-Control", "public, max-age=86400");
            httpExchange.sendResponseHeaders(304, -1);
            httpExchange.close();
            return;
        }

        // 基础跨域
//        respHeaders.set("Access-Control-Allow-Origin", "*");
        respHeaders.set("ETag", etag);
        respHeaders.set("Last-Modified", lastModifyGMT);
        // 静态资源缓存1天，大文件减少重复请求
        respHeaders.set("Cache-Control", "public, max-age=86400");

        String fileName = file.getName();
        String fileContentType = FiletypeUtil.getFileContentType(fileName);
        // Content-Type
        if (fileContentType.startsWith("video/") || fileContentType.startsWith("audio/") || fileContentType.startsWith("image/")) {
            respHeaders.set("Content-Type", fileContentType);
        } else {
            respHeaders.set("Content-Type", fileContentType + "; charset=utf-8");
        }

        // 下载/在线预览标记
        String encodedFileName = URLEncoder.encode(fileName, charset.name()).replace("+", "%20");
        String dis = "application/octet-stream".equals(fileContentType) ? "attachment" : "inline";
        respHeaders.set("Content-Disposition", dis + "; filename*=UTF-8''" + encodedFileName);

        // ========== 核心：处理 Range 分片请求，支持视频拖拽 ==========
        String rangeHeader = reqHeaders.getFirst("Range");
        long start = 0;
        long end = totalSize - 1;
        boolean isRangeRequest = false;
        if (StrUtil.isNotBlank(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            try {
                String rangeVal = rangeHeader.substring(6);
                String[] parts = rangeVal.split("-");
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && StrUtil.isNotBlank(parts[1])) {
                    end = Long.parseLong(parts[1]);
                }
                // 边界校验
                if (start > totalSize - 1) {
                    // 范围超出文件，返回416
                    httpExchange.sendResponseHeaders(416, -1);
                    httpExchange.close();
                    return;
                }
                end = Math.min(end, totalSize - 1);
                isRangeRequest = true;
            } catch (Exception e) {
                // range格式错误，正常全量返回
                isRangeRequest = false;
                start = 0;
                end = totalSize - 1;
            }
        }

        long contentLength = end - start + 1;
        if (isRangeRequest) {
            respHeaders.set("Accept-Ranges", "bytes");
            respHeaders.set("Content-Range", String.format("bytes %d-%d/%d", start, end, totalSize));
            httpExchange.sendResponseHeaders(206, contentLength); // 分片响应码206
        } else {
            respHeaders.set("Accept-Ranges", "bytes");
            httpExchange.sendResponseHeaders(200, totalSize); // 全量200
        }

        // 分片读取输出，不会一次性加载整个文件
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(filePath), BUFFER_SIZE);
             OutputStream out = httpExchange.getResponseBody()
        ) {
            // 跳过起始偏移
            if (start > 0) {
                bis.skip(start);
            }
            byte[] buf = new byte[BUFFER_SIZE];
            long remain = contentLength;
            int read;
            while (remain > 0 && (read = bis.read(buf, 0, (int) Math.min(BUFFER_SIZE, remain))) != -1) {
                out.write(buf, 0, read);
                remain -= read;
                // 可选：out.flush(); 低延迟预览，会小幅损耗性能
            }
            out.flush();
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
