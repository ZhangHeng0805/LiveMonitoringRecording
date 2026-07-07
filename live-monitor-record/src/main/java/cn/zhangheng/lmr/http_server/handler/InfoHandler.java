package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.FileModeMain;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:06
 * @version: 1.0
 * @description:
 */
public class InfoHandler extends JSONHandler {
    public InfoHandler(String prefix) {
        super(prefix);
    }


    @Override
    protected void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();
        if (indexPath.startsWith("getThread")) {
            getThread(msg);
        } else if (indexPath.startsWith("getCount")) {
            Map<String, Object> douYinCounter = DouYinBrowserFactory.getBrowser().getCount();
            Map<String, Object> allCounter = FileModeMain.getCounter();
            Map<String, Object> data = new HashMap<>();
            data.put("DouYinCounter", douYinCounter);
            data.put("AllCounter", allCounter);
            msg.setData(data);
        } else {
            msg.setCode(1);
            msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
        }
        responseJson(httpExchange, msg);
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
}
