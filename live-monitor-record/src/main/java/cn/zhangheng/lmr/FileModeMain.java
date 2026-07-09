package cn.zhangheng.lmr;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import cn.zhangheng.common.util.RoomUtils;
import cn.zhangheng.common.util.TrayIconUtil;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.bean.RoomJson;
import cn.zhangheng.lmr.http_server.LocalMonitorServer;
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
    private static String basePath = "./room";
    @Getter
    private static final String fileSuffix = ".room.json";
    @Getter
    private static ThreadPoolExecutor ThreadPool = null;
    @Getter
    private static final ConcurrentHashMap<Path, RoomFileModel> roomFileMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Room.Platform, Integer> platformMap = new ConcurrentHashMap<>();
    private static LocalMonitorServer serverApi;
    private static final AtomicInteger runCount = new AtomicInteger(0);
    private static final Setting setting = Setting.getInstance();

    public static void main(String[] args) throws Exception {
        try {
            if (args.length > 0) {
                basePath = args[0];
            }
            List<Path> paths = retrieveFile(basePath, fileSuffix);
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

            serverApi = new LocalMonitorServer(setting.getMonitorServerPort());
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
                ThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
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
            RoomJson bean = JSONUtil.toBean(s, RoomJson.class);
            boolean isEnable = bean.isEnable();
            if (!isEnable) return;//判断使用启用
            boolean autoRecord = bean.isAutoRecord();
            String id = bean.getId();
            if (StrUtil.isBlank(id)) throw new IllegalArgumentException(file + "中直播ID不能为空!");
            Room.Platform platform = bean.getPlatform();
            Setting set = bean.convert(BeanUtil.copyProperties(setting, Setting.class));
            if (!RunMode.FILE.equals(set.getRunMode())) {
                set.setRunMode(RunMode.FILE);
            }
            //直播间标识
            key = platform.name() + "-" + id;
            Thread.currentThread().setName(key);
            model = new RoomFileModel();
            model.setId(key);
            model.setFilePath(file);
            roomFileMap.put(file, model);
            Main main = new Main();
            model.setMain(main);
            runCount.incrementAndGet();
            platformMap.compute(platform, (k, v) -> v == null ? 1 : v + 1);
            log.debug("{} 监听文件开始运行!", file);
            model.setStartTime();
            main.start(set, id, platform, autoRecord);
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
        Room room = model.getMain().getRoom();
        Room.Platform platform = room.getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            DouYinBrowserFactory.closeBrowser();
        }
        log.info("{}个监听运行情况：{}", runCount.get(), platformMap);

        if (StrUtil.isNotBlank(room.getNickname())) {
            File file = model.getFilePath().toFile();
            String json = FileUtil.readString(file, StandardCharsets.UTF_8);
            RoomJson bean = JSONUtil.toBean(json, RoomJson.class);
            String basePathStr = RoomUtils.getBasePathStr(room);
            String savePath = bean.getSavePath();
            if (savePath != null && !basePathStr.equals(savePath)) {
                Path newPath = Paths.get(basePathStr);
                Path oldPath = Paths.get(savePath);
                try {
                    Files.move(oldPath, newPath);
                } catch (IOException e) {
                    log.error("文件夹重命名失败!");
                }
            }
            bean.setName(room.getNickname());
            bean.setSavePath(basePathStr);
            FileUtil.writeString(JSONUtil.toJsonPrettyStr(bean), file, StandardCharsets.UTF_8);//修改重写
        }
        if (runCount.get() < 1) {
            log.debug("没有监听任务，程序结束！");
            ThreadPool.shutdownNow();
            System.exit(0);
        }
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


    public static void delMain(String key) throws WarnException, IOException {
        RoomFileModel model = getModelById(key);
        if (model == null) throw new WarnException(key + "直播监听不存在！");
        if (model.isRunning()) throw new WarnException(key + "直播监听已开启！请先停止后删除");
        Path filePath = model.getFilePath();
        String read = FileUtil.readString(filePath.toFile(), StandardCharsets.UTF_8);
        RoomJson bean = JSONUtil.toBean(read, RoomJson.class);
        bean.setEnable(false);
        File file = FileUtil.writeString(JSONUtil.toJsonPrettyStr(bean), filePath.toFile(), StandardCharsets.UTF_8);//修改重写
        Path rename = FileUtil.rename(file.toPath(), bean.getName() + "-" + bean.getPlatform().name() + "-" + bean.getId() + fileSuffix + ".del", true);
        roomFileMap.remove(filePath);
        log.info("{}直播监听已删除,监听文件重命名:{}", key, rename.toString());
    }

    public static void addMain(RoomJson object) throws WarnException {
        if (StrUtil.isBlank(object.getId())) throw new WarnException("直播间ID不能为空!");
        String key = object.getPlatform().name() + "-" + object.getId();
        object.setEnable(true);
        object.check();
        RoomFileModel model = getModelById(key);
        if (model != null) throw new WarnException(key + "直播监听已存在！");
        Path path = Paths.get(getBasePath(), key + fileSuffix);
        FileUtil.mkdir(getBasePath());//若目录不存在直接新建
        File file = FileUtil.writeString(JSONUtil.toJsonPrettyStr(object), path.toFile(), StandardCharsets.UTF_8);
        log.info("新增直播监听文件{}创建成功", file.getPath());
        submitRoomFileModel(file.toPath());
    }


    public static void recoverRoomFile(String fileName, RoomJson roomJson) throws Exception {
        String suffix = fileSuffix + ".del";
        if (fileName == null || !fileName.endsWith(suffix))
            throw new WarnException("恢复的监听文件名后缀不符合标准!");
        Path delPath = Paths.get(getBasePath(), fileName);
        String read = FileUtil.readString(delPath.toFile(), StandardCharsets.UTF_8);
        RoomJson bean = JSONUtil.toBean(read, RoomJson.class);
        BeanUtil.copyProperties(roomJson.getSetting(), bean.getSetting(), CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));
        bean.setEnable(true);
        bean.setAutoRecord(roomJson.isAutoRecord());
        bean.check();
        Files.deleteIfExists(delPath);
        FileUtil.writeString(JSONUtil.toJsonPrettyStr(bean), delPath.toFile(), StandardCharsets.UTF_8);//先修改
        Path rename = FileUtil.rename(delPath, bean.getName() + "-" + bean.getPlatform().name() + "-" + bean.getId() + fileSuffix, true);
        if (getModelById(bean.getPlatform() + "-" + bean.getId()) == null) {
            submitRoomFileModel(rename);
            log.info("恢复直播监听文件{}成功!", rename);
        } else {
            throw new RuntimeException("恢复的直播监听已在运行中!");
        }
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
