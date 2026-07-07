package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.enums.MonitorStatus;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.bean.RoomJson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.parseQuery;
import static cn.zhangheng.common.httpServer.util.HandlerUtils.parseRequestBodyStr;
import static cn.zhangheng.lmr.http_server.util.CheckUtils.*;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/27 星期六 21:22
 * @version: 1.0
 * @description:
 */
public class ActionHandler extends JSONHandler {

    private static final Logger log = LoggerFactory.getLogger(ActionHandler.class);

    public ActionHandler(String prefix) {
        super(prefix);
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        return checkCookie(httpExchange);
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();
        try {
            if (indexPath.startsWith("monitor")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionMonitor(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("record")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionRecord(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("addRoom")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    addMonitor(msg, httpExchange);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("delRoom")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    delMonitor(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("setting")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionSetting(msg, httpExchange, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("refresh")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkRoomKey(query, msg)) {
                    actionRefresh(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("clear")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    DouYinBrowserFactory.getBrowser().threadLocalClear();
                    msg.setMessage("清理成功！");
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("close")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    boolean b = DouYinBrowserFactory.closeBrowser();
                    msg.setCode(b ? 0 : 1);
                    msg.setMessage("浏览器重启" + (b ? "成功" : "失败"));
                } else {
                    msg.setCode(1);
                }
            } else {
                msg.setCode(1);
                msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
            }
        } catch (Throwable throwable) {
            msg.setCode(1);
            msg.setTitle("接口异常！");
            String errMsg = ThrowableUtil.getAllCauseMessage(throwable);
            msg.setMessage(errMsg);
            log.error(prefix + indexPath + "接口异常:{}", errMsg);
        }
//        System.out.println(msg);
        responseJson(httpExchange, msg);
    }

    private synchronized void delMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        try {
            FileModeMain.delMain(key);
            msg.setMessage("直播监听删除成功");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(e.getMessage());
        }
    }

    private synchronized void addMonitor(Message msg, HttpExchange httpExchange) throws IOException {
        String bodyStr = parseRequestBodyStr(httpExchange,charset);
        try {
            FileModeMain.addMain(JSONUtil.toBean(bodyStr, RoomJson.class));
            msg.setMessage("直播监听新增成功");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(e.getMessage());
        }
    }

    private synchronized void actionMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        if (flag == monitorMain.getIsRunning()) {
            msg.setCode(1);
            msg.setMessage(StrUtil.format("监听状态已{},请勿重复操作！", flag ? "开启" : "关闭"));
            return;
        }
        if (flag) {
            try {
                FileModeMain.restartMain(key);
            } catch (RuntimeException e) {
                msg.setCode(1);
                msg.setMessage(e.getMessage());
                return;
            }
            msg.setMessage("监听启动成功！");
        } else {
            monitorMain.setIsForceStop(true);
            monitorMain.stop();
            msg.setMessage("标识[" + key + "]的直播监听已关闭！");
        }

    }

    private synchronized void actionRecord(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();

        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        Recorder recorder = monitorMain.getRecorder();
        boolean isRecord = recorder != null && recorder.isRunning();
        if (flag == isRecord) {
            msg.setCode(1);
            msg.setMessage(StrUtil.format("录制状态已{},请勿重复操作！", flag ? "开启" : "停止"));
            return;
        }
        boolean res;
        if (flag) {
            res = monitorMain.startRecord();
        } else {
            res = monitorMain.stopRecord();
        }
        msg.setMessage(StrUtil.format("{}录制{}！", flag ? "开启" : "停止", res ? "成功" : "失败"));

    }

    private synchronized void actionRefresh(Message msg, Map<String, String> query) {
        String key = query.get("key");
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        if (monitorMain.getStatus() != MonitorStatus.RUNNING) {
            msg.setCode(1);
            msg.setMessage("该直播间没有启动监听!");
            return;
        }
        try {
            monitorMain.getRoomMonitor().nowRefresh();
            msg.setMessage("刷新成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("刷新失败!");
        }
    }

    private synchronized void actionSetting(Message msg, HttpExchange httpExchange, Map<String, String> query) {
        String key = query.get("key");
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        try {
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            String bodyStr = parseRequestBodyStr(httpExchange,charset);
            RoomJson bean = JSONUtil.toBean(bodyStr, RoomJson.class);
            Room room = monitorMain.getRoom();
            Setting setting = room.getSetting();
            bean.convert(setting);
            monitorMain.setAutoRecord(bean.isAutoRecord());
            room.initSetting(setting);
            msg.setMessage("设置成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("设置错误! " + ThrowableUtil.getAllCauseMessage(e));
        }
    }




}
