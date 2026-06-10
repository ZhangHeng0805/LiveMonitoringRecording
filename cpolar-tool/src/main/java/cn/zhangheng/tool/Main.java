package cn.zhangheng.tool;

import cn.hutool.json.JSONUtil;
import cn.zhangheng.tool.git.GitUtil;
import com.zhangheng.bean.Debouncer;
import com.zhangheng.file.TxtOperation;
import com.zhangheng.log.AsyncBatchLogger;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 利用cpolar端口映射工具实现内网穿透，
 * 并将映射的外网URL自动更新至git上，
 * 实现实时获取最新的映射外网URL地址
 */
public class Main {
    private static Process process = null;
    private static final Debouncer debouncer = new Debouncer(1000);
    private static final AsyncBatchLogger logger = new AsyncBatchLogger(Paths.get("logs/cpolar-run" + ".log"));
    private static final String gitRepoDir = "F:\\Git Project\\LiveMonitoringRecordingPage";
    private static final String gitFileRelativePath = "redirect/json/config.json";
    private static final String exePath = "./bin/cpolar.exe";
    private static final String[] localCommand = {"http", "8005"};

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    int i = process.waitFor();
                    highLog("程序退出码:" + i,"info");
                } catch (InterruptedException ignored) {
                }
            }
            logger.flushRemaining();
        }));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String mapping = args.length > 0 ? args[0] : "8005";
        localCommand[1] = mapping;
        System.out.println("=====启动HTTP端口映射 " + mapping + "=====");
        highLog("运行命令: " + String.join(" ", localCommand), "info");
        if (!Files.exists(Paths.get(exePath))) {
            highLog(exePath + "端口映射工具不存在！", "warn");
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(exePath,
                localCommand[0],
                localCommand[1],
                "--log-level=info",
                "-log=stdout"
        );
        pb.redirectErrorStream(true);// 合并错误流
        process = pb.start();
        // 单独线程读取字节流，手动处理\r \n
        new Thread(() -> processResult(process)).start();
        process.waitFor();

    }

    private static void processResult(Process process) {
        // 读取输出信息
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            AppLog appLog = new AppLog();
            while ((line = reader.readLine()) != null) {
                try {
                    processResult(line, appLog);
                } catch (Exception e) {
                    highLog("处理processResult异常:" + ThrowableUtil.getAllCauseMessage(e), "error");
                }
            }
        } catch (IOException e) {
            highLog("读取process信息异常:" + ThrowableUtil.getAllCauseMessage(e), "error");
        }
    }

    private static void processResult(String line, AppLog appLog) {
        AppLog log = LogParser.parseLogLine(line, appLog);
        if (log != null) {
            String msg = log.getMsg();
            if (msg.contains("Tunnel established at")) {
                highLog(log.toString());
                debouncer.debounce(() -> {
                    String url = msg.substring(msg.lastIndexOf("at ") + 3);
                    try {
                        updateGitURLFile(url);
                        highLog("获取URl更新: " + url, "info");
                    } catch (Exception e) {
                        highLog("更新GitURL文件失败:" + ThrowableUtil.getAllCauseMessage(e), "error");
                    }
                });
            }
        }
    }

    public static void updateGitURLFile(String url) throws Exception {
        Path file = Paths.get(gitRepoDir, gitFileRelativePath);
        String json = JSONUtil.createObj().set("redirectUrl", url)
                .set("localCommand", String.join(" ", localCommand))
                .set("updateTime", TimeUtil.getNowTime())
                .toStringPretty();
        TxtOperation.writeTxtFile(json, file.toFile(), "UTF-8", false);
        GitUtil.gitUploadFile(gitRepoDir, gitFileRelativePath, "自动提交");
    }

    private static void highLog(String msg, String level) {
        msg = TimeUtil.getNowTime() + " [" + level + "] " + msg;
        highLog(msg);
    }

    private static void highLog(String msg) {
        logger.highLog(msg);
        System.out.println(msg);
    }
}