package cn.zhangheng.tool.ffmpeg;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.video.ffmpeg.FFmpegProgress;
import cn.zhangheng.common.video.ffmpeg.FFmpegService;
import cn.zhangheng.tool.bean.MediaInfo;
import com.zhangheng.util.TimeUtil;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/11 星期四 23:43
 * @version: 1.0
 * @description:
 */
public class FFmpegExtractRawFrame extends FFmpegService {
    @Setter
    private int frameStepSecond = 2;
    @Setter
    private int thumbnail = 2;
    private final String tempDir;
    private final FFmpegProgress progress = new FFmpegProgress();

    public FFmpegExtractRawFrame(String tempDir) {
        this(Constant.FFmpegExePath, tempDir);
    }

    public FFmpegExtractRawFrame(String ffmpegExePath, String tempDir) {
        super(ffmpegExePath);
        this.tempDir = tempDir;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isRunning()) {
                //程序关闭，自动关闭浏览器
                log.debug("程序关闭，自动关闭FFmpeg视频帧提取");
                stop(true);
            }
        }));
    }

    public boolean run(String video) throws IOException, InterruptedException {
        String framePattern = tempDir + File.separator + "raw_%06d.jpg";
        return run(video, framePattern);
    }

    public boolean run(String video, String framePattern) throws IOException, InterruptedException {
        if (frameStepSecond < 1) {
            MediaInfo info = new FFmpegMediaInfo().getInfo(video);
            log.info(info.toString());
            if (info.getDurationMs() > 1_000_000) {
                frameStepSecond = (int) (info.getDurationMs() / 1_000_000);
                log.info("根据视频文件时长自动设置frameStepSecond={}", frameStepSecond);
            } else if (info.getDurationMs() > 60_000) {
                frameStepSecond = 2;
            } else {
                frameStepSecond = 1;
            }
        }
        List<String> list = Arrays.asList(
                "-y",
                "-hide_banner",
                "-i",
                video,
                "-vf",
                "blackdetect=black_min_duration=0.3:picture_black_ratio_th=0.9,fps=1/" + frameStepSecond + ",thumbnail=n=" + thumbnail,
                "-q:v",
                "0",
                framePattern
        );
        long sta = System.currentTimeMillis();
        int code = run(list);
        if (code != 0) {
            System.err.println("FFmpeg抽帧失败，退出码:" + code);
            return false;
        }
        System.out.println("✅ FFmpeg 原始帧抽取完成！耗时:" + TimeUtil.formatMS((int) (System.currentTimeMillis() - sta)));
        return true;
    }


    int lines = 0;

    @Override
    protected void processResult(String logs) {
        lines++;
        progress.parse(logs);
        if (lines % 10 == 0) {
            String s = progress.toString();
            if (s.length() > 20)
                System.out.println(s);
        }
    }
}
