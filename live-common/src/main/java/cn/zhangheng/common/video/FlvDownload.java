package cn.zhangheng.common.video;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.common.video.ffmpeg.FFmpegProgress;
import cn.zhangheng.common.video.ffmpeg.FFmpegService;
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
        this(Constant.FFmpegExePath);
    }

    public FlvDownload(String ffmpegPath) {
        super(ffmpegPath);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isRunning()) {
                //程序关闭，自动关闭浏览器
                log.debug("程序关闭，自动关闭FFmpeg下载");
                stop(true);
            }
        }));
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
//                    "-reconnect", "1",
//                    "-reconnect_streamed", "1",
//                    "-reconnect_max", "10",
//                    "-reconnect_delay_max", "5",
//                    "-timeout", "5000000",  // 单位微秒，5秒后未建立连接则超时
//                    "-err_detect", "ignore_err", // 忽略部分编码错误
//                    "-fflags", "+igndts+genpts+nobuffer", //关键调整
//                    "-fflags", "+igndts +discardcorrupt", // 忽略错误时间戳，丢弃损坏帧
//                    "-fflags", "+igndts+genpts",  // 忽略错误时间戳，生成连续新时间戳
//                    "-max_delay", "2000000", // 最大延迟3000ms，给足时间等待乱序帧
//                    "-vsync", "vfr", // 可变帧率,控制同步方式
//                    "-f", "flv",
                    "-probesize", "32M",
                    "-rw_timeout", "15000000",
                    "-i", "\"" + url + "\"",
                    "-c:v", "copy",
                    "-c:a", "copy",
                    file
            );
            if (headers != null && !headers.isEmpty()) {
                List<String> headerList = headers.entrySet().stream().filter(h -> h.getValue().length() < 4096).map(h -> h.getKey() + ": " + h.getValue()).collect(Collectors.toList());
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
//            System.out.println(logs);
            ffmpegProgress.parse(logs);
        } else {
            if (logUtil != null) logUtil.highLog(logs);
        }
    }

}
