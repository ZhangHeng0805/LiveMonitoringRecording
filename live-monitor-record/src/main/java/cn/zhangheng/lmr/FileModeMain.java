package cn.zhangheng.lmr;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.activation.ActivationUtil;
import cn.zhangheng.common.activation.ErrorException;
import cn.zhangheng.common.activation.WarnException;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.util.TrayIconUtil;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.fileModeApi.LocalServerApi;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Getter
    private static String basePath = "./";
    @Getter
    private static final String fileSuffix = ".room.json";
    @Getter
    private static ThreadPoolExecutor ThreadPool = null;
    @Getter
    private static final ConcurrentHashMap<Path, RoomFileModel> roomFileMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Room.Platform, Integer> platformMap = new ConcurrentHashMap<>();
    private static LocalServerApi serverApi;
    private static final AtomicInteger runCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        try {
            if (args.length > 0) {
                basePath = args[0];
            }
            List<Path> paths = retrieveFile(basePath, fileSuffix);
//            if (paths.isEmpty()) {
//                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
//                String message = StrUtil.format("{} 路径下没有获取到监听的直播间文件[{}]", path, fileSuffix);
//                iconUtil.notifyMessage(message, TrayIcon.MessageType.WARNING);
//                log.warn(message);
//                TimeUnit.SECONDS.sleep(3);
//                iconUtil.shutdown();
//                return;
//            }
            Setting setting = new Setting();
            try {
                ActivationUtil.verifyActivationCodeFile(Constant.deviceUniqueId, setting.getActivateVoucherPath());
            } catch (ErrorException errorException) {
                String message = ThrowableUtil.getAllCauseMessage(errorException);
                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
                iconUtil.notifyMessage(errorException.getMessage(), TrayIcon.MessageType.ERROR);
                log.error("启动失败！{}", message);
                TimeUnit.SECONDS.sleep(3);
                iconUtil.shutdown();
                return;
            } catch (WarnException warnException) {
                TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
                String message = warnException.getMessage();
                log.warn(message);
                iconUtil.notifyMessage(message, TrayIcon.MessageType.WARNING);
                TimeUnit.SECONDS.sleep(3);
                iconUtil.shutdown();
            }

            serverApi = new LocalServerApi(Constant.monitorServerPort);
            serverApi.start();
            int coreSize = setting.getMaxMonitorThreads();
            ThreadPool = new ThreadPoolExecutor(coreSize, coreSize, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(coreSize * 2));
            log.info("启动监听线程数：{}个", coreSize);
            for (int i = 0; i < paths.size(); i++) {
                Path file = paths.get(i);
                submitRoomFileModel(file);
                try {
                    //延时启动
                    TimeUnit.SECONDS.sleep(i + 1);
                } catch (InterruptedException ignored) {
                }
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
        String key;
        RoomFileModel model = null;
        try {
            //解析文件
            String s = String.join("", Files.readAllLines(file));
            JSONObject json = JSONUtil.parseObj(s);
            Boolean isEnable = json.getBool("isEnable", true);
            if (!isEnable) return;//判断使用启用
            Boolean isRecord = json.getBool("isRecord", false);
            String id = json.getStr("id");
            Room.Platform platform = json.get("platform", Room.Platform.class);
            Setting setting = json.get("setting", Setting.class);
            if (setting != null) {
                setting.setRunMode(RunMode.FILE);
            } else {
                log.warn("{}监听文件没有setting", file);
            }
            //直播间标识
            key = platform.name() + "-" + id;
            Thread.currentThread().setName(key);
            model = new RoomFileModel();
            model.setId(key);
            model.setFilePath(file);
//            executeFileMap.put(key, file);
            roomFileMap.put(file, model);
            Main main = new Main();
            model.setMain(main);
            runCount.incrementAndGet();
            platformMap.compute(platform, (k, v) -> v == null ? 1 : v + 1);
            log.debug("{} 监听文件开始运行!", file);
            model.setStartTime();
            main.start(setting, id, platform, isRecord);
            log.debug("{} 监听文件结束运行!", file);
        } catch (Throwable e) {
            log.error(file + " 监听发生异常:" + e.getMessage(), e);
        } finally {
            endMonitor(model);
            Thread.currentThread().interrupt();
        }
    }

    private static void endMonitor(RoomFileModel model) {
        if (model == null) return;
        runCount.decrementAndGet();
        model.setEndTime();
        Room.Platform platform = model.getMain().getRoom().getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            DouYinBrowserFactory.closeBrowser();
        }
        log.info("{}个监听运行情况：{}", runCount.get(), platformMap);
        if (runCount.get() < 1) {
            log.debug("没有监听任务，程序结束！");
            ThreadPool.shutdownNow();
            System.exit(0);
        }
//        executeFileMap.remove(model.getId());
    }

    public static void delMain(String key) throws WarnException, IOException {
        RoomFileModel model = getModelById(key);
        if (model == null) throw new WarnException(key + "直播监听不存在！");
        if (model.isRunning()) throw new WarnException(key + "直播监听已开启！请先停止后删除");
        Path filePath = model.getFilePath();
        List<String> strings = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        String name = model.getMain().getMonitorMain().getRoom().getNickname();
        JSONObject json = JSONUtil.parseObj(String.join("", strings)).set("isEnable", false);
        if (StrUtil.isNotBlank(name)) {
            json.set("name", name);
        }
        File file = FileUtil.writeString(json.toStringPretty(), filePath.toFile(), StandardCharsets.UTF_8);//修改重写
        Path rename = FileUtil.rename(filePath, file.getName() + ".del", true);
        roomFileMap.remove(filePath);
        log.info("{}直播监听已删除,监听文件重命名:{}", key, rename.toString());
    }

    public static void addMain(JSONObject object) throws WarnException {
        Room.Platform platform = object.get("platform", Room.Platform.class);
        String roomID = object.getStr("id");
        String key = platform.name() + "-" + roomID;
        object.set("isEnable", true);
        RoomFileModel model = getModelById(key);
        if (model != null) throw new WarnException(key + "直播监听已存在！");
        JSONObject setting = object.getJSONObject("setting");
        String cookie = setting.getStr("cookie");
        if (StrUtil.isNotBlank(cookie)) {
            setting.set("cookie" + platform.name(), cookie);
        }
        setting.remove("cookie");
        if (StrUtil.isBlank(setting.getStr("xiZhiUrl"))) {
            setting.remove("xiZhiUrl");
        }
        setting.set("runMode", RunMode.FILE.name());
        Path path = Paths.get(getBasePath(), key + fileSuffix);
        FileUtil.mkdir(getBasePath());//若目录不存在直接新建
        File file = FileUtil.writeString(object.toStringPretty(), path.toFile(), StandardCharsets.UTF_8);
        log.info("新增直播监听文件{}创建成功", file.getPath());
        submitRoomFileModel(file.toPath());
    }

    private static void submitRoomFileModel(Path path) {
        try {
            ThreadPool.execute(() -> {
                if (path == null) {
                    log.warn("监听文件path为null，无法重新启动监听");
                    return;
                }
                startMonitor(path);
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

    public static void restartMain(String key) throws RuntimeException {
        RoomFileModel model = getModelById(key);
        if (model == null) throw new RuntimeException(key + "直播标识不存在");
        if (model.isRunning()) throw new RuntimeException("直播监听已启动运行,不可重复启动");
        submitRoomFileModel(model.getFilePath());
    }

    public static RoomFileModel getModelById(String id) {
        return roomFileMap.values().stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public static Map<String, Object> getCounter() {
        Map<String, Object> platformData = new HashMap<>();
        long total = 0L;
        for (RoomFileModel model : roomFileMap.values()) {
            Map<String, Object> counter = new HashMap<>();
            Room room = model.getMain().getMonitorMain().getRoom();
            counter.put("name", room.getNickname());
            counter.put("url", room.getRoomUrl());
            counter.put("platform", room.getPlatform().getName());
            counter.put("living", room.isLiving());
            counter.put("running", model.isRunning());
            counter.put("intervalSec", room.getSetting().getDelayIntervalSec());
            int count = model.getMain().getMonitorMain().getRoomMonitor().getCount();
            total += count;
            counter.put("count", count);
            counter.put("updateTime", TimeUtil.toTime(room.getUpdateTime()));
            counter.put("startTime", TimeUtil.toTime(model.getStartTime()));
            counter.put("endTime", model.getEndTime() > 0 ? TimeUtil.toTime(model.getEndTime()) : null);
            platformData.put(model.getId(), counter);
        }
        platformData.put("totalCount", total);
        return platformData;
    }
}
