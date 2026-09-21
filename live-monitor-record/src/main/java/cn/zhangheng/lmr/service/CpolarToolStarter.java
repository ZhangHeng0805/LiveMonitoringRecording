package cn.zhangheng.lmr.service;

import cn.zhangheng.record.bean.Setting;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:43
 * @version: 1.0
 * @description:
 */
public class CpolarToolStarter extends Starter {
    private static final Logger log = LoggerFactory.getLogger(CpolarToolStarter.class);
    private Thread thread;

    @Override
    public void start() {
        thread = new Thread(() -> {
            try {
                if (delayedSec > 0) {
                    try {
                        TimeUnit.SECONDS.sleep(delayedSec);
                    } catch (InterruptedException ignored) {
                    }
                }
                int port = System.getProperty("monitor.port") != null ? Integer.parseInt(System.getProperty("monitor.port")) : Setting.getInstance().getMonitorServerPort();
                thread.setName("CpolarToolStarter - " + port);
                cn.zhangheng.cpolar.Starter.start(port);
            } catch (Exception e) {
                log.error("cpolar端口映射服务创建失败！{}", ThrowableUtil.getAllCauseMessage(e));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        try {
            cn.zhangheng.cpolar.Starter.stop(false);
            thread.interrupt();
        } catch (Exception e) {
            log.error("cpolar端口映射服务停止失败！{}", ThrowableUtil.getAllCauseMessage(e));
        }
    }
}
