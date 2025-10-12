package cn.zhangheng.kuaishou;

import cn.zhangheng.common.service.ApplicationMain;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import cn.zhangheng.kuaishou.service.KuaiShouMain;

public class Main extends ApplicationMain<KuaiShouRoom> {
    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain<KuaiShouRoom, ?> getMonitorMain(KuaiShouRoom room) {
        return new KuaiShouMain(room.getSetting());
    }

    @Override
    protected KuaiShouRoom getRoom(Room.Platform platform, String id) {
        return new KuaiShouRoom(id);
    }

    @Override
    protected Room.Platform[] supportedPlatforms() {
        return new Room.Platform[]{Room.Platform.KuaiShou};
    }
}