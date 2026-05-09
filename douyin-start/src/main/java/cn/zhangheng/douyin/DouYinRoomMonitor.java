package cn.zhangheng.douyin;

import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.common.service.RoomMonitor;

import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.TARGET_REQUEST_PREFIX;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/23 星期五 15:30
 * @version: 1.0
 * @description:
 */
public class DouYinRoomMonitor extends RoomMonitor<DouYinRoom,DouYinRoomService> {

    public DouYinRoomMonitor(DouYinRoom room) {
        super(room);
    }

    @Override
    protected DouYinRoomService getRoomService(DouYinRoom room) {
        room.setBrowserApi(new BrowserAPI(TARGET_REQUEST_PREFIX));
        return new DouYinRoomService(room);
    }

}
