package cn.zhangheng.cpolar;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.cpolar.git.GitUtil;
import com.zhangheng.bean.Debouncer;
import com.zhangheng.file.TxtOperation;
import com.zhangheng.log.AsyncBatchLogger;
import com.zhangheng.util.HttpURLConnectionUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import com.zhangheng.util.UserAgentUtil;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 利用cpolar端口映射工具实现内网穿透，
 * 并将映射的外网URL自动更新至git上，
 * 实现实时获取最新的映射外网URL地址
 */
public class Starter {
    private static Process process = null;
    private static final AsyncBatchLogger logger = new AsyncBatchLogger(Paths.get("./logs/cpolar-run.log"));;
    private static Setting setting = null;
    private static final Debouncer debouncer = new Debouncer(1000);
    //    private static final String gitRepoDir = "F:\\Git Project\\LiveMonitoringRecordingPage";
//    private static final String gitFileRelativePath = "redirect/json/config.json";
//    private static final String exePath = "./bin/cpolar.exe";
    private static final String[] localCommand = {"http", "8005"};
    @Getter
    private static volatile boolean isRunning;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                }
            }
            logger.flushRemaining();
        }));
    }

    public static void main(String[] args) throws RuntimeException {
        String mapping = args.length > 0 ? args[args.length - 1] : "8005";
        start(Integer.parseInt(mapping));
    }

    public static void start(int port) throws RuntimeException {
        try {
            setting = Setting.getInstance();
            localCommand[1] = String.valueOf(port);
            System.out.println("=====启动HTTP端口映射 " + port + "=====");
            highLog("运行命令: " + String.join(" ", localCommand), "info");
            if (!Files.exists(Paths.get(setting.getExePath()))) {
                highLog(setting.getExePath() + "端口映射工具不存在！", "warn");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(setting.getExePath(),
                    localCommand[0],
                    localCommand[1],
                    "--log-level=info",
                    "-log=stdout"
            );
            isRunning = true;
            pb.redirectErrorStream(true);// 合并错误流
            process = pb.start();
            // 单独线程读取字节流，手动处理\r \n
            new Thread(() -> processResult(process)).start();
            int i = process.waitFor();
            highLog("程序退出码:" + i, "info");
        } catch (Exception e) {
            highLog("启动运行异常:" + ThrowableUtil.getAllCauseMessage(e), "error");
            throw new RuntimeException("cpolar工具启动异常", e);
        }
    }

    public static void stop(boolean force){
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
                        JSONObject jsonObject = JSONUtil.createObj().set("redirectUrl", url)
                                .set("localCommand", String.join(" ", localCommand))
                                .set("ip", JSONUtil.createObj().set("ticket", getIPTicket()))
                                .set("updateTime", TimeUtil.getNowTime());
                        updateGitURLFile(jsonObject);
                        highLog("获取URl更新: " + url, "info");
                    } catch (Exception e) {
                        highLog("更新GitURL文件失败:" + ThrowableUtil.getAllCauseMessage(e), "error");
                    }
                });
            }
        }
    }

    private static String getIPTicket() {
        HttpURLConnection connection = null;
        try {
            connection = HttpURLConnectionUtil.getRequest("https://ip.cn/", MapUtil.of("User-Agent", UserAgentUtil.getRandomUser_Agent()));
            String bodyStr = HttpURLConnectionUtil.responseBodyStr(connection);
            int index;
            if ((index = bodyStr.indexOf("_ticket")) > 0) {
                String tmp1 = bodyStr.substring(index);
                String tmp2 = tmp1.substring(0, tmp1.indexOf(";"));
                return tmp2.substring(tmp2.indexOf("\"") + 1, tmp2.lastIndexOf("\""));
            }
        } catch (Exception e) {
        } finally {
            HttpURLConnectionUtil.close(connection);
        }
        return null;
    }

    private static void updateGitURLFile(JSONObject jsonObject) throws Exception {
        Path file = Paths.get(setting.getGitRepoDir(), setting.getGitFileRelativePath());
        highLog(jsonObject.toString(), "info");
        String json = jsonObject.toStringPretty();
        TxtOperation.writeTxtFile(json, file.toFile(), "UTF-8", false);
        GitUtil.gitUploadFile(setting.getGitRepoDir(), setting.getGitFileRelativePath(), "自动提交");
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