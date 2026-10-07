package cn.zhangheng.lmr.service;

import cn.zhangheng.lmr.http_server.LocalMonitorHttpServer;
import cn.zhangheng.record.bean.Setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:26
 * @version: 1.0
 * @description:
 */
public class LocalHttpServerStarter extends Starter {
    private static LocalMonitorHttpServer localMonitorServer;

    @Override
    public void start() {
        if (localMonitorServer != null && isRunning()) return;
        localMonitorServer = new LocalMonitorHttpServer(Setting.getInstance().getMonitorServerPort());
        localMonitorServer.start();
        setRunning(true);
    }

    @Override
    public void stop() {
        if (localMonitorServer != null) {
            localMonitorServer.stop();
            localMonitorServer = null;
            setRunning(false);
        }
    }

    @Override
    public void destroy() {
        stop();
    }
}
