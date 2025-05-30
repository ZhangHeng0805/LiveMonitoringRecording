package cn.zhangheng.bilibili.service;

import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.RoomMonitor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 22:28
 * @version: 1.0
 * @description:
 */
public class BiliMonitor extends RoomMonitor<BiliRoom, BilibiliService> {
    public BiliMonitor(int delayIntervalSec, BiliRoom room) {
        super(delayIntervalSec, room);
    }

    public BiliMonitor(BiliRoom room) {
        super(room);
    }

    @Override
    protected BilibiliService getRoomService(BiliRoom room) {
        return new BilibiliService(room);
    }
}
