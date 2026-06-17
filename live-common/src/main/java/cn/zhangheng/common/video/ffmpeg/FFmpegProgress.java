package cn.zhangheng.common.video.ffmpeg;

import com.zhangheng.file.FileUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/12 星期五 01:24
 * @version: 1.0
 * @description:
 */
public class FFmpegProgress {
    @Getter
    private long frame = -1;       // 帧号
    @Getter
    private float fps = -1f;        // 帧率

    private long sizeKb  = -1;      // 大小 (KB)
    // 对外获取字节大小，原始存储KB，解耦换算逻辑
    public long getSizeByte() {
        return sizeKb < 0 ? sizeKb : sizeKb * 1024L;
    }

    @Getter
    private long timeMs = -1;      // 时间 (毫秒)
    @Getter
    private float bitrate = -1f;    // 比特率 (kbits/s)
    @Getter
    private float speed = -1f;      // 处理速度 (x)
    @Getter
    private long elapsedMs = -1; //耗时(毫秒)
    // 定义正则表达式模式
    private static final Pattern FRAME_PATTERN = Pattern.compile("frame=\\s*(\\d+)");
    private static final Pattern FPS_PATTERN = Pattern.compile("fps=\\s*(\\d+\\.?\\d*)");
    private static final Pattern SIZE_PATTERN = Pattern.compile("size=\\s*(\\d+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d\\d):(\\d\\d):(\\d\\d\\.\\d+)");
    private static final Pattern ELAPSED_PATTERN = Pattern.compile("elapsed=(\\d{1,2}):(\\d{2}):(\\d\\d\\.\\d+)");
    private static final Pattern BITRATE_PATTERN = Pattern.compile("bitrate=\\s*(\\d+\\.?\\d*)kbits/s");
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*(\\d+\\.?\\d*)x");
    // 全局复用一个Matcher，避免频繁创建销毁
    private final Matcher globalMatcher = Pattern.compile("").matcher("");
    public void parse(String line) {
        if (line == null || !line.contains("frame=")) {
            return;
        }
        // 解析 frame
        frame = matchLong(FRAME_PATTERN, line, -1);

        // 解析 fps
        fps = matchFloat(FPS_PATTERN, line, -1f);

        // 解析 size (B)
        sizeKb = matchLong(SIZE_PATTERN, line, -1);

        // 解析 time (毫秒)
        timeMs = matchTime(TIME_PATTERN, line,-1);

        // 解析 bitrate
        bitrate = matchFloat(BITRATE_PATTERN, line, -1f);

        // 解析 speed
        speed = matchFloat(SPEED_PATTERN, line, -1f);
        // 解析 elapsed (毫秒)
        elapsedMs = matchTime(ELAPSED_PATTERN,line,-1);
    }

    /** 复用matcher解析时分秒时间 */
    private long matchTime(Pattern pattern, String line,long defVal) {
        globalMatcher.usePattern(pattern).reset(line);
        if (!globalMatcher.find()) {
            return defVal;
        }
        try {
            int h = Integer.parseInt(globalMatcher.group(1));
            int m = Integer.parseInt(globalMatcher.group(2));
            double s = Double.parseDouble(globalMatcher.group(3));
            double totalSec = h * 3600L + m * 60L + s;
            // 四舍五入消除浮点精度丢失
            return Math.round(totalSec * 1000);
        } catch (Exception e) {
            return defVal;
        }
    }
    /** 复用matcher匹配float */
    private float matchFloat(Pattern pattern, String line, float defVal) {
        globalMatcher.usePattern(pattern).reset(line);
        if (globalMatcher.find()) {
            String numStr = globalMatcher.group(1);
            return safeParseFloat(numStr, defVal);
        }
        return defVal;
    }
    /** 复用matcher匹配long */
    private long matchLong(Pattern pattern, String line, long defVal) {
        globalMatcher.usePattern(pattern).reset(line);
        if (globalMatcher.find()) {
            String numStr = globalMatcher.group(1);
            return safeParseLong(numStr, defVal);
        }
        return defVal;
    }
    // 安全数字解析，防止日志格式错乱抛异常
    private long safeParseLong(String val, long def) {
        if (val == null || val.isEmpty()) return def;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
    private float safeParseFloat(String val, float def) {
        if (val == null || val.isEmpty()) return def;
        try {
            return Float.parseFloat(val.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    // 重置所有字段，实例可循环复用，减少new对象开销
    public void reset() {
        frame = -1;
        fps = -1f;
        sizeKb = -1;
        timeMs = -1;
        bitrate = -1f;
        speed = -1f;
        elapsedMs = -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FFmpeg进度 = ");
        if (timeMs>0) sb.append("时间:").append(TimeUtil.formatMS((int) timeMs)).append(", ");
        if (elapsedMs>0) sb.append("耗时:").append(TimeUtil.formatMS((int) elapsedMs)).append(", ");
        if (frame>0) sb.append("帧号:").append(frame).append(", ");
        if (fps>0) sb.append("帧率:").append(fps).append(", ");
        if (sizeKb>0) sb.append("大小:").append(FileUtil.fileSizeStr(getSizeByte())).append(", ");
        if (bitrate>0) sb.append("比特率:").append(bitrate).append("kbits/s, ");
        if (speed>0) sb.append("速度:").append(speed).append("x");

        return sb.toString();
    }
}
