package cn.zhangheng.lmr;

import cn.zhangheng.bilibili.BiliMain;
import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.*;
import cn.zhangheng.douyin.DouYinMain;
import cn.zhangheng.douyin.DouYinRoom;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import cn.zhangheng.kuaishou.service.KuaiShouMain;

public class Main extends ApplicationMain<Room> {
    public static void main(String[] args) {
        new Main().start(null, args);
    }

    @Override
    protected MonitorMain getMonitorMain(Room room) {
        switch (room.getPlatform()) {
            case DouYin:
                return new DouYinMain(setting);
            case Bili:
                return new BiliMain(setting);
            case KuaiShou:
                return new KuaiShouMain(setting);
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
            case KuaiShou:
                return new KuaiShouRoom(id);
            default:
                throw new IllegalArgumentException("参数错误platform");
        }
    }

    @Override
    protected Room.Platform[] supportedPlatforms() {
        return Room.Platform.values();
    }
}