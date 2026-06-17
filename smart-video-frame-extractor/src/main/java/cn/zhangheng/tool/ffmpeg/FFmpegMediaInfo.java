package cn.zhangheng.tool.ffmpeg;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.util.DateTimeUtil;
import cn.zhangheng.common.video.ffmpeg.FFmpegService;
import cn.zhangheng.tool.bean.MediaInfo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/12 星期五 05:10
 * @version: 1.0
 * @description:
 */
public class FFmpegMediaInfo extends FFmpegService {
    private final MediaInfo info = new MediaInfo();

    public static void main(String[] args) throws Exception {
        String path1 = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[兰小美]\\2025-08-22\\【兰小美】抖音直播录制2025-08-22 23-56-11[兰小美正在直播].mp4";
        FFmpegMediaInfo fFmpegMediaInfo = new FFmpegMediaInfo();
        long sta = System.currentTimeMillis();
        System.out.println(fFmpegMediaInfo.getInfo(path1));
        System.out.println(System.currentTimeMillis() - sta);
    }

    public FFmpegMediaInfo() {
        this(Constant.FFmpegExePath);
    }

    public FFmpegMediaInfo(String ffmpegExePath) {
        super(ffmpegExePath, true);
    }

    public MediaInfo getInfo(String videoPath) throws IOException, InterruptedException {
        List<String> list = Arrays.asList(
                "-i", videoPath,
                "-frames:v", "1",
                "-f", "rawvideo",
                "-y",
                "NUL"
        );
        run(list);
        return info;
    }

    @Override
    protected void processResult(String logs) {
        if (logs.contains("Duration:")) {
//            System.out.println(logs);
            String time = logs.substring(logs.indexOf("Duration:") + 9, logs.indexOf(",")).trim();
            String bitrate = logs.substring(logs.indexOf("bitrate:") + 8).trim();
            info.setDurationMs(DateTimeUtil.hmsToMsFast(time));
            info.setBitrate(bitrate);
        } else if (logs.contains("Stream #0:0[0x1](und):")) {
            String[] split = logs.split(", ");
            for (int i = 0; i < split.length; i++) {
                if (i < 2) continue;
                String trim = split[i].trim();
                if (trim.contains("x")) {
                    String[] xes = trim.split("x");
                    info.setWidth(Integer.parseInt(xes[0]));
                    info.setHeight(Integer.parseInt(xes[1]));
                } else if (trim.contains("fps")) {
                    String fpsStr = trim.substring(0, trim.indexOf("fps")).trim();
                    info.setFps(Double.parseDouble(fpsStr));
                }

            }
//            System.out.println(logs);
        }
    }
}
