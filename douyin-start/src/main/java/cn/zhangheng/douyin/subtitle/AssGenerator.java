package cn.zhangheng.douyin.subtitle;

import cn.hutool.core.util.StrUtil;
import com.zhangheng.file.FileUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 22:04
 * @version: 1.0
 * @description:
 */
public class AssGenerator extends SubtitleGenerator {
    private int x = 1080, y = 1920, fontSize = 48;



    public static void main(String[] args) throws IOException {
        String log = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2026-05-12\\2026-05-12 22-09-28聊天弹幕.log";
        String out = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2026-05-12\\2026-05-12 23-36-48聊天弹幕.ass";

        AssGenerator generator = new AssGenerator(10, log, out, "2026-05-12 23-36-48");
//        AssGenerator generator = new AssGenerator(10, log);
//        generator.setXY(1920,1080);
        generator.generate();
    }

    public AssGenerator(int subtitleDurationSec, String inputPath) {
        super(subtitleDurationSec, inputPath,
                inputPath.substring(0, inputPath.lastIndexOf(".")) + ".ass",
                FileUtil.getName(inputPath).substring(0, 19)
        );
    }

    protected AssGenerator(int subtitleDurationSec, String inputPath, String outputPath, String baseStartTime) {
        super(subtitleDurationSec, inputPath, outputPath, baseStartTime);
    }

    public void setXY(int x, int y) {
        this.x = x;
        this.y = y;
        if (x > y) {
            fontSize = 30;
        } else {
            fontSize = 48;
        }
    }


    private String formatMsToSrt(long millis) {
        return super.formatMsToSrt(millis, "%02d:%02d:%02d.%03d");
    }

    @Override
    protected void writeSubtitle(TreeMap<Long, List<String>> secondGroupMap, BufferedWriter bw) throws IOException {
        bw.write(getHeader());
        bw.newLine();
        int i = 0;
        int[] indexes = {0,2,4,1,3,5};
        for (Long offsetSecond : secondGroupMap.keySet()) {
            List<String> contents = secondGroupMap.get(offsetSecond);
            String stackText = String.join("\\N", contents);
            long startMs = offsetSecond * 1000;
            List<String> list = Arrays.asList(stackText.split("\\\\N"));
            int textLength = list.stream().mapToInt(String::length).max().orElse(0);
            int speed, add;
            if (textLength < 6) {
                speed = 6;
                add = 0;
            } else if (textLength < 12) {
                speed = 8;
                add = 4;
            } else if (textLength < 20) {
                speed = 10;
                add = 8;
            } else {
                speed = 10;
                add = 12;
            }
            long endMs = startMs + (long) (subtitleDurationSec + add) * 1000;
            String format = StrUtil.format("Dialogue: 0,{},{},TopDanmu{},,0,0,0,Banner;{};0;0,{}",
                    formatMsToSrt(startMs),
                    formatMsToSrt(endMs),
                    indexes[i++ % 6],
                    speed,
                    stackText
            );
            bw.write(format);
            bw.newLine();
        }
    }

    private String getHeader() {
        return "[Script Info]\n" +
                "Title: 顶部飘动弹幕\n" +
                "ScriptType: v4.00+\n" +
                "PlayResX: " + x + "\n" +
                "PlayResY: " + y + "\n" +
                "Timer: 100.00\n" +
                "\n" +
                "[V4+ Styles]\n" +
                "Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\n" +
                "Style: TopDanmu0,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,50,1\n" +
                "Style: TopDanmu1,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,100,1\n" +
                "Style: TopDanmu2,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,150,1\n" +
                "Style: TopDanmu3,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,200,1\n" +
                "Style: TopDanmu4,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,250,1\n" +
                "Style: TopDanmu5,微软雅黑," + fontSize + ",&H50FFFFFF,&H00000000,&H80000000,&H00000000,1,0,0,0,100,100,0,0,0,1,0,7,0,0,300,1\n" +
                "\n" +
                "[Events]\n" +
                "Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text";
    }
}
