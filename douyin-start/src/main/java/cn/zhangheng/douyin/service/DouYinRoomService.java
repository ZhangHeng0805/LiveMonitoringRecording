package cn.zhangheng.douyin.service;

import cn.zhangheng.record.service.RoomService;
import cn.zhangheng.douyin.bean.DouYinRoom;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 03:01
 * @version: 1.0
 * @description:
 */
public class DouYinRoomService extends RoomService<DouYinRoom> {

    private final DouYinDataService dataService;
    private final DouYinSubtitleService subtitleService;


    public DouYinRoomService(DouYinRoom room) {
        super(room);
        this.dataService = new DouYinDataService(room);
        this.subtitleService = new DouYinSubtitleService(room);
    }


    @Override
    protected boolean refresh(boolean force) {
        return dataService.refresh(force);
    }

    @Override
    public void startSubtitle() {
        subtitleService.startSubtitle();
    }

    @Override
    public synchronized void stopSubtitle() {
        subtitleService.stopSubtitle();
    }

    @Override
    public boolean isOpenSubtitle() {
        return room.isSubtitleRunning();
    }
}
