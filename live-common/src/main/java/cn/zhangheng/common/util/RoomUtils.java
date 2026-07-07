package cn.zhangheng.common.util;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.TimeUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/07 星期二 11:26
 * @version: 1.0
 * @description:
 */
public class RoomUtils {

    public static String getBasePathStr(Room room) {
        return Constant.Application + "/" + room.getPlatform().getName() + "/" + FileUtil.filterFileName(room.getNickname()) + "[" + room.getId() + "]";
    }


    public static String getBaseSavePathStr(Room room) {
        if (room.getStartTime() != null) {
            String nowTime = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd");
            return getBasePathStr(room) + "/" + nowTime;
        } else {
            return getBasePathStr(room);
        }
    }
}
