package cn.zhangheng.douyin.subtitle;

import cn.hutool.core.util.StrUtil;
import com.zhangheng.file.FileUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.TreeMap;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 22:04
 * @version: 1.0
 * @description:
 */
public class AssGenerator extends SubtitleGenerator {
    private int x = 1080, y = 1920;


    public static void main(String[] args) throws IOException {
        String log = "F:\\Git Project\\LiveMonitoringRecording\\【星曦向荣】直播监听工具\\抖音\\[超级喜欢uu子]\\2026-05-11\\2026-05-11 00-08-33聊天弹幕.log";

        AssGenerator generator = new AssGenerator(10, log);
        generator.generate();
    }

    public AssGenerator(int subtitleDurationSec, String inputPath) {
        super(subtitleDurationSec, inputPath,
                inputPath.substring(0, inputPath.lastIndexOf(".")) + ".ass",
                FileUtil.getName(inputPath).substring(0, 19)
        );
    }

    public void setXY(int x, int y) {
        this.x = x;
        this.y = y;
    }


    private String formatMsToSrt(long millis) {
        return super.formatMsToSrt(millis, "%02d:%02d:%02d.%03d");
    }

    @Override
    protected void writeSubtitle(TreeMap<Long, List<String>> secondGroupMap, BufferedWriter bw) throws IOException {
        bw.write(getHeader());
        bw.newLine();
        int i = 0;
        int[] indexes = {0,2,4,1,3};
        for (Long offsetSecond : secondGroupMap.keySet()) {
            List<String> contents = secondGroupMap.get(offsetSecond);
            String stackText = String.join("\\N", contents);
            long startMs = offsetSecond * 1000;
            long endMs = startMs + (long) subtitleDurationSec * 1000;
            ;
            String format = StrUtil.format("Dialogue: 0,{},{},TopDanmu{},,0,0,0,Banner;6;0;0,{}",
                    formatMsToSrt(startMs),
                    formatMsToSrt(endMs),
                    indexes[i++ % 5],
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
                "Style: TopDanmu0,微软雅黑,40,&H50FFFFFF,&H00000000,&H00000000,&H00000000,1,0,0,0,100,100,0,0,0,0,0,7,0,0,80,1\n" +
                "Style: TopDanmu1,微软雅黑,40,&H50FFFFFF,&H00000000,&H00000000,&H00000000,1,0,0,0,100,100,0,0,0,0,0,7,0,0,120,1\n" +
                "Style: TopDanmu2,微软雅黑,40,&H50FFFFFF,&H00000000,&H00000000,&H00000000,1,0,0,0,100,100,0,0,0,0,0,7,0,0,160,1\n" +
                "Style: TopDanmu3,微软雅黑,40,&H50FFFFFF,&H00000000,&H00000000,&H00000000,1,0,0,0,100,100,0,0,0,0,0,7,0,0,200,1\n" +
                "Style: TopDanmu4,微软雅黑,40,&H50FFFFFF,&H00000000,&H00000000,&H00000000,1,0,0,0,100,100,0,0,0,0,0,7,0,0,240,1\n" +
                "\n" +
                "[Events]\n" +
                "Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text";
    }
}
