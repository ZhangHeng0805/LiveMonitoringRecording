package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.activation.WarnException;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.common.httpServer.handle.JWTUtil;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.enums.MonitorStatus;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.bean.DouYinVideo;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.douyin.browser.DouYinVideoParse;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.RoomFileModel;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/27 星期六 21:22
 * @version: 1.0
 * @description:
 */
public class ActionHandler extends JSONHandler {

    protected ActionHandler(String prefix) {
        super(prefix);
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        if (httpExchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            Headers responseHeaders = httpExchange.getResponseHeaders();
            responseHeaders.set("Access-Control-Allow-Origin", "*");
            responseHeaders.set("Access-Control-Allow-Headers", "Content-Type");
            httpExchange.sendResponseHeaders(204, -1);
            return false;
        }
//        Map<String, String> cookies = getRequestCookies(httpExchange);
//        String session_id = cookies.get("session_id");
//        String token = cookies.get("token");
//        if (session_id == null || token == null) return false;
//        if (!JWTUtil.checkToken(token)) return false;
//        return session_id.equals(JWTUtil.getSessionID(token));
        return true;
    }

    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();
        try {
            if (indexPath.startsWith("monitor")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionMonitor(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("record")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionRecord(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("addRoom")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg)) {
                    addMonitor(msg, httpExchange);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("delRoom")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    delMonitor(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("setting")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
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
            } else if (indexPath.startsWith("getThread")) {
                getThread(msg);
            } else if (indexPath.startsWith("getCount")) {
                Map<String, Object> douYinCounter = DouYinBrowserFactory.getBrowser().getCount();
                Map<String, Object> allCounter = FileModeMain.getCounter();
                Map<String, Object> data = new HashMap<>();
                data.put("DouYinCounter", douYinCounter);
                data.put("AllCounter", allCounter);
                msg.setData(data);
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
            } else if (indexPath.startsWith("videoParsing")) {
                Map<String, String> query = parseQuery(httpExchange);
                videoParsing(msg, query, getRequestUserAgent(httpExchange));
            } else {
                msg.setCode(1);
                msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
            }
        } catch (Throwable throwable) {
            msg.setCode(1);
            msg.setTitle("接口异常！");
            msg.setMessage(ThrowableUtil.getAllCauseMessage(throwable));
            throwable.printStackTrace();
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
        String bodyStr = parseRequestBodyStr(httpExchange);
        try {
            FileModeMain.addMain(JSONUtil.parseObj(bodyStr));
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
            String bodyStr = parseRequestBodyStr(httpExchange);
            JSONObject setting = JSONUtil.parseObj(bodyStr);
            if (setting.containsKey("delayIntervalSec")) {
                int delayIntervalSec = setting.getInt("delayIntervalSec");
                monitorMain.getRoom().getSetting().setDelayIntervalSec(delayIntervalSec);
            }
            if (setting.containsKey("convertFlvToMp4")) {
                boolean convertFlvToMp4 = setting.getBool("convertFlvToMp4");
                monitorMain.getRoom().getSetting().setConvertFlvToMp4(convertFlvToMp4);
            }
            if (setting.containsKey("openSubtitle")) {
                boolean openSubtitle = setting.getBool("openSubtitle");
                monitorMain.getRoom().getSetting().setOpenSubtitle(openSubtitle);
            }
            if (setting.containsKey("isLoop")) {
                boolean isLoop = setting.getBool("isLoop");
                monitorMain.getRoom().getSetting().setLoop(isLoop);
            }
            if (setting.containsKey("cookie")) {
                String cookie = setting.getStr("cookie");
                if (StrUtil.isNotBlank(cookie)) {
                    if (cookie.equalsIgnoreCase("null")) {
                        monitorMain.getRoom().setCookie(null);
                    } else {
                        monitorMain.getRoom().setCookie(Setting.parseCookie(cookie));
                    }
                }
            }
            if (setting.containsKey("xiZhiUrl")) {
                String xiZhiUrl = setting.getStr("xiZhiUrl");
                if (StrUtil.isNotBlank(xiZhiUrl)) {
                    if (xiZhiUrl.equalsIgnoreCase("null")) {
                        monitorMain.getRoom().getSetting().setXiZhiUrl(null);
                    } else {
                        monitorMain.getRoom().getSetting().setXiZhiUrl(xiZhiUrl);
                    }
                }
            }
            msg.setMessage("设置成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("设置错误! " + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    private void getThread(Message msg) {
        ThreadPoolExecutor threadPool = FileModeMain.getThreadPool();
        int corePoolSize = threadPool.getCorePoolSize();
        int activeCount = threadPool.getActiveCount();
        int remainingThreads = corePoolSize - activeCount;
        Map<String, Integer> res = new HashMap<>();
        res.put("corePoolSize", corePoolSize);
        res.put("activeCount", activeCount);
        res.put("remainingThreads", remainingThreads);
        msg.setData(res);
        msg.setMessage(StrUtil.format("核心线程数: {}， 正在工作的线程数: {}, 剩余可用线程数: {}", corePoolSize, activeCount, remainingThreads));
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

    private boolean checkRoomKey(Map<String, String> query, Message msg) {
        String key = query.get("key");
        if (StrUtil.isBlank(key)) {
            msg.setMessage("直播间标识不能为空！");
            return false;
        }
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("直播间标识[" + key + "]不存在！");
            return false;
        }
        return true;
    }
}
