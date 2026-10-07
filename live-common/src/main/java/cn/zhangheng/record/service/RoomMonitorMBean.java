package cn.zhangheng.record.service;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/07 星期三 10:38
 * @version: 1.0
 * @description:
 */
public interface RoomMonitorMBean {
    String getRoomName();
    String getRoomUrl();
    String getRoomPlatform();
    String getRefreshTime();
    boolean getIsLiving();
    boolean getIsOpenSubtitle();
    int getRefreshCount();
    void startSubtitle();
    void stopSubtitle();
    void nowRefresh();
}
