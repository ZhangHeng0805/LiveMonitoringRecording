package cn.zhangheng.lmr;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.douyin.util.DouYinBrowserFactory;
import cn.zhangheng.lmr.fileModeApi.LocalServerApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private static ExecutorService ThreadPool = null;
    @Getter
    private static final ConcurrentHashMap<String, Main> mainMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Room.Platform, Integer> platformMap = new ConcurrentHashMap<>();
    private static LocalServerApi serverApi;

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
                log.warn("{} 路径下没有获取到监听的直播间文件[{}]", path, fileSuffix);
                return;
            }
            serverApi = new LocalServerApi(8005);
            serverApi.start();
            int coreSize = Math.min(paths.size(), Constant.maxMonitorThreads);
            ThreadPool = Executors.newFixedThreadPool(coreSize);
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
                ThreadPool.shutdown();
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

    private static void startMonitor(Path file) {
        try {
            //解析文件
            String s = String.join("", Files.readAllLines(file));
            JSONObject json = JSONUtil.parseObj(s);
            System.out.println(json.toStringPretty());
            Boolean isRecord = json.getBool("isRecord", false);
            String id = json.getStr("id");
            Room.Platform platform = json.get("platform", Room.Platform.class);
            Setting setting = json.get("setting", Setting.class);
            //运行监听
            Thread.currentThread().setName(platform.name() + "-" + id);
            Main main = new Main();
            String key = platform.name() + "-" + id;
            mainMap.put(key, main);
            platformMap.compute(platform, (k, v) -> v == null ? 1 : v + 1);
            log.info("{} 监听文件开始运行!", file);
            main.start(setting, id, platform, isRecord);
            log.info("{} 监听文件结束运行!", file);
            endMonitor(key);
        } catch (Exception e) {
            log.error(file + " 启动监听异常:" + e.getMessage(), e);
        }
    }

    private static void endMonitor(String key) {
        Main remove = mainMap.remove(key);
        Room.Platform platform = remove.getRoom().getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            DouYinBrowserFactory.closeBrowser();
        }
        log.info("监听运行情况：" + platformMap);
        if (mainMap.isEmpty()) {
            log.debug("没有监听任务，程序结束！");
            System.exit(0);
        }
    }
}
