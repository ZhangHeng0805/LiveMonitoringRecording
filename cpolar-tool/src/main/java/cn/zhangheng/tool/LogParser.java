package cn.zhangheng.tool;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/08 星期一 14:09
 * @version: 1.0
 * @description: 日志输出解析
 */
public class LogParser {
    // 正则表达式：匹配你的日志格式
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "time=\"([^\"]+)\"\\s+level=([^\\s]+)\\s+msg=\"([^\"]+)\""
    );

    /**
     * 把一行日志解析成 AppLog 对象
     */
    public static AppLog parseLogLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        String timeStr = matcher.group(1);
        String level = matcher.group(2);
        String msg = matcher.group(3);

        ZonedDateTime time = ZonedDateTime.parse(timeStr);

        return new AppLog(time, level, msg);
    }
}
