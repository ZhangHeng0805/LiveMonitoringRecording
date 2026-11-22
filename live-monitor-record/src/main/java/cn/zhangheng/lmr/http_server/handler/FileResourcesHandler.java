package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RequestContext;
import cn.zhangheng.record.util.RoomUtils;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import cn.zhangheng.lmr.util.FilePageUtils;
import cn.zhangheng.lmr.util.MonitorLogParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.file.FiletypeUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.getClientIP;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/31 星期日 19:17
 * @version: 1.0
 * @description:
 */
@Slf4j
public class FileResourcesHandler extends AnnotationHandler {

    public FileResourcesHandler(String prefix) {
        super(prefix, AuthResolvers.defaults());
    }

    /**
     * 录制文件浏览/下载：整个 /fileRes 前缀共用一套鉴权（必须令牌/秘钥）
     */
    @Endpoint(value = "/", auth = AuthPolicy.READ)
    public void fileResource(RequestContext ctx) throws IOException {
        HttpExchange httpExchange = ctx.exchange();
        Map<String, String> query = ctx.query();
        Message<Object> message = ctx.message();
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
//                        if (!"127.0.0.1".equals(getClientIP(httpExchange))) {
//                            filePredicate = r -> {
//                                if ("file".equals(r.getType())) {
//                                    String name = r.getName();
//                                    return !name.endsWith(".mp4") &&
//                                            !name.endsWith(".flv") &&
//                                            !name.endsWith("视频转换.log") &&
//                                            !name.endsWith("视频下载.log")
//                                            ;
//                                }
//                                return true;
//                            };
//                        }
                        int pageNum = Integer.parseInt(query.getOrDefault("pageNum", "1"));
                        int pageSize = Integer.parseInt(query.getOrDefault("pageSize", "50"));
                        // 返回给前端的 path 相对房间录制根目录计算（前端会原样回传）
                        FilePageUtils.FilePageResult filePageResult = FilePageUtils.getFilePage(
                                Paths.get(basePathStr), targetPath, pageNum, pageSize, filePredicate);
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

    /**
     * 监听日志统计：解析目录里的 ***监听.log，返回时间序列给前端画统计图。
     * path 可以是日期目录，也可以直接指向某个 .log 文件。
     */
    @Endpoint(value = "/stats", auth = AuthPolicy.READ)
    public void stats(RequestContext ctx) throws IOException {
        Map<String, String> query = ctx.query();
        Message<Object> message = ctx.message();
        RoomFileModel model = checkRoomKey(query, message);
        if (model == null) {
            return;
        }
        String path = query.getOrDefault("path", "./");
        Room room = model.getMain().getRoom();
        String basePathStr = RoomUtils.getBasePathStr(room);
        Path targetPath = Paths.get(basePathStr, path);
        if (!Files.exists(targetPath)) {
            message.setCode(1);
            message.setMessage("路径不存在！" + targetPath);
            return;
        }
        try {
            MonitorLogParser.Result result = Files.isDirectory(targetPath)
                    ? MonitorLogParser.parseDir(targetPath)
                    : MonitorLogParser.parseFile(targetPath);
            message.setData(result);
            if (result.getSeries() == null || result.getSeries().isEmpty()) {
                message.setCode(1);
                message.setTitle("暂无可统计的日志");
                message.setMessage("该目录下没有找到可解析的 ***监听.log 统计记录");
            } else {
                message.setMessage("解析日志 " + result.getFiles().size() + " 个，统计点 " + result.getSeries().size() + " 条");
            }
        } catch (Exception e) {
            message.setCode(1);
            message.setTitle("日志解析失败");
            message.setMessage(ThrowableUtil.getAllCauseMessage(e));
        }
    }

    // 限速：字节/毫秒，0=不限速，例如 100 代表 100KB/s
    private static final long LIMIT_BYTE_PER_MS = 0;
    //RFC 1123 线程安全格式化器；SimpleDateFormat 非线程安全，多线程响应下会解析错乱
    private static final DateTimeFormatter GMT_DATE_FORMAT =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).withZone(ZoneOffset.UTC);
    private static final int BUFFER_SIZE = 1024 * 8; //缓冲区，优化大文件IO

