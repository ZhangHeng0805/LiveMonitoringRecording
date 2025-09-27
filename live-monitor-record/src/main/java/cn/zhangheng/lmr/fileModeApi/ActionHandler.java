package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.util.DouYinBrowserFactory;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;

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
            actionMonitor(msg, query);
        } else if (indexPath.startsWith("record")) {
            Map<String, String> query = parseQuery(httpExchange);
            actionRecord(msg, query);
        } else if (indexPath.startsWith("getThread")) {
            getThread(msg);
        }else if (indexPath.startsWith("clear")) {
            DouYinBrowserFactory.getBrowser().clear();
            msg.setMessage("清理成功！");
        }else {
            msg.setCode(1);
            msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
        }
//        System.out.println(msg);
        responseJson(httpExchange, msg);
    }

    private void actionMonitor(Message msg, Map<String, String> query) {
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
                FileModeMain.startMain(key);
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

    private void actionRecord(Message msg, Map<String, String> query) {
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
}
