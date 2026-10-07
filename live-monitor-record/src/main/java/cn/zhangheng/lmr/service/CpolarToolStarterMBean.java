package cn.zhangheng.lmr.service;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/06 星期二 22:02
 * @version: 1.0
 * @description:
 */
public interface CpolarToolStarterMBean {
    void startServer();

    void stopServer();

    void updateGitURLFile();

    boolean getIsRunning();

    String getStartTime();

    String getUpdateGitTime();

    String getPublicUrl();

    String getLocalCommand();

    String getSetting();
}
