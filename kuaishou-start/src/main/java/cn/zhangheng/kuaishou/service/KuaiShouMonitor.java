package cn.zhangheng.kuaishou.service;

import cn.zhangheng.common.service.RoomMonitor;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 19:10
 * @version: 1.0
 * @description:
 */
public class KuaiShouMonitor extends RoomMonitor<KuaiShouRoom, KuaiShouService> {
    public KuaiShouMonitor(KuaiShouRoom room) {
        super(room);
    }

    @Override
    protected KuaiShouService getRoomService(KuaiShouRoom room) {
        return new KuaiShouService(room);
    }
}
