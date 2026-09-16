package cn.zhangheng.lmr.service;

import cn.zhangheng.lmr.http_server.LocalMonitorServer;
import cn.zhangheng.record.bean.Setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:26
 * @version: 1.0
 * @description:
 */
public class LocalHttpServerStarter implements Starter{
    private static LocalMonitorServer localMonitorServer;
    @Override
    public void start() {
        localMonitorServer = new LocalMonitorServer(Setting.getInstance().getMonitorServerPort());
        localMonitorServer.start();
    }

    @Override
    public void stop() {
        if (localMonitorServer != null) {
            localMonitorServer.stop();
        }
    }
}
