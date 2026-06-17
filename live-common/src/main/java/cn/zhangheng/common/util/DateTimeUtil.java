package cn.zhangheng.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/14 星期四 18:58
 * @version: 1.0
 * @description:
 */
public class DateTimeUtil {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String toTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DTF);
    }

    /**
     * 时间转换毫秒
     *
     * @param time 格式 00:02:03.15 / 00:02:03
     * @return
     */
    public static long hmsToMsFast(String time) {
        String[] hms = time.split(":");
        int h = Integer.parseInt(hms[0]);
        int m = Integer.parseInt(hms[1]);
        String secStr = hms[2];

        long secTotalMs;
        int dotIdx = secStr.indexOf('.');
        if (dotIdx == -1) {
            // 无小数，只有秒
            int sec = Integer.parseInt(secStr);
            secTotalMs = sec * 1000L;
        } else {
            // 有秒+小数毫秒
            int sec = Integer.parseInt(secStr.substring(0, dotIdx));
            String msStr = secStr.substring(dotIdx + 1);
            // 防止超长小数，只取前3位毫秒
            if (msStr.length() > 3) {
                msStr = msStr.substring(0, 3);
            }
            // 不足3位补0
            while (msStr.length() < 3) {
                msStr += "0";
            }
            int ms = Integer.parseInt(msStr);
            secTotalMs = sec * 1000L + ms;
        }

        return h * 3600_000L + m * 60_000L + secTotalMs;
    }

}
