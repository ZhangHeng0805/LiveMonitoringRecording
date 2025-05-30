package cn.zhangheng.bilibili;

import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.bilibili.service.BiliMonitor;
import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.util.SettingUtil;
import com.zhangheng.util.ThrowableUtil;

import java.io.IOException;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 22:30
 * @version: 1.0
 * @description:
 */
public class BiliMain extends MonitorMain<BiliRoom, BiliMonitor> {


    public BiliMain(SettingUtil setting) {
        super(setting);
    }

    @Override
    protected BiliMonitor getRoomMonitor(BiliRoom room) {
        return new BiliMonitor(delayIntervalSec, room);
    }

    @Override
    protected String statistics(LogUtil logUtil, BiliRoom room) {
        if (room.isLiving()) {
            String info = "关注数：" + room.getFollowers() + "，观看人数：" + room.getViewers();
            try {
                logUtil.log(info);
            } catch (IOException e) {
                log.warn("统计日志产生异常：{}", ThrowableUtil.getAllCauseMessage(e));
            }
            return info;
        }
        return "";
    }
}
