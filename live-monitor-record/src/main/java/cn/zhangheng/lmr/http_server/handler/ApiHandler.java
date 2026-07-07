package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.common.httpServer.util.HandlerUtils;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.bean.DouYinVideo;
import cn.zhangheng.douyin.browser.DouYinVideoParse;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.bean.RoomFileModel;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.parseQuery;
import static cn.zhangheng.lmr.http_server.util.CheckUtils.checkCookie;

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
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        return checkCookie(httpExchange);
    }



    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        try {
            String indexPath = getIndexPath(httpExchange, prefix);
            Message<Object> msg = new Message<>();

            if (indexPath.startsWith("rooms")) {
                List<Map<String, Object>> collect = FileModeMain.getRoomFileMap().values().stream()
                        .map(ApiHandler::getRoomsMap)
                        .filter(m -> m != null && !m.isEmpty())
                        .sorted(Comparator.comparing(m -> m.get("isRunning").equals(false)))
                        .collect(Collectors.toList());
                msg.setData(collect);
            } else if (indexPath.startsWith("videoParsing")) {
                Map<String, String> query = parseQuery(httpExchange);
                videoParsing(msg, query, HandlerUtils.getRequestUserAgent(httpExchange));
            } else {
                msg.setCode(1);
                msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
            }
            responseJson(httpExchange, msg);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            sendErrorResponse(httpExchange, e);
            throw e;
        }
    }

    private void videoParsing(Message msg, Map<String, String> query, String userAgent) {
        try {
            String url = query.get("url");
            if (url == null) {
                throw new IllegalArgumentException("解析URl缺省！");
            }
            DouYinVideo parse = DouYinVideoParse.parse(url, userAgent);
            msg.setData(parse);
            msg.setMessage("解析成功！");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(e.getMessage());
        }
    }


    private static Map<String, Object> getRoomsMap(RoomFileModel model) {
        try {
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
            entries.append("statistics", main.getMonitorMain().statistics(null, room));
            JSONObject setting = entries.getJSONObject("setting");
            String cookie = setting.getStr("cookie" + room.getPlatform().name(), "");
            if (!cookie.isEmpty()) {
                setting.set("cookie", cookie.length() > 100 ? cookie.substring(0, 100) + "......" : cookie);
            }
            setting.remove("cookieDouYin");
            setting.remove("cookieBili");
            setting.remove("cookieKuaiShou");
            setting.remove("ffmpegPath");
            setting.remove("activateVoucherPath");
            setting.remove("recordType");
            setting.remove("monitorServerPort");
            setting.remove("browserIsPageClear");
            setting.remove("browserHeadless");
            setting.remove("maxMonitorThreads");
            setting.set("isAutoRecord", main.getMonitorMain().isAutoRecord());
            String xiZhiUrl = setting.getStr("xiZhiUrl");
            if (StrUtil.isBlank(xiZhiUrl)) {
                setting.putOnce("isNotice", Boolean.FALSE);
            } else {
                setting.putOnce("isNotice", Boolean.TRUE);
                setting.set("xiZhiUrl", StrUtil.replace(xiZhiUrl, 25, 50, "***"));

            }
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
        } catch (Exception e) {
            if (!(e instanceof NullPointerException)) {
                log.error(ThrowableUtil.getAllCauseMessage(e), e);
            }
        }
        return null;
    }


}
