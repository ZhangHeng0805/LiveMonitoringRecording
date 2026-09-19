package cn.zhangheng.lmr.service;

import cn.zhangheng.record.bean.Setting;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:43
 * @version: 1.0
 * @description:
 */
public class CpolarToolStarter implements Starter {
    private static final Logger log = LoggerFactory.getLogger(CpolarToolStarter.class);

    @Override
    public void start() {
//        int port = Setting.getInstance().getMonitorServerPort();
        int port= Integer.parseInt(System.getProperty("monitor.port"));
        Thread thread = new Thread(() -> {
            try {
                cn.zhangheng.cpolar.Starter.start(port);
            } catch (Exception e) {
                log.error("cpolar端口映射服务创建失败！{}", ThrowableUtil.getAllCauseMessage(e));
            }
        });
        thread.setDaemon(true);
        thread.setName("CpolarToolStarter-" + port);
        thread.start();
    }

    @Override
    public void stop() {
        try {
            cn.zhangheng.cpolar.Starter.stop(false);
        } catch (Exception e) {
            log.error("cpolar端口映射服务停止失败！{}", ThrowableUtil.getAllCauseMessage(e));
        }
    }
}
