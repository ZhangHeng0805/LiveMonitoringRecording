package cn.zhangheng.douyin.service;

import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.record.service.RoomService;
import cn.zhangheng.douyin.bean.DouYinRoom;

import java.util.Objects;

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
        PlaywrightBrowserFactory.setHeadless(!Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE));
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

    /**
     * 手动开启弹幕录制：不改房间设置，仅本次直播内生效
     */
    @Override
    public void forceStartSubtitle() {
        subtitleService.startSubtitle(true);
    }

    @Override
    public void stopSubtitle() {
        subtitleService.stopSubtitle();
    }

    @Override
    public boolean isOpenSubtitle() {
        return subtitleService.isRunning();
    }
}
