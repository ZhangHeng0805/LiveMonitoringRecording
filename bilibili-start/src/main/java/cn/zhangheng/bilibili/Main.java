package cn.zhangheng.bilibili;

import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.service.ApplicationMain;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;

public class Main extends ApplicationMain<BiliRoom> {


    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain<BiliRoom, ?> getMonitorMain(BiliRoom room) {
        return new BiliMain(room.getSetting());
    }

    @Override
    protected BiliRoom getRoom(Room.Platform platform, String id) {
        return new BiliRoom(id);
    }

    @Override
    protected Room.Platform[] supportedPlatforms() {
        return new Room.Platform[]{Room.Platform.Bili};
    }

}