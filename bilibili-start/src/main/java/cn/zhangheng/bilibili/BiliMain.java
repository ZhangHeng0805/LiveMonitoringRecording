package cn.zhangheng.bilibili;

import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.bilibili.service.BiliMonitor;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.util.ThrowableUtil;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 22:30
 * @version: 1.0
 * @description:
 */
public class BiliMain extends MonitorMain<BiliRoom, BiliMonitor> {


    public BiliMain(Setting setting) {
        super(setting);
    }

    @Override
    protected BiliMonitor getRoomMonitor(BiliRoom room) {
        return new BiliMonitor(room);
    }

    @Override
    protected String statistics(LogUtil logUtil, BiliRoom room) {
        if (room.isLiving()) {
            String info = "粉丝数：" + room.getFollowers() + "，观看人数：" + room.getViewers();
            try {
                logUtil.log(info);
            } catch (Exception e) {
                log.warn("{} #统计日志产生异常：{}", info, ThrowableUtil.getAllCauseMessage(e));
            }
            return info;
        }
        return "";
    }
}
