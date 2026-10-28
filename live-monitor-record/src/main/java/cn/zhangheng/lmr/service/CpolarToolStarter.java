package cn.zhangheng.lmr.service;

import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import cn.zhangheng.common.jmx.JmxRegisterUtil;
import cn.zhangheng.cpolar.CpolarSetting;
import cn.zhangheng.cpolar.CpolarStarter;
import cn.zhangheng.record.bean.Setting;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 22:43
 * @version: 1.0
 * @description:
 */
public class CpolarToolStarter extends Starter {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private Thread thread;
    // ========== MBean 注册 ==========
    private ObjectName mbeanObjectName;
    private final CpolarStarter cpolarStarter;

    public CpolarToolStarter() {
        registerMBean();
        cpolarStarter = new CpolarStarter();
    }

    @Override
    public void start() {
        if (thread != null && isRunning()) throw new RuntimeException("cpolar已运行！");
        thread = new Thread(() -> {
            try {
                if (delayedSec > 0) {
                    try {
                        TimeUnit.SECONDS.sleep(delayedSec);
                    } catch (InterruptedException ignored) {
                    }
                }
                int port = System.getProperty("monitor.port") != null ? Integer.parseInt(System.getProperty("monitor.port")) : Setting.getInstance().getMonitorServerPort();
                thread.setName("CpolarToolStarter-" + port);
                setRunning(true);
                cpolarStarter.start(port);
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
        if (thread == null && !isRunning()) throw new RuntimeException("cpolar未运行！");
        try {
            cpolarStarter.stop(false);
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
        String type = "CpolarToolStarter";
        try {
            mbeanObjectName = JmxRegisterUtil.register(this, type);
        } catch (Exception e) {
            log.error("JMX MBean注册失败[{}]", type, e);
        }
    }

    // JMX注销（销毁时调用）
    private void unregisterMBean() {
        try {
            JmxRegisterUtil.unregister(mbeanObjectName);
        } catch (Exception e) {
            log.error("JMX注销异常[{}]", mbeanObjectName.getCanonicalName(), e);
        }
        // 置空，防止重复注销
        mbeanObjectName = null;
    }

    @JmxOperation(desc = "启动服务")
    public void startServer() {
        start();
    }

    @JmxOperation(desc = "停止服务")
    public void stopServer() {
        stop();
    }

    @JmxOperation(desc = "更新Git文件")
    public void updateGitURLFile() {
        try {
            cpolarStarter.triggerGitUpload();
        } catch (Exception e) {
            throw new RuntimeException(ThrowableUtil.getAllCauseMessage(e));
        }
    }

    @JmxAttribute(name = "isRunning", desc = "是否运行")
    public boolean getIsRunning() {
        return isRunning();
    }

    @JmxAttribute(name = "isUpdateGitSuccess", desc = "是否更新Git文件成功")
    public boolean getIsUpdateGit() {
        return cpolarStarter.isUpdateGitSuccess();
    }

    @JmxAttribute(name = "startTime", desc = "启动/停止时间")
    public String getStartTime() {
        String time = TimeUtil.toTime(updateTime);
        return isRunning() ? "启动时间: " + time : "停止时间: " + time;
    }

    @JmxAttribute(name = "updateGitTime", desc = "更新Git文件时间")
    public String getUpdateGitTime() {
        if (cpolarStarter.getUpdateGitTime() > 0) {
            return TimeUtil.toTime(cpolarStarter.getUpdateGitTime());
        } else {
            return null;
        }
    }

    @JmxAttribute(name = "publicUrl", desc = "映射公网地址")
    public String getPublicUrl() {
        return cpolarStarter.getPublicUrl();
    }

    @JmxAttribute(name = "localCommand", desc = "运行命令")
    public String getLocalCommand() {
        return String.join(" ", cpolarStarter.getLocalCommand());
    }

    @JmxAttribute(name = "setting", desc = "设置")
    public CpolarSetting getSetting() {
        return CpolarSetting.getInstance();
    }
}
