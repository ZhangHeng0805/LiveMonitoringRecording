package cn.zhangheng.lmr;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.activation.ActivationUtil;
import cn.zhangheng.common.activation.DeviceInfoCollector;
import cn.zhangheng.common.activation.ErrorException;
import cn.zhangheng.common.activation.WarnException;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.util.TrayIconUtil;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.fileModeApi.LocalServerApi;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/25 星期四 01:15
 * @version: 1.0
 * @description: 通过直播监听文件的形式启动监听，可以同时监听多个
 */
@Slf4j
public class FileModeMain {
    private static final String basePath = "./";
    private static final String fileSuffix = ".room.json";
    @Getter
    private static ThreadPoolExecutor ThreadPool = null;
    @Getter
    private static final ConcurrentHashMap<String, Main> mainMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<String, Path> executeFileMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Room.Platform, Integer> platformMap = new ConcurrentHashMap<>();
    private static LocalServerApi serverApi;
    private static final AtomicInteger runCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        try {
            String path;
            if (args.length > 0) {
                path = args[0];
            } else {
                path = basePath;
            }
            List<Path> paths = retrieveFile(path, fileSuffix);
            if (paths.isEmpty()) {
                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
                String message = StrUtil.format("{} 路径下没有获取到监听的直播间文件[{}]", path, fileSuffix);
                iconUtil.notifyMessage(message, TrayIcon.MessageType.WARNING);
                log.warn(message);
                iconUtil.shutdown();
                return;
            }
            Setting setting = new Setting();
            try {
                ActivationUtil.verifyActivationCodeFile(Constant.deviceUniqueId, setting.getActivateVoucherPath());
            } catch (ErrorException errorException) {
                String message = ThrowableUtil.getAllCauseMessage(errorException);
                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
                iconUtil.notifyMessage(errorException.getMessage(), TrayIcon.MessageType.ERROR);
                iconUtil.shutdown();
                log.error(message, errorException);
                return;
            } catch (WarnException warnException) {
                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
                String message = warnException.getMessage();
                log.warn(message);
                iconUtil.notifyMessage(message, TrayIcon.MessageType.WARNING);
                iconUtil.shutdown();
            }

            serverApi = new LocalServerApi(Constant.monitorServerPort);
            serverApi.start();
            int coreSize = Math.min(paths.size(), setting.getMaxMonitorThreads());
            ThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(coreSize);
            log.info("启动监听线程数：{}个", coreSize);
            for (int i = 0; i < coreSize; i++) {
                Path file = paths.get(i);
                ThreadPool.execute(() -> {
                    startMonitor(file);
                });
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (ThreadPool != null) {
                ThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
            if (serverApi != null) {
                serverApi.stop();
            }
        }


    }


    private static List<Path> retrieveFile(String basePath, String fileSuffix) {
        try (Stream<Path> list = Files.list(Paths.get(basePath))) {
            return list.filter(f -> f.getFileName().toString().endsWith(fileSuffix)).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("遍历文件出现异常", e);
        }
    }

    public static void startMonitor(Path file) {
        try {
            //解析文件
            String s = String.join("", Files.readAllLines(file));
            JSONObject json = JSONUtil.parseObj(s);
//            System.out.println(json.toStringPretty());
            Boolean isRecord = json.getBool("isRecord", false);
            String id = json.getStr("id");
            Room.Platform platform = json.get("platform", Room.Platform.class);
            Setting setting = json.get("setting", Setting.class);
            if (setting != null) {
                setting.setRunMode(RunMode.FILE);
            }
            //运行监听
            String key = platform.name() + "-" + id;
            Thread.currentThread().setName(key);
            executeFileMap.put(key, file);
            Main main = new Main();
            mainMap.put(key, main);
            runCount.incrementAndGet();
            platformMap.compute(platform, (k, v) -> v == null ? 1 : v + 1);
            log.debug("{} 监听文件开始运行!", file);
            main.start(setting, id, platform, isRecord);
            log.debug("{} 监听文件结束运行!", file);
            endMonitor(key);
        } catch (Exception e) {
            log.error(file + " 监听发生异常:" + e.getMessage(), e);
        }
    }

    private static void endMonitor(String key) {
        runCount.decrementAndGet();
        Main remove = mainMap.get(key);
        Room.Platform platform = remove.getRoom().getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            DouYinBrowserFactory.closeBrowser();
        }
        log.info("{}个监听运行情况：{}", runCount.get(), platformMap);
        if (runCount.get() < 1) {
            log.debug("没有监听任务，程序结束！");
            ThreadPool.shutdown();
            System.exit(0);
        }
    }

    public static void restartMain(String key) throws RuntimeException {
        try {
            ThreadPool.execute(() -> {
                startMonitor(executeFileMap.get(key));
            });
        } catch (RejectedExecutionException e) {
            // 处理任务被拒绝的情况（如线程池关闭、队列满等）
            String s = "监听任务提交失败，线程池可能已关闭或任务队列已满：" + ThrowableUtil.getAllCauseMessage(e);
            log.warn(s);
            throw new RuntimeException(s);
        } catch (Exception e) {
            String s = "提交监听任务发生异常" + ThrowableUtil.getAllCauseMessage(e);
            log.error("提交任务发生异常: {}", s);
            throw new RuntimeException(s);
        }
    }
}
