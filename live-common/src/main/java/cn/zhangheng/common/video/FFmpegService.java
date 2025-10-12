package cn.zhangheng.common.video;

import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/01 星期日 12:06
 * @version: 1.0
 * @description: FFmpeg抽象类
 */
public abstract class FFmpegService {
    protected static final Logger log = LoggerFactory.getLogger(FFmpegService.class);
    protected Process process;
    @Getter
    private volatile boolean isRunning;
    protected final String ffmpegExePath;
    private final List<String> commands;

    public FFmpegService(String ffmpegExePath) {
        if (!Files.exists(Paths.get(ffmpegExePath))) {
            throw new IllegalArgumentException("未找到" + ffmpegExePath + "(Not Found ffmpeg.exe)");
        }
        this.ffmpegExePath = ffmpegExePath;
        commands = new ArrayList<>();
        commands.add(this.ffmpegExePath);
    }


    protected boolean run(List<String> command) throws IOException, InterruptedException {
        try {
            isRunning = true;
            commands.addAll(command);
            ProcessBuilder pb = new ProcessBuilder(commands);
            String ffmpegCommand = "ffmpeg command: " + String.join(" ", pb.command());
            log.info(ffmpegCommand);
            processResult(TimeUtil.getNowTime() + " - " + ffmpegCommand);
            // 捕获命令输出
            pb.redirectErrorStream(true);
            process = pb.start();
            processResult(process);
            int exitCode = process.waitFor();
            return exitCode == 0;
        } finally {
            isRunning = false;
        }
    }

    public void stop(boolean force) {
        isRunning = false;
        if (process != null && process.isAlive()) {
            if (force) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
            }
        }
    }


    /**
     * 输出每行日志结果
     *
     * @param logs
     */
    protected abstract void processResult(String logs);

    private void processResult(Process process) throws IOException {
        // 读取输出信息
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processResult(line);
            }
        }
    }


}
