package cn.zhangheng.douyin;

import cn.zhangheng.common.service.ApplicationMain;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/20 星期二 12:35
 * @version: 1.0
 * @description:
 */
public class Main extends ApplicationMain<DouYinRoom> {

    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain<DouYinRoom, ?> getMonitorMain(DouYinRoom room) {
        return new DouYinMain(room.getSetting());
    }

    @Override
    protected DouYinRoom getRoom(Room.Platform platform, String id) {
        return new DouYinRoom(id);
    }

    @Override
    protected Room.Platform[] supportedPlatforms() {
        return new Room.Platform[]{Room.Platform.DouYin};
    }
}
