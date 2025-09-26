package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/26 星期五 11:31
 * @version: 1.0
 * @description:
 */
@Slf4j
public class JsonHandler implements HttpHandler {
    private final String type = "application/json";
    private final Charset charset = StandardCharsets.UTF_8;
    ;
    private final String prefix;

    public JsonHandler(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        URI requestURI = httpExchange.getRequestURI();
        String path = requestURI.getPath();
        String indexPath = StrUtil.subAfter(path, prefix, true);
        Message msg = new Message();
        ConcurrentHashMap<String, Main> mainMap = FileModeMain.getMainMap();
        if (StrUtil.isNotBlank(indexPath)) {
            Main main = mainMap.get(indexPath);
            if (main == null) {
                msg.setMessage("没有找到开直播监听信息");
            } else {
                msg.setObj(getResponseMap(new AbstractMap.SimpleEntry<>(indexPath, main)));
            }
        } else {
            List<Map<String, Object>> collect = mainMap.entrySet().stream()
                    .map(JsonHandler::getResponseMap)
                    .collect(Collectors.toList());
            msg.setObj(collect);
        }
//        System.out.println(msg);
        responseJson(httpExchange, msg);

    }

    private static Map<String, Object> getResponseMap(Map.Entry<String, Main> main) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", main.getKey());
        Main value = main.getValue();
        map.put("status", value.getMonitorMain().getStatus());
        map.put("room-status", value.getMonitorMain().getRoomMonitor().getState());
        Room room = value.getMonitorMain().getRoom();
        JSONObject entries = JSONUtil.parseObj(room);
        entries.remove("cookie");
        JSONObject setting = entries.getJSONObject("setting");
        setting.remove("cookieDouYin");
        setting.remove("cookieBili");
        setting.remove("cookieKuaiShou");
        setting.remove("ffmpegPath");
        setting.remove("activateVoucherPath");
        setting.remove("recordType");
        setting.remove("flvPlayerPort");
        if (StrUtil.isBlank(setting.getStr("xiZhiUrl"))){
                setting.putOnce("isNotice",Boolean.FALSE);
        }else {
            setting.putOnce("isNotice", Boolean.TRUE);
        }
        setting.remove("xiZhiUrl");
        map.put("room", entries);
        Recorder recorder = value.getMonitorMain().getRecorder();
        if (recorder != null) {
            Map<String, Object> record = new HashMap<>();
            record.put("msg", recorder.getProgressMsg());
            record.put("definition", recorder.getDefinition());
            record.put("path", recorder.getSaveFilePath());
            map.put("recorder", record);
        }
//        System.out.println(map);
        return map;
    }

    private void responseJson(HttpExchange httpExchange, Object json) throws IOException {
        responseJson(httpExchange, JSONUtil.toJsonStr(json), 200);
    }

    private void responseJson(HttpExchange httpExchange, String json) throws IOException {
        responseJson(httpExchange, json, 200);
    }

    private void responseJson(HttpExchange httpExchange, String json, int responseCode) throws IOException {
//        System.out.println(json);
        // 设置响应头（不预先计算Content-Length）
        String contentType = type + "; charset=" + charset.name();
        httpExchange.getResponseHeaders().set("Content-Type", contentType);
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        // 发送响应头时不指定长度（-1表示由底层自动处理）
        byte[] bytes = json.getBytes(charset);
        httpExchange.sendResponseHeaders(responseCode, bytes.length);
        try (OutputStream os = httpExchange.getResponseBody()) {
            os.write(bytes);
        } catch (Exception e) {
            log.error("响应JSON响应失败: {}, 错误: {}", json, ThrowableUtil.getAllCauseMessage(e));
        } finally {
            httpExchange.close();
        }
    }
}
