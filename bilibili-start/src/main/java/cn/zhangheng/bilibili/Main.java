package cn.zhangheng.bilibili;

import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.ApplicationMain;
import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import com.zhangheng.util.SettingUtil;

public class Main extends ApplicationMain<BiliRoom> {


    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain<BiliRoom, ?> getMonitorMain(Setting setting, BiliRoom room) {
        return new BiliMain(setting);
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