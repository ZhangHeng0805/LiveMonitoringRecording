package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.RoomFileModel;
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
public class ApiHandler extends JSONHandler {

    public ApiHandler(String prefix) {
        super(prefix);
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message msg = new Message();
//        ConcurrentHashMap<String, Main> mainMap = FileModeMain.getRoomFileMap();
        if (StrUtil.isNotBlank(indexPath)) {
            RoomFileModel model = FileModeMain.getModelById(indexPath);
            if (model == null) {
                msg.setMessage("没有找到开直播监听信息");
            } else {
                msg.setObj(getResponseMap(model));
            }
        } else {
            List<Map<String, Object>> collect = FileModeMain.getRoomFileMap().values().stream()
                    .map(ApiHandler::getResponseMap)
                    .collect(Collectors.toList());
            msg.setObj(collect);
        }
        responseJson(httpExchange, msg);
    }


    private static Map<String, Object> getResponseMap(RoomFileModel model) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", model.getId());
        Main main = model.getMain();
        map.put("isRunning", model.isRunning());
        map.put("startTime", model.getStartTime());
        map.put("endTime", model.getEndTime());
        map.put("status", main.getMonitorMain().getStatus());
        map.put("room-status", main.getMonitorMain().getRoomMonitor().getState());
        Room room = main.getMonitorMain().getRoom();
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
        setting.remove("browserIsPageClear");
        setting.remove("browserHeadless");
        setting.remove("maxMonitorThreads");
        if (StrUtil.isBlank(setting.getStr("xiZhiUrl"))) {
            setting.putOnce("isNotice", Boolean.FALSE);
        } else {
            setting.putOnce("isNotice", Boolean.TRUE);
        }
        setting.remove("xiZhiUrl");
        map.put("room", entries);
        Recorder recorder = main.getMonitorMain().getRecorder();
        if (recorder != null) {
            Map<String, Object> record = new HashMap<>();
            record.put("msg", recorder.getProgressMsg());
            record.put("definition", recorder.getDefinition());
            record.put("path", recorder.getSaveFilePath());
            record.put("isRecord", recorder.isRunning());
            map.put("recorder", record);
        }
        return map;
    }


}
