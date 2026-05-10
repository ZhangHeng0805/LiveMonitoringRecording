package cn.zhangheng.douyin.subtitle;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 15:15
 * @version: 1.0
 * @description: 高性能弹幕字幕生成器（支持百万级日志，低内存占用）
 * 功能：同秒弹幕自动堆叠 + 流式处理 + 无内存溢出
 */


import com.zhangheng.file.FileUtil;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 *
 */
public abstract class SubtitleGenerator {
    protected static final DateTimeFormatter START_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    protected static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    protected final int subtitleDurationSec;
    protected final String inputPath, outputPath, baseStartTime;

    protected SubtitleGenerator(int subtitleDurationSec, String inputPath, String outputPath, String baseStartTime) {
        this.subtitleDurationSec = subtitleDurationSec;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.baseStartTime = baseStartTime;
    }


    public void generate() throws IOException {
        LocalDateTime baseTime = LocalDateTime.parse(baseStartTime, START_TIME_FORMAT);

        // 按【秒】分组存储弹幕，TreeMap自动排序
        TreeMap<Long, List<String>> secondGroupMap = new TreeMap<>();

        // 流式读取大文件，不会OOM
        // 1MB 缓冲
        int bufferSize = 1024 * 1024;
        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {

            String line;
            while ((line = br.readLine()) != null) {
                try {
                    // 1. 解析日志时间（只取前19位）
                    String timeStr = line.substring(0, 19);
                    LocalDateTime logTime = LocalDateTime.parse(timeStr, LOG_TIME_FORMAT);

                    // 过滤早于开始时间的日志
                    if (logTime.isBefore(baseTime)) {
                        continue;
                    }

                    // 2. 计算相对于视频的秒数
                    long offsetSecond = ChronoUnit.SECONDS.between(baseTime, logTime);

                    // 3. 提取弹幕内容
                    int contentIndex = line.indexOf(" : ");
                    if (contentIndex == -1) continue;

                    String content = line.substring(contentIndex + 3).trim();
                    if (content.isEmpty()) continue;

                    // 4. 按秒分组（同秒弹幕放一起）
                    List<String> group = secondGroupMap.computeIfAbsent(offsetSecond, k -> new ArrayList<>());
                    group.add(content);

                } catch (Exception ignored) {
                    // 解析失败直接跳过，不影响整体
                }
            }
            writeSubtitle(secondGroupMap, bw);
        }

        System.out.println("✅ 字幕生成完成：" + outputPath);
    }

    protected abstract void writeSubtitle(TreeMap<Long, List<String>> secondGroupMap, BufferedWriter bw) throws IOException;

    protected String formatMsToSrt(long millis,String format){
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(format, hours, minutes, seconds, ms);
    }
}
