package cn.zhangheng.douyin;

import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.util.SettingUtil;
import com.zhangheng.util.ThrowableUtil;

import java.io.IOException;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 04:50
 * @version: 1.0
 * @description:
 */
public class DouYinMain extends MonitorMain<DouYinRoom, DouYinRoomMonitor> {

    public DouYinMain(Setting setting) {
        super(setting);
    }

    @Override
    protected DouYinRoomMonitor getRoomMonitor(DouYinRoom room) {
        return new DouYinRoomMonitor(delayIntervalSec, room);
    }

    @Override
    protected String statistics(LogUtil logUtil, DouYinRoom room) {
        if (room.isLiving()) {
            String info = "在线人数：" + room.getUserCountStr() + "，点赞数：" + room.getLikeCount() + "，总观看人数：" + room.getTotalUserStr();
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
