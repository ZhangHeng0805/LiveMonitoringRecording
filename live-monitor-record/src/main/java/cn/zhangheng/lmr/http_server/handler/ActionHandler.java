package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.douyin.browser.DouYinUrlParse;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.handle.JSONHandler;
import cn.zhangheng.record.httpServer.util.JWTUtil;
import cn.zhangheng.record.service.MonitorMain;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.enums.MonitorStatus;
import cn.zhangheng.record.record.Recorder;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.bean.RoomJson;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseRequestBodyStr;
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

    /** 抖音直播间地址，捕获其中的数字房间号 */
    private static final Pattern DOUYIN_LIVE_URL_PATTERN = Pattern.compile("live\\.douyin\\.com/(\\d{5,20})");
    /** 纯数字房间号 */
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^\\d{5,20}$");

    public ActionHandler(String prefix) {
        super(prefix);
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        // 换取接口令牌的入口自身用 actionKey 校验，这里放行
        if (getIndexPath(httpExchange, prefix).startsWith("auth")) {
            return true;
        }
        return checkRequestAuth(httpExchange);
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
            } else if (indexPath.startsWith("parseDouYinLink")) {
                Map<String, String> query = parseQuery(httpExchange);
                parseDouYinRoomId(msg, query.get("shareLink"));
            } else if (indexPath.startsWith("refresh")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkRoomKey(query, msg)) {
                    actionRefresh(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("destroyBrowser")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    PlaywrightBrowserFactory.destroy();
                    msg.setMessage("浏览器销毁成功！");
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("closeBrowser")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    PlaywrightBrowserFactory.closeBrowser();
                    msg.setMessage("浏览器关闭成功！");
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("exit")) {
                // 退出整个程序：必须携带操作秘钥，防止任意人关掉常驻服务
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    msg.setMessage("退出指令已接收，程序将在数秒内停止！");
                    log.info("收到来自{}的退出指令，准备终止程序", httpExchange.getRemoteAddress());
                    FileModeMain.shutdown("http:" + httpExchange.getRemoteAddress());
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("auth")) {
                // 用操作秘钥换取无状态接口令牌（跨站前端用 Authorization: Bearer 携带）
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    long expireSec = ConfigKit.get(Setting.class).getApiExpireSec();
                    String token = JWTUtil.generateApiToken(expireSec);
                    msg.setMessage("接口令牌签发成功！");
                    JSONObject data = new JSONObject();
                    data.set("token", token);
                    data.set("expireSec", expireSec);
                    data.set("expireAt", System.currentTimeMillis() + expireSec * 1000);
                    msg.setData(data);
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
            monitorMain.exit();
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

    /**
     * 解析抖音分享链接为直播间号，并对结果做校验：
     * 1. 输入为空 → 提示缺少链接；
     * 2. 输入本身就是数字房间号 → 直接返回，不启动浏览器；
     * 3. 解析得到的地址必须是 live.douyin.com/{数字}，否则视为解析失败（不会把网页地址当房间号回填）。
     */
    private void parseDouYinRoomId(Message msg, String shareLink) {
        String raw = shareLink == null ? "" : shareLink.trim();
        if (StrUtil.isBlank(raw)) {
            msg.setCode(1);
            msg.setMessage("缺少分享链接，请粘贴抖音直播间分享文案或链接");
            return;
        }
        if (ROOM_ID_PATTERN.matcher(raw).matches()) {
            msg.setData(raw);
            msg.setMessage("输入已是直播间号，无需解析");
            return;
        }
        long start = System.currentTimeMillis();
        String url;
        try {
            url = DouYinUrlParse.parseDouyinShareLink(raw);
        } catch (Throwable e) {
            log.warn("抖音链接解析异常", e);
            msg.setCode(1);
            msg.setMessage("解析失败，请检查分享链接是否有效（" + ThrowableUtil.getAllCauseMessage(e) + "）");
            return;
        }
        String result = url == null ? "" : url.trim();
        log.info("抖音链接解析 {} -> {}（耗时 {} ms）",
                raw.length() > 80 ? raw.substring(0, 80) + "..." : raw, result,
                System.currentTimeMillis() - start);
        if (StrUtil.isBlank(result)) {
            msg.setCode(1);
            msg.setMessage("解析失败：未获取到直播间地址，分享链接可能已失效");
            return;
        }
        Matcher liveMatcher = DOUYIN_LIVE_URL_PATTERN.matcher(result);
        if (liveMatcher.find()) {
            msg.setData(liveMatcher.group(1));
            msg.setMessage("解析成功");
            return;
        }
        if (ROOM_ID_PATTERN.matcher(result).matches()) {
            msg.setData(result);
            msg.setMessage("解析成功");
            return;
        }
        msg.setCode(1);
        msg.setMessage("解析结果不是直播间地址（返回：" + briefUrl(result) + "），请确认是抖音直播间分享链接");
    }

    private static String briefUrl(String url) {
        String text = url.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

}