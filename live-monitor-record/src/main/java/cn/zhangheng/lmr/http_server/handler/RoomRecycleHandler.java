package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomJson;
import cn.zhangheng.lmr.util.FilePageUtils;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/21 星期日 05:44
 * @version: 1.0
 * @description:
 */
@Slf4j
public class RoomRecycleHandler extends JSONHandler {
    public RoomRecycleHandler(String prefix) {
        super(prefix);
    }


    private boolean checkActionKey(Map<String, String> query, Message msg) {
        String actionKey = query.get("actionKey");
        if (StrUtil.isBlank(actionKey)) {
            msg.setMessage("操作秘钥不能为空！");
            return false;
        }
        if (!actionKey.equals(Constant.deviceUniqueId)) {
            msg.setMessage("操作秘钥错误！");
            return false;
        }
        return true;
    }

    @Override
    protected void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();
        try {
            Map<String, String> query = parseQuery(httpExchange);
            if (checkActionKey(query, msg)) {
                if (indexPath.startsWith("getList")) {
                    getList(query, msg);
                } else if (indexPath.startsWith("getDetails")) {
                    getDetails(query, msg);
                } else if (indexPath.startsWith("recover")) {
                    recover(httpExchange, query, msg);
                } else if (indexPath.startsWith("delete")) {
                    delete(query, msg);
                } else {
                    msg.setCode(1);
                    msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
                }
            } else {
                msg.setCode(1);
            }
        } catch (Throwable throwable) {
            msg.setCode(1);
            msg.setTitle("接口异常！");
            String errMsg = ThrowableUtil.getAllCauseMessage(throwable);
            msg.setMessage(errMsg);
            log.error(prefix + indexPath + "接口异常:{}", errMsg);
        }
        responseJson(httpExchange, msg);
    }

    private void recover(HttpExchange httpExchange, Map<String, String> query, Message<Object> msg) {
        String fileName = query.getOrDefault("path", null);
        if (fileName == null) {
            msg.setCode(1);
            msg.setMessage("path不能为空");
            return;
        }
        try {
            String bodyStr = parseRequestBodyStr(httpExchange);
            RoomJson roomJson = null;
            if (JSONUtil.isTypeJSON(bodyStr)) {
                roomJson = JSONUtil.toBean(bodyStr, RoomJson.class);
            }
            FileModeMain.recoverRoomFile(fileName, roomJson);
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("异常:" + e.getMessage());
        }
    }

    private void delete(Map<String, String> query, Message<Object> msg) {
        String fileName = query.getOrDefault("path", null);
        if (fileName == null) {
            msg.setCode(1);
            msg.setMessage("path不能为空");
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
        FilePageUtils.FilePageResult result = FilePageUtils.getFilePage(Paths.get(FileModeMain.getBasePath()), pageNum, pageSize, fileResult -> {
            String name = fileResult.getName();
            return name.endsWith(FileModeMain.getFileSuffix() + ".del");
        });
        List<Map<String, String>> collect = result.getFiles().stream().map(r -> {
            Map<String, String> map = new HashMap<>();
            map.put("title", r.getName().substring(0, r.getName().indexOf(".")));
            map.put("path", r.getName());
            return map;
        }).collect(Collectors.toList());
        JSONObject entries = JSONUtil.parseObj(result);
        entries.remove("files");
        entries.set("list", collect);
        msg.setData(entries);
    }

    private void getDetails(Map<String, String> query, Message msg) {
        String fileName = query.getOrDefault("path", null);
        if (fileName == null) {
            msg.setCode(1);
            msg.setMessage("path不能为空");
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
