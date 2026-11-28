package cn.zhangheng.lmr.service;

import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import cn.zhangheng.common.jmx.JmxRegisterUtil;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.cpolar.CpolarSetting;
import cn.zhangheng.cpolar.CpolarStarter;
import cn.zhangheng.record.bean.Setting;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /** 面板「组件栏」用：HTTP 线程拿不到启动器实例，这里留一个静态引用 */
    private static volatile CpolarToolStarter INSTANCE;

    public CpolarToolStarter() {
        INSTANCE = this;
        registerMBean();
        cpolarStarter = new CpolarStarter();
    }

    public static CpolarToolStarter getInstance() {
        return INSTANCE;
    }

    /**
     * 组件栏用的只读快照：不抛异常、不含敏感信息（只给路径，不给秘钥之类）
     *
     * @param isDesensitize true 时把本地路径类信息打码（未通过操作秘钥验证的请求）
     */
    public static Map<String, Object> statusSnapshot(boolean isDesensitize) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        boolean configured = Files.exists(Paths.get("cpolar.setting"));
        snapshot.put("configured", configured);
        snapshot.put("exeExists", false);
        CpolarToolStarter instance = INSTANCE;
        if (instance == null) {
            snapshot.put("running", false);
            snapshot.put("publicUrl", null);
            snapshot.put("localCommand", null);
            snapshot.put("startTime", null);
            snapshot.put("updateGitTime", null);
            snapshot.put("updateGitSuccess", false);
            return snapshot;
        }
        snapshot.put("running", instance.getIsRunning());
        snapshot.put("publicUrl", instance.getPublicUrl());
        snapshot.put("localCommand", instance.getLocalCommand());
        snapshot.put("startTime", instance.updateTime > 0 ? TimeUtil.toTime(instance.updateTime) : null);
        snapshot.put("updateGitTime", instance.getUpdateGitTime());
        snapshot.put("updateGitSuccess", instance.getIsUpdateGit());
        try {
            CpolarSetting setting = ConfigKit.get(CpolarSetting.class);
            if (setting != null) {
                String exePath = setting.getExePath();
                snapshot.put("exePath", isDesensitize ? "***" : exePath);
                snapshot.put("exeExists", exePath != null && Files.exists(Paths.get(exePath)));
                snapshot.put("gitRepoDir", isDesensitize ? "***" : setting.getGitRepoDir());
                snapshot.put("gitFileRelativePath", isDesensitize ? "***" : setting.getGitFileRelativePath());
            }
        } catch (Exception e) {
            // 未配置/配置损坏时只少这几个字段，不影响面板展示
            instance.log.debug("读取 cpolar.setting 失败：{}", ThrowableUtil.getAllCauseMessage(e));
        }
        return snapshot;
    }

    @Override
    @JmxOperation(desc = "启动服务")
    public void start() {
        // 发行版默认不带内网穿透：缺少 cpolar.setting 时直接跳过，避免日志里出现误导性的“启动失败”
        if (!Files.exists(Paths.get("cpolar.setting"))) {
            log.info("未配置内网穿透（缺少 cpolar.setting），跳过 cpolar 启动");
            return;
        }
        if (thread != null && isRunning()) throw new RuntimeException("cpolar已运行！");
        thread = new Thread(() -> {
            try {
                if (delayedSec > 0) {
                    try {
                        TimeUnit.SECONDS.sleep(delayedSec);
                    } catch (InterruptedException ignored) {
                    }
                }
                int port = System.getProperty("monitor.port") != null ? Integer.parseInt(System.getProperty("monitor.port")) : ConfigKit.get(Setting.class).getMonitorServerPort();
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
    @JmxOperation(desc = "停止服务")
    public void stop() {
        if (thread == null && !isRunning()) {
            // 未启动过（例如没有 cpolar.setting），停止时应静默跳过
            log.debug("cpolar未运行，跳过停止");
            return;
        }
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
        return ConfigKit.get(CpolarSetting.class);
    }
}
