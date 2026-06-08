package cn.zhangheng.tool;

import com.zhangheng.util.TimeUtil;
import lombok.Data;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/08 星期一 14:08
 * @version: 1.0
 * @description:
 */
@Data
public class AppLog implements Serializable {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TimeUtil.EnDateFormat);

    // 日志时间（带时区）
    private ZonedDateTime time;

    // 日志级别 info/warn/error
    private String level;

    // 日志内容
    private String msg;

    // 空构造
    public AppLog() {
    }

    // 全参构造
    public AppLog(ZonedDateTime time, String level, String msg) {
        this.time = time;
        this.level = level;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return time.format(formatter) +
                " [" + level + "] " +
                " = " + msg;
    }
}
