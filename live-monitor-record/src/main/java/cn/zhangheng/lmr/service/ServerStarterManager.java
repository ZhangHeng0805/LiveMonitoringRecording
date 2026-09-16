package cn.zhangheng.lmr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:23
 * @version: 1.0
 * @description:
 */
public class ServerStarterManager {
    private static final Logger log = LoggerFactory.getLogger(ServerStarterManager.class);
    private final List<Starter> starters = new ArrayList<>();

    public void addStarter(Starter starter) {
        starters.add(starter);
    }

    public void startAllServer() {
        for (Starter starter : starters) {
            if (starter == null) continue;
            String name = starter.getClass().getName();
            try {
                starter.start();
                log.info("{}服务已启动！", name);
            } catch (Exception e) {
                log.error("{}服务启动失败!", name);
            }
        }
    }

    public void stopAllServer() {
        for (Starter starter : starters) {
            if (starter == null) continue;
            String name = starter.getClass().getName();
            try {
                starter.stop();
                log.info("{}服务已停止！", name);
            } catch (Exception e) {
                log.info("{}服务停止失败！", name);
            }
        }
    }
}
