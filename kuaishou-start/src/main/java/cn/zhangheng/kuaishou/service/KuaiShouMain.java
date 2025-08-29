package cn.zhangheng.kuaishou.service;

import cn.zhangheng.common.bean.MonitorMain;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import com.zhangheng.util.ThrowableUtil;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 19:12
 * @version: 1.0
 * @description:
 */
public class KuaiShouMain extends MonitorMain<KuaiShouRoom, KuaiShouMonitor> {
    public KuaiShouMain(Setting setting) {
        super(setting);
    }

    @Override
    protected KuaiShouMonitor getRoomMonitor(KuaiShouRoom room) {
        return new KuaiShouMonitor(delayIntervalSec, room);
    }

    @Override
    protected String statistics(LogUtil logUtil, KuaiShouRoom room) {
        if (room.isLiving()) {
            String info = "粉丝数：" + room.getFollowers() + "，点赞数：" + room.getLikeCount();
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
