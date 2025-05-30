package cn.zhangheng.common.video;

import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/24 星期六 17:26
 * @version: 1.0
 * @description:
 */
public class FlvToMp4 {
    private final static String ffmpegExePath = "bin/ffmpeg.exe";
    private static final Logger log = LoggerFactory.getLogger(FlvToMp4.class);
    private static LogUtil logUtil = null;
    @Getter
    private boolean isRunning;

    public static void main(String[] args) {
        String input = "抖音直播监听录制/小兰花/2025-05-19/【小兰花】直播录制2025-05-19 22-53-21.flv";
        String output = "抖音直播监听录制/小兰花/2025-05-19/【小兰花】直播录制2025-05-19 22-53-21.mp4";
        convert(ffmpegExePath, input, output);
    }

    public boolean convert(String input, String output) {
        try {
            isRunning = true;
            if (logUtil == null) {
                String parent = Paths.get(input).getParent().toString();
                try {
                    logUtil = new LogUtil(Paths.get(parent, "视频转换.log").toString());
                } catch (IOException e) {
                    log.error("视频转换日志生成失败" + ThrowableUtil.getAllCauseMessage(e));
                }
            }
            return convert(ffmpegExePath, input, output);
        }finally {
            isRunning = false;
        }
    }


    public static boolean convert(String ffmpegExePath, String input, String output) {
        try {
            if (!Files.exists(Paths.get(ffmpegExePath))) {
                throw new FileNotFoundException("未找到ffmpeg.exe(Not Found ffmpeg.exe)");
            }
            if (!Files.exists(Paths.get(input))) {
                throw new FileNotFoundException("未找到输入文件(Not Found Input File):" + input);
            }
            // 构建FFmpeg命令
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegExePath,// FFmpeg可执行文件路径
                    "-i", input,// 输入文件
                    "-c:v", "copy",// 视频编码器
                    "-c:a", "copy",// 音频编码器
                    output // 输出文件
            );

            // 捕获命令输出
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出信息
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            List<String> result = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            if (logUtil != null) {
                logUtil.log(result);
            } else {
                log.info(String.join("\n", result));
            }
            // 等待命令执行完成
            int exitCode = process.waitFor();
            String exit = input + "转换结束!\nFFmpeg 退出码: " + exitCode;
            if (logUtil != null) {
                logUtil.log(exit);
            } else {
                log.info(exit);
            }
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.error("视频转换失败{}", ThrowableUtil.getAllCauseMessage(e));
        }
        return false;
    }

}
