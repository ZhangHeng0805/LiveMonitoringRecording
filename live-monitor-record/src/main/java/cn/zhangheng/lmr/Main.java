package cn.zhangheng.lmr;

import cn.zhangheng.bilibili.BiliMain;
import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.*;
import cn.zhangheng.douyin.DouYinMain;
import cn.zhangheng.douyin.DouYinRoom;
import com.zhangheng.util.SettingUtil;

public class Main extends ApplicationMain<Room> {
    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain getMonitorMain(SettingUtil settingUtil, Room room) {
        switch (room.getPlatform()) {
            case DouYin:
                return new DouYinMain(settingUtil);
            case Bili:
                return new BiliMain(settingUtil);
            default:
                throw new IllegalArgumentException("参数错误platform");
        }
    }

    @Override
    protected Room getRoom(Room.Platform platform, String id) {
        switch (platform) {
            case DouYin:
                return new DouYinRoom(id);
            case Bili:
                return new BiliRoom(id);
            default:
                throw new IllegalArgumentException("参数错误platform");
        }
    }

    @Override
    protected Room.Platform[] supportedPlatforms() {
        return new Room.Platform[]{Room.Platform.DouYin, Room.Platform.Bili};
    }
}