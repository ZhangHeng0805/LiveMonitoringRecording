package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RequestContext;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomJson;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import cn.zhangheng.lmr.util.FilePageUtils;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.file.FileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseRequestBodyStr;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/21 星期日 05:44
 * @version: 1.0
 * @description:
 */
@Slf4j
public class RoomRecycleHandler extends AnnotationHandler {
    public RoomRecycleHandler(String prefix) {
        super(prefix, AuthResolvers.defaults());
    }

    /**
     * 回收站列表：私密接口（回收站属于管理功能，必须秘钥令牌）
     */
    @Endpoint(value = "/getList", auth = AuthPolicy.PRIVATE)
    public void list(RequestContext ctx) throws IOException {
        Message<Object> msg = ctx.message();
        getList(ctx.query(), msg);
    }

    /**
     * 回收站详情：私密接口
     */
    @Endpoint(value = "/getDetails", auth = AuthPolicy.PRIVATE)
    public void details(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        getDetails(ctx.query(), msg);
    }

    /**
     * 恢复房间：私密接口
     */
    @Endpoint(value = "/recover", auth = AuthPolicy.PRIVATE)
    public void recoverRoom(RequestContext ctx) throws IOException {
        Message<Object> msg = ctx.message();
        recover(ctx.exchange(), ctx.query(), msg);
    }

    /**
     * 彻底删除：私密接口
     */
    @Endpoint(value = "/delete", auth = AuthPolicy.PRIVATE)
    public void deleteRoom(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        delete(ctx.query(), msg);
    }

    /**
     * 回收站文件名校验：必须是回收站目录下的普通 *.room.json.del 文件，
     * 防止通过 path 参数做目录穿越（../），读到或删掉回收站以外的文件
     */
    private boolean invalidFileName(String fileName, Message<Object> msg) {
        String suffix = FileModeMain.getFileSuffix() + ".del";
        if (fileName == null || fileName.trim().isEmpty()) {
            msg.setCode(1);
            msg.setMessage("path不能为空");
            return true;
        }
        if (!fileName.endsWith(suffix)) {
            msg.setCode(1);
            msg.setMessage("不是回收站文件：" + fileName);
            return true;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            msg.setCode(1);
            msg.setMessage("文件路径不合法：" + fileName);
            return true;
        }
        return false;
    }

    private void recover(HttpExchange httpExchange, Map<String, String> query, Message<Object> msg) {
        String fileName = query.getOrDefault("path", null);
        if (invalidFileName(fileName, msg)) {
            return;
        }
        try {
            String bodyStr = parseRequestBodyStr(httpExchange, charset);
            RoomJson roomJson = null;
            if (JSONUtil.isTypeJSON(bodyStr)) {
                roomJson = JSONUtil.toBean(bodyStr, RoomJson.class);
            }
            FileModeMain.recoverRoomFile(fileName, roomJson);
            msg.setCode(0);
            msg.setMessage("恢复成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("异常:" + e.getMessage());
        }
    }

    private void delete(Map<String, String> query, Message<Object> msg) {
        String fileName = query.getOrDefault("path", null);
        if (invalidFileName(fileName, msg)) {
            return;
        }
        Path path = Paths.get(FileModeMain.getBasePath(), fileName);
        try {
            boolean b = Files.deleteIfExists(path);
            msg.setCode(b ? 0 : 1);
            String message = b ? "删除成功!" : "删除失败!";
            msg.setMessage(message);
            log.info("{}直播监听文件彻底{}", fileName, msg);
        } catch (IOException e) {
            msg.setCode(1);
            msg.setMessage("异常:" + e.getMessage());
        }
    }

    private void getList(Map<String, String> query, Message msg) throws IOException {
        int pageNum = Integer.parseInt(query.getOrDefault("pageNum", "1"));
        int pageSize = Integer.parseInt(query.getOrDefault("pageSize", "100"));
        String suffix = FileModeMain.getFileSuffix() + ".del";
        Path base = Paths.get(FileModeMain.getBasePath());
        FilePageUtils.FilePageResult result = FilePageUtils.getFilePage(base, base, pageNum, pageSize, fileResult -> {
            String name = fileResult.getName();
            return name.endsWith(suffix);
        });
        List<Map<String, String>> collect = result.getFiles().stream().map(r -> {
            Map<String, String> map = new HashMap<>();
            String name = r.getName();
            // 按固定后缀截断，昵称里含“.”时也不会被截错
            map.put("title", name.substring(0, name.length() - suffix.length()));
            map.put("path", name);
            return map;
        }).collect(Collectors.toList());
        JSONObject entries = JSONUtil.parseObj(result);
        entries.remove("files");
        entries.set("list", collect);
        msg.setData(entries);
    }

    private void getDetails(Map<String, String> query, Message msg) {
        String fileName = query.getOrDefault("path", null);
        if (invalidFileName(fileName, msg)) {
            return;
        }
        Path path = Paths.get(FileModeMain.getBasePath(), fileName);
        if (Files.exists(path)) {
            String read = FileUtil.readString(path.toFile(), StandardCharsets.UTF_8);
            RoomJson bean = JSONUtil.toBean(read, RoomJson.class);
            bean.desensitize();
            msg.setData(bean);
        } else {
            msg.setCode(1);
            msg.setMessage(fileName + "文件不存在");
        }
    }
}
