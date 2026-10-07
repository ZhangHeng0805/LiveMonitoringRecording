package cn.zhangheng.lmr.service;

import cn.hutool.json.JSONUtil;
import cn.zhangheng.cpolar.CpolarSetting;
import cn.zhangheng.cpolar.CpolarStarter;
import cn.zhangheng.record.bean.Setting;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:43
 * @version: 1.0
 * @description:
 */
public class CpolarToolStarter extends Starter implements CpolarToolStarterMBean {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private Thread thread;
    // ========== MBean 注册 ==========
    private ObjectName mbeanObjectName;

    public CpolarToolStarter() {
        registerMBean();
    }

    @Override
    public void start() {
        if (thread != null && isRunning()) return;
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
                setRunning(true);
                CpolarStarter.start(port);
            } catch (Exception e) {
                log.error("cpolar端口映射服务创建失败！{}", ThrowableUtil.getAllCauseMessage(e));
            } finally {
                setRunning(false);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        if (thread == null && !isRunning()) return;
        try {
            CpolarStarter.stop(false);
            thread.interrupt();
            thread = null;
        } catch (Exception e) {
            log.error("cpolar端口映射服务停止失败！{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            updateTime = System.currentTimeMillis();
        }
    }

    @Override
    public void destroy() {
        stop();
        unregisterMBean();
    }

    // JMX MBean 注册
    private void registerMBean() {
        String mbeanName = "cn.zhangheng.lmr:type=CpolarToolStarter";
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbeanObjectName = new ObjectName(mbeanName);
            if (!mbs.isRegistered(mbeanObjectName)) {
                mbs.registerMBean(this, mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX MBean注册失败[{}]", mbeanName, e);
        }
    }

    // JMX注销（销毁时调用）
    private void unregisterMBean() {
        if (mbeanObjectName == null) {
            return;
        }
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            if (mbs.isRegistered(mbeanObjectName)) {
                mbs.unregisterMBean(mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX注销异常[{}]", mbeanObjectName.getCanonicalName(), e);
        }
        // 置空，防止重复注销
        mbeanObjectName = null;
    }

    @Override
    public void startServer() {
        start();
    }

    @Override
    public void stopServer() {
        stop();
    }

    @Override
    public void updateGitURLFile() throws Exception {
        CpolarStarter.updateGitURLFile();
    }

    @Override
    public boolean getIsRunning() {
        return isRunning();
    }

    @Override
    public String getStartTime() {
        String time = TimeUtil.toTime(updateTime);
        return isRunning() ? "启动时间: " + time : "停止时间: " + time;
    }

    @Override
    public String getUpdateGitTime() {
        if (CpolarStarter.getUpdateGitTime() > 0) {
            return TimeUtil.toTime(CpolarStarter.getUpdateGitTime());
        } else {
            return null;
        }
    }

    @Override
    public String getPublicUrl() {
        return CpolarStarter.getPublicUrl();
    }

    @Override
    public String getLocalCommand() {
        return String.join(" ", CpolarStarter.getLocalCommand());
    }

    @Override
    public String getSetting() {
        return JSONUtil.parseObj(CpolarSetting.getInstance()).toStringPretty();
    }
}
