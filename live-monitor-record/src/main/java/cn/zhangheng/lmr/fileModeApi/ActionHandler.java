package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.enums.MonitorStatus;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
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
    public void handle(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message msg = new Message();
        if (indexPath.startsWith("monitor")) {
            Map<String, String> query = parseQuery(httpExchange);
            if (checkActionKey(query, msg)) {
                actionMonitor(msg, query);
            } else {
                msg.setCode(1);
            }
        } else if (indexPath.startsWith("record")) {
            Map<String, String> query = parseQuery(httpExchange);
            if (checkActionKey(query, msg)) {
                actionRecord(msg, query);
            } else {
                msg.setCode(1);
            }
        } else if (indexPath.startsWith("setting")) {
            Map<String, String> query = parseQuery(httpExchange);
            if (checkActionKey(query, msg)) {
                actionSetting(msg, query);
            } else {
                msg.setCode(1);
            }
        } else if (indexPath.startsWith("refresh")) {
            Map<String, String> query = parseQuery(httpExchange);
            actionRefresh(msg, query);
        } else if (indexPath.startsWith("getThread")) {
            getThread(msg);
        } else if (indexPath.startsWith("clear")) {
            Map<String, String> query = parseQuery(httpExchange);
            if (checkActionKey(query, msg)) {
                DouYinBrowserFactory.getBrowser().clear();
                msg.setMessage("清理成功！");
            } else {
                msg.setCode(1);
            }
        } else {
            msg.setCode(1);
            msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
        }
//        System.out.println(msg);
        responseJson(httpExchange, msg);
    }

    private synchronized void actionMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        Main main = FileModeMain.getMainMap().get(key);
        if (main == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
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
        Main main = FileModeMain.getMainMap().get(key);
        if (main == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
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
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        Main main = FileModeMain.getMainMap().get(key);
        if (main == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
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

    private synchronized void actionSetting(Message msg, Map<String, String> query) {
        String key = query.get("key");
        Main main = FileModeMain.getMainMap().get(key);
        try {
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            if (query.containsKey("delayIntervalSec")) {
                int delayIntervalSec = Integer.parseInt(query.get("delayIntervalSec"));
                monitorMain.getRoom().getSetting().setDelayIntervalSec(delayIntervalSec);
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
        msg.setObj(res);
        msg.setMessage(StrUtil.format("核心线程数: {}， 正在工作的线程数: {}, 剩余可用线程数: {}", corePoolSize, activeCount, remainingThreads));
    }

    private boolean checkActionKey(Map<String, String> query, Message msg) {
        String actionKey = query.get("actionKey");
        if (StrUtil.isBlank(actionKey)) {
            msg.setMessage("操作秘钥不能为空！");
            return false;
        }
        String key = query.get("key");
        Main main = FileModeMain.getMainMap().get(key);
        if (main == null) {
            msg.setMessage("标识不存在！");
            return false;
        }
        if (!actionKey.equals(Constant.deviceUniqueId)) {
            msg.setMessage("操作秘钥错误！");
            return false;
        }
        return true;
    }
}
