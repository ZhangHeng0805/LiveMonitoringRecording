package cn.zhangheng.tool;

import cn.zhangheng.tool.extractor.VideoFrameExtractor;
import com.zhangheng.util.TimeUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
//        String video="http://pull-flv-gravity-l11.douyincdn.com/stage/stream-119563463939588241_or4.flv?expire=1782239328&sign=aa3d82399fefd0cc90560d15d43f126a&exp_hrchy=w1&arch_hrchy=w1&major_anchor_level=common&unique_id=stream-119563463939588241_145_flv_or4&t_id=037-2026061702284896B9F08B8C601444B5BB-FxUDdy";
        String videoSource = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[兰小美]\\2026-06-17\\【兰小美】抖音直播录制2026-06-17 00-23-14[客官，进来坐坐呀～].mp4";
//        String videoSource = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2026-06-17\\2026-06-11(2).mp4";
//        String videoSource = "F:\\视频\\抖音\\小兰花\\2025-11-16.mp4";
        Path videoPath = Paths.get(videoSource);
        Path parent = videoPath.getParent();
        String resultDir = Paths.get(parent.toString(), "video_frames").toString();
        String tempDir = Paths.get(resultDir, "temp_frames").toString();
        System.out.println(tempDir);
        System.out.println(resultDir);
        long sta = System.currentTimeMillis();
        VideoFrameExtractor extractor = new VideoFrameExtractor(videoSource, tempDir, resultDir);
        extractor.setFrameStepSecond(2);//自动根据视频时长设置
        extractor.run();
        System.out.println("总耗时：" + TimeUtil.formatMS((int) (System.currentTimeMillis() - sta)));
    }
}