package cn.zhangheng.douyin.subtitle;

import com.zhangheng.file.FileUtil;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 21:27
 * @version: 1.0
 * @description:
 */
public class SrtGenerator extends SubtitleGenerator {


    protected SrtGenerator(int subtitleDurationSec, String inputPath) {
        super(subtitleDurationSec, inputPath,
                inputPath.substring(0, inputPath.lastIndexOf(".")) + ".srt",
                FileUtil.getName(inputPath).substring(0, 19)
        );
    }


    public static void main(String[] args) throws IOException {
        String log = "F:\\Git Project\\LiveMonitoringRecording\\【星曦向荣】直播监听工具\\抖音\\[电影频道央影传媒]\\2026-05-10\\2026-05-10 16-57-14聊天弹幕.log";
//        System.out.println(startTime);
        SrtGenerator srtGenerator = new SrtGenerator(10, log);
        srtGenerator.generate();
    }


    /**
     * 毫秒 → SRT 时间格式 HH:mm:ss,SSS
     */
    protected String formatMsToSrt(long millis) {
        return formatMsToSrt(millis, "%02d:%02d:%02d,%03d");
    }

    @Override
    protected void writeSubtitle(TreeMap<Long, List<String>> secondGroupMap, BufferedWriter bw) throws IOException {
        // 写入 SRT 文件
        int index = 1;
        for (Long offsetSecond : secondGroupMap.keySet()) {
            List<String> contents = secondGroupMap.get(offsetSecond);
            String stackText = String.join("\n", contents);
            long startMs = offsetSecond * 1000;
            long endMs = startMs + (long) subtitleDurationSec * 1000;
            write(bw, index++, startMs, endMs, stackText);
        }
    }

    /**
     * 写入单条 SRT 字幕
     */
    private void write(BufferedWriter bw, int index, long startMs, long endMs, String text) throws IOException {
        bw.write(index + "");
        bw.newLine();
        bw.write(formatMsToSrt(startMs) + " --> " + formatMsToSrt(endMs));
        bw.newLine();
        bw.write(text);
        bw.newLine();
        bw.newLine();
    }
}