    public void responseFile(HttpExchange httpExchange, File file) throws IOException {
        Path filePath = file.toPath();
        Headers reqHeaders = httpExchange.getRequestHeaders();
        Headers respHeaders = httpExchange.getResponseHeaders();
        String ua = reqHeaders.getFirst("User-Agent");
        log.debug("{} 文件请求[{}]:{}", getClientIP(httpExchange), filePath, ua);

        if (!file.exists() || file.isDirectory()) {
            //交换对象由 MyHandler.handle() 的 finally 统一关闭，此处不再重复 close
            httpExchange.sendResponseHeaders(404, -1);
            return;
        }

        long totalSize = file.length();
        BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
        long lastModifyTime = attr.lastModifiedTime().toMillis();
        String lastModifyGMT = GMT_DATE_FORMAT.format(Instant.ofEpochMilli(lastModifyTime));
        String etag = "\"" + totalSize + "-" + lastModifyTime + "\"";

        // 协商缓存：304 无需返回文件
        String ifModifiedSince = reqHeaders.getFirst("If-Modified-Since");
        String ifNoneMatch = reqHeaders.getFirst("If-None-Match");
        boolean cacheHit = false;
        if (StrUtil.isNotBlank(ifNoneMatch) && ifNoneMatch.equals(etag)) {
            cacheHit = true;
        } else if (StrUtil.isNotBlank(ifModifiedSince)) {
            try {
                //HTTP 日期只精确到秒，按秒比较，避免毫秒误差让 304 失效
                long reqSecond = ZonedDateTime.parse(ifModifiedSince, GMT_DATE_FORMAT).toInstant().getEpochSecond();
                if (reqSecond >= lastModifyTime / 1000) {
                    cacheHit = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (cacheHit) {
            respHeaders.set("ETag", etag);
            respHeaders.set("Last-Modified", lastModifyGMT);
            respHeaders.set("Cache-Control", "public, max-age=86400");
            httpExchange.sendResponseHeaders(304, -1);
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
                String rangeVal = rangeHeader.substring(6).trim();
                // 多段 Range 暂不支持，按全量返回
                if (rangeVal.contains(",")) throw new IllegalArgumentException("不支持多段Range");
                String[] parts = rangeVal.split("-", -1);
                if (parts.length > 1 && StrUtil.isBlank(parts[0]) && StrUtil.isNotBlank(parts[1])) {
                    // 后缀范围：bytes=-500 表示最后 500 字节
                    long suffixLen = Long.parseLong(parts[1].trim());
                    start = Math.max(0, totalSize - suffixLen);
                } else if (StrUtil.isNotBlank(parts[0])) {
                    start = Long.parseLong(parts[0].trim());
                    if (parts.length > 1 && StrUtil.isNotBlank(parts[1])) {
                        end = Long.parseLong(parts[1].trim());
                    }
                } else {
                    throw new IllegalArgumentException("Range格式不合法");
                }
                // 边界校验
                if (start > totalSize - 1) {
                    // 范围超出文件，返回416
                    httpExchange.sendResponseHeaders(416, -1);
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
        // 客户端拖拽进度条/暂停/关闭连接导致的 IOException 属正常现象，不按错误上报
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(start);
            copyWithLimit(raf, httpExchange.getResponseBody(), start, end);
        } catch (IOException e) {
            if (httpExchange.getResponseCode() == -1) throw e;
            log.debug("{} 下载中断{}[{}-{}]:{}", getClientIP(httpExchange), filePath, start, end, e.getMessage());
        }

    }

    /**
     * 带限速的拷贝
     */
    private void copyWithLimit(RandomAccessFile raf, OutputStream out, long start, long end) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = end - start + 1;
        int readLen;
        long lastTs = System.currentTimeMillis();
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            readLen = raf.read(buf, 0, toRead);
            if (readLen == -1) break;
            out.write(buf, 0, readLen);
            remaining -= readLen;

            // 限速逻辑
            if (LIMIT_BYTE_PER_MS > 0) {
                long now = System.currentTimeMillis();
                long cost = now - lastTs;
                long expectCost = readLen / LIMIT_BYTE_PER_MS;
                if (expectCost > cost) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(expectCost - cost);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                lastTs = System.currentTimeMillis();
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
