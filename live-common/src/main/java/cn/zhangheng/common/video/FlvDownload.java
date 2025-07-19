package cn.zhangheng.common.video;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/01 星期日 14:15
 * @version: 1.0
 * @description: FLV视频下载
 */
public class FlvDownload extends FFmpegService {
    private LogUtil logUtil = null;
    @Getter
    private final FFmpegProgress ffmpegProgress = new FFmpegProgress();

    public FlvDownload() {
        super(Constant.FFmpegExePath);
    }

    public FlvDownload(String ffmpegPath) {
        super(ffmpegPath);
    }

    public void download(String url, String file, Map<String, String> headers) {
        if (logUtil == null) {
            String parent = Paths.get(Paths.get(file).toFile().getAbsolutePath()).getParent().toString();
            try {
                logUtil = new LogUtil(Paths.get(parent, "视频下载.log").toString());
            } catch (IOException e) {
                log.error("视频下载日志生成失败" + ThrowableUtil.getAllCauseMessage(e));
            }
        }
        try {
            List<String> commands = new ArrayList<>();
            List<String> baseList = Arrays.asList(
                    "-y",
//                "-re",
//                "-reconnect_at_eof", "1",
                    "-reconnect", "1",
                    "-reconnect_streamed", "1",
//                    "-reconnect_max", "10",
                    "-reconnect_delay_max", "5",
                    "-timeout", "5000000",  // 单位微秒，5秒后未建立连接则超时
                    "-err_detect", "ignore_err", // 忽略部分编码错误
//                    "-fflags", "+igndts +discardcorrupt", // 忽略错误时间戳，丢弃损坏帧
                    "-fflags", "+igndts+genpts",  // 忽略错误时间戳，生成连续新时间戳
                    "-max_delay", "500000", // 最大延迟500ms，给足时间等待乱序帧
                    "-i", "\"" + url + "\"",
                    "-c:v", "copy",
                    "-c:a", "copy",
                    "-probesize", "32M",
//                    "-rw_timeout", "20000000",
                    file
            );
            if (headers != null && !headers.isEmpty()) {
                List<String> headerList = headers.entrySet().stream().map(h -> h.getKey() + ": " + h.getValue()).collect(Collectors.toList());
                commands.add("-headers");
                String join = String.join("\r\n", headerList);
                commands.add("\"" + join + "\r\n\"");
            }
            commands.addAll(baseList);
            run(commands);
        } catch (InterruptedException e) {
            log.error("视频下载失败: {}", ThrowableUtil.getAllCauseMessage(e));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (logUtil != null) {
                logUtil.log("下载结束！" + file);
                logUtil.log(ffmpegProgress.toString());
                logUtil.close();
            }
        }
    }

    @Override
    protected void processResult(String logs) {
        if (logs.startsWith("frame=")) {
            ffmpegProgress.parse(logs);
        } else {
            if (logUtil != null) logUtil.highLog(logs);
        }
    }


    public static class FFmpegProgress {
        @Getter
        private long frame;       // 帧号
        @Getter
        private float fps;        // 帧率
        @Getter
        private long size;      // 大小 (B)
        @Getter
        private long timeMs;      // 时间 (毫秒)
        @Getter
        private float bitrate;    // 比特率 (kbits/s)
        @Getter
        private float speed;      // 处理速度 (x)
        // 定义正则表达式模式
        private static final Pattern FRAME_PATTERN = Pattern.compile("frame=\\s*(\\d+)");
        private static final Pattern FPS_PATTERN = Pattern.compile("fps=\\s*(\\d+\\.?\\d*)");
        private static final Pattern SIZE_PATTERN = Pattern.compile("size=\\s*(\\d+)kB");
        private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d)");
        private static final Pattern BITRATE_PATTERN = Pattern.compile("bitrate=\\s*(\\d+\\.?\\d*)kbits/s");
        private static final Pattern SPEED_PATTERN = Pattern.compile("speed=\\s*(\\d+\\.?\\d*)x");

        public void parse(String line) {
            if (line == null || !line.contains("frame=")) {
                return;
            }


            // 解析 frame
            frame = parseLong(FRAME_PATTERN, line);

            // 解析 fps
            fps = parseFloat(FPS_PATTERN, line);

            // 解析 size (B)
            size = parseLong(SIZE_PATTERN, line) * 1024L;

            // 解析 time (毫秒)
            Matcher timeMatcher = TIME_PATTERN.matcher(line);
            if (timeMatcher.find()) {
                int hours = Integer.parseInt(timeMatcher.group(1));
                int minutes = Integer.parseInt(timeMatcher.group(2));
                double seconds = Double.parseDouble(timeMatcher.group(3));
                timeMs = (long) ((hours * 3600L + minutes * 60L + seconds) * 1000);
            }

            // 解析 bitrate
            bitrate = parseFloat(BITRATE_PATTERN, line);

            // 解析 speed
            speed = parseFloat(SPEED_PATTERN, line);
        }

        @Override
        public String toString() {
            return "已录制：" +
                    "帧号: " + frame +
                    ", 大小=" + FileUtil.fileSizeStr(size) +
                    ", 时间=" + TimeUtil.formatMSToCn((int) timeMs) +
                    ", 比特率=" + bitrate + "kbits/s" +
                    ", 帧率: " + fps +
                    ", 速度=" + speed + "x";
        }

        private long parseLong(Pattern pattern, String line) {
            Matcher matcher = pattern.matcher(line);
            return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
        }

        private float parseFloat(Pattern pattern, String line) {
            Matcher matcher = pattern.matcher(line);
            return matcher.find() ? Float.parseFloat(matcher.group(1)) : -1;
        }
    }
}
