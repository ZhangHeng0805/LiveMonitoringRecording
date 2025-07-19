package cn.zhangheng.common.record;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.video.FlvDownload;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/02 星期一 00:09
 * @version: 1.0
 * @description:
 */
public class FFmpegFlvRecorder extends Recorder {

    private final FlvDownload flvDownload;

    public FFmpegFlvRecorder(String downloadUrl, String saveFilePath, String definition) {
        this(downloadUrl, saveFilePath, definition, null);
    }

    public FFmpegFlvRecorder(String downloadUrl, String saveFilePath, String definition, String ffmpegPath) {
        super(downloadUrl, saveFilePath, definition);
        if (ffmpegPath != null) {
            flvDownload = new FlvDownload(ffmpegPath);
        } else {
            flvDownload = new FlvDownload();
        }
    }

    @Override
    public long getTimeMs() {
        return flvDownload.getFfmpegProgress().getTimeMs();
    }

    @Override
    public long getDownloadSize() {
        return flvDownload.getFfmpegProgress().getSize();
    }

    @Override
    public String getProgressMsg() {
        FlvDownload.FFmpegProgress ffmpegProgress = flvDownload.getFfmpegProgress();
        return "已录制: "
                + TimeUtil.formatMSToCn((int) ffmpegProgress.getTimeMs()) + " / "
                + FileUtil.fileSizeStr(ffmpegProgress.getSize()) + " / "
                + ffmpegProgress.getFps() + "fps / "
                + ffmpegProgress.getBitrate() + "kbits/s"
                ;
    }

    @Override
    public void download() {
        flvDownload.download(downloadUrl, saveFilePath, room.getRequestHead());
    }

    @Override
    public void stop(boolean force) {
        super.stop(force);
        flvDownload.stop(force);
    }

    @Override
    public boolean isRunning() {
        return super.isRunning() && flvDownload.isRunning();
    }

    // 示例用法
    public static void main(String[] args) {
        String flvUrl = "https://pull-flv-l26.douyincdn.com/stage/stream-7510992371950078760_or4.flv?arch_hrchy=h1&exp_hrchy=h1&expire=6845c418&major_anchor_level=common&sign=a3f11bfb3a0f7da42112bc72bf92c2ef&t_id=037-20250602011047DE5DAE6B3E4CC10A3E24-rqU2Dx&unique_id=stream-7510992371950078760_139_flv_or4&abr_pts=-800";
        String outputPath = TimeUtil.getNowUnix() + ".flv";

        try {
            System.out.println("开始录制FLV流...");
            Recorder flvStreamRecorder = new FFmpegFlvRecorder(flvUrl, outputPath, "原画");
            flvStreamRecorder.setTimeoutSeconds(60);
            flvStreamRecorder.setProgressCallback(new Recorder.ProgressCallback() {
            });
            flvStreamRecorder.run(false);
        } catch (Exception e) {
            System.err.println("录制过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
