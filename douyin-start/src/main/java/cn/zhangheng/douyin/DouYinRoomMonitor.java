package cn.zhangheng.douyin;

import cn.zhangheng.common.bean.RoomMonitor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/23 星期五 15:30
 * @version: 1.0
 * @description:
 */
public class DouYinRoomMonitor extends RoomMonitor<DouYinRoom,DouYinRoomService> {

    public DouYinRoomMonitor(DouYinRoom room) {
        super(room);
    }

    public DouYinRoomMonitor(int delayIntervalSec, DouYinRoom douYinRoom) {
        super(delayIntervalSec,douYinRoom);
    }

    @Override
    protected DouYinRoomService getRoomService(DouYinRoom room) {
        return new DouYinRoomService(room);
    }

}
