package cn.zhangheng.kuaishou;

import cn.zhangheng.common.bean.ApplicationMain;
import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import cn.zhangheng.kuaishou.service.KuaiShouMain;

public class Main extends ApplicationMain<KuaiShouRoom> {
    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain<KuaiShouRoom, ?> getMonitorMain(Setting settingUtil, KuaiShouRoom room) {
        return new KuaiShouMain(settingUtil);
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