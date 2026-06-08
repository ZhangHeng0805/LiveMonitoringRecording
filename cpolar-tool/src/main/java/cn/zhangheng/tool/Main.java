package cn.zhangheng.tool;

import cn.hutool.json.JSONUtil;
import com.zhangheng.bean.Debouncer;
import com.zhangheng.file.TxtOperation;
import com.zhangheng.log.AsyncBatchLogger;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
    private static final String gitFilePath = "F:\\Git Project\\LiveMonitoringRecordingPage\\redirect\\json";
    private static final String gitFileName = "config.json";
    private static final String exePath = "./bin/cpolar.exe";
    private static final String[] localCommand = {"http", "8005"};

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    int i = process.waitFor();
                    System.out.println("程序已关闭：" + i);
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
        highLog("运行命令: " + String.join(" ", localCommand));
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
        int exitCode = process.waitFor();
        System.out.println("退出码:" + exitCode);
    }

    private static void processResult(Process process) {
        // 读取输出信息
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    processResult(line);
                } catch (Exception e) {
                    highLog("处理processResult异常:" + ThrowableUtil.getAllCauseMessage(e), "error");
                }
            }
        } catch (IOException e) {
            highLog("读取process信息异常:" + ThrowableUtil.getAllCauseMessage(e), "error");
        }
    }

    private static void processResult(String line) {
        AppLog log = LogParser.parseLogLine(line);
        if (log != null) {
            highLog(log.toString());
            String msg = log.getMsg();
            if (msg.contains("Tunnel established at")) {
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
        Path path = Paths.get(gitFilePath, gitFileName);
        String json = JSONUtil.createObj().set("redirectUrl", url).set("localCommand", String.join(" ", localCommand)).toStringPretty();
        TxtOperation.writeTxtFile(json, path.toFile(), "UTF-8", false);
        GitCmdUpload.gitUploadFile(gitFilePath, gitFileName);
    }

    private static void highLog(String msg, String level) {
        msg = TimeUtil.getNowTime() + " [" + level + "] " + msg;
        highLog(msg);
    }

    private static void highLog(String msg) {
        logger.highLog(msg);
        System.out.println(msg);
    }

    private static void parseRawStream(InputStream is) {
        byte[] buf = new byte[1024];
        int len;
        StringBuilder sb = new StringBuilder();
        Charset charset = Charset.forName("UTF-8");
        try {
            while ((len = is.read(buf)) != -1) {
                String chunk = new String(buf, 0, len, charset);
                for (char c : chunk.toCharArray()) {
                    if (c == '\r') {
                        // 回车：打印当前缓冲内容后清空（对应原地刷新的一行）
                        System.out.println("[刷新行] " + sb);
                        sb.setLength(0);
                    } else if (c == '\n') {
                        // 换行：标准一行
                        System.out.println("[新行] " + sb);
                        sb.setLength(0);
                    } else {
                        sb.append(c);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}