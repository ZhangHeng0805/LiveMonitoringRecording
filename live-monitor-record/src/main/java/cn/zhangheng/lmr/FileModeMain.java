package cn.zhangheng.lmr;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.lmr.service.CpolarToolStarter;
import cn.zhangheng.lmr.service.LocalHttpServerStarter;
import cn.zhangheng.lmr.service.ServerStarterManager;
import cn.zhangheng.record.activation.ActivationUtil;
import cn.zhangheng.record.activation.ErrorException;
import cn.zhangheng.record.activation.WarnException;
import cn.zhangheng.record.bean.Constant;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.bean.enums.RunMode;
import cn.zhangheng.record.service.MonitorMain;
import cn.zhangheng.record.util.RoomUtils;
import cn.zhangheng.record.util.TrayIconUtil;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.bean.RoomJson;
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
import java.util.ArrayList;
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
    private static final ServerStarterManager starterManager = new ServerStarterManager();
    private static final AtomicInteger runCount = new AtomicInteger(0);
    private static final Setting setting = ConfigKit.get(Setting.class);

    public static void main(String[] args) throws Exception {
        try {
            if (verifyActivation()) return;
            if (args.length > 0) {
                basePath = args[0];
            }
            List<Path> paths = retrieveFile(basePath);
            //添加相关服务
            starterManager.addStarter(new LocalHttpServerStarter());
            CpolarToolStarter cpolarToolStarter = new CpolarToolStarter();
            cpolarToolStarter.setDelayedSec(2);
            starterManager.addStarter(cpolarToolStarter);
            starterManager.startAllServer();//启动服务

            int maxMonitorThreads = setting.getMaxMonitorThreads();
            ThreadPool = new ThreadPoolExecutor(
                    1,
                    maxMonitorThreads,
                    30, TimeUnit.SECONDS,
                    new SynchronousQueue<>()// 关键：不存排队任务，没有空闲线程就新建线程直到max
                    );
            log.info("启动监听线程数：{}个", paths.size());
            for (int i = 0; i < paths.size(); i++) {
                Path file = paths.get(i);
                try {
                    submitRoomFileModel(file);
                } catch (RuntimeException e) {
                    log.error("启动失败: {}", ThrowableUtil.getAllCauseMessage(e));
                }
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
            starterManager.stopAllServer();
        }


    }

    private static boolean verifyActivation() throws InterruptedException {
        try {
            ActivationUtil.verifyActivationCodeFile(Constant.deviceUniqueId, setting.getActivateVoucherPath());
        } catch (ErrorException errorException) {
            String message = ThrowableUtil.getAllCauseMessage(errorException);
            TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
            iconUtil.notifyMessage(errorException.getMessage(), TrayIcon.MessageType.ERROR);
            log.error("启动失败！{}", message);
            TimeUnit.SECONDS.sleep(3);
            iconUtil.shutdown();
            return true;
        } catch (WarnException warnException) {
            TrayIconUtil iconUtil = TrayIconUtil.getInstance(Constant.Application);
            String message = warnException.getMessage();
            log.warn(message);
            iconUtil.notifyMessage(message, TrayIcon.MessageType.WARNING);
            TimeUnit.SECONDS.sleep(3);
            iconUtil.shutdown();
        }
        return false;
    }


    private static List<Path> retrieveFile(String basePath) {
        try (Stream<Path> list = Files.list(Paths.get(basePath))) {
            return list.filter(f -> f.getFileName().toString().endsWith(FileModeMain.fileSuffix)).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("遍历文件出现异常", e);
        }
    }

    public static void startMonitor(Path file) {
        String key;
        RoomFileModel model = null;
        boolean startFailed = true;
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
            if (platform == null) throw new IllegalArgumentException(file + "中直播平台不能为空!");
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
            startFailed = false;
            log.debug("{} 监听文件结束运行!", file);
        } catch (Throwable e) {
            log.error("{} 监听发生异常:{}", file, e.getMessage(), e);
        } finally {
            endMonitor(model);
            // 启动阶段失败（解析失败/平台为空/授权异常等）：回滚注册，避免占着 key 又无法再次添加
            if (startFailed && model != null) {
                roomFileMap.remove(file, model);
                log.warn("{} 监听启动失败，已回滚监听注册状态", file);
            }
        }
    }

    private static void endMonitor(RoomFileModel model) {
        if (model == null) return;
        runCount.decrementAndGet();
        model.setEndTime();
        Room room = model.getMain() == null ? null : model.getMain().getRoom();
        if (room == null || room.getPlatform() == null) {
            // 还没进入监听就退出了，没有运行记录可清理
            log.warn("{} 监听未成功进入到直播间，跳过收尾", model.getId());
            return;
        }
        Room.Platform platform = room.getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            PlaywrightBrowserFactory.destroy();
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
            log.info("所有直播监听已结束，程序保持待命（网页接口仍在运行，可通过页面「退出程序」结束进程）");
        }
    }

    /**
     * 优雅退出程序（供 HTTP 接口 action/exit 在秘钥校验通过后调用）
     * 顺序：通知所有监听停止 → 关闭浏览器 → 关闭监听线程池 → 停止 HTTP/cpolar 服务 → 退出进程
     *
     * @param reason 退出来源，仅用于日志
     */
    public static void shutdown(String reason) {
        log.info("收到退出指令({})，开始终止程序...", reason);
        Thread hook = new Thread(() -> {
            try {
                // 先给 HTTP 响应留出返回时间
                TimeUnit.MILLISECONDS.sleep(500);
                for (RoomFileModel model : roomFileMap.values()) {
                    if (model == null || model.getMain() == null) continue;
                    MonitorMain<?, ?> monitorMain = model.getMain().getMonitorMain();
                    if (monitorMain == null) continue;
                    try {
                        monitorMain.setIsForceStop(true);
                        monitorMain.exit();
                    } catch (Throwable t) {
                        log.warn("停止监听{}失败:{}", model.getId(), t.getMessage());
                    }
                }
                // 等等录制任务把文件写完、监听线程退出
                TimeUnit.SECONDS.sleep(2);
                try {
                    PlaywrightBrowserFactory.closeBrowser();
                } catch (Throwable t) {
                    log.warn("关闭浏览器失败:{}", t.getMessage());
                }
                ThreadPoolExecutor pool = ThreadPool;
                if (pool != null) {
                    pool.shutdownNow();
                    try {
                        pool.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                starterManager.stopAllServer();
            } catch (Throwable t) {
                log.error("退出清理异常:{}", ThrowableUtil.getAllCauseMessage(t));
            } finally {
                log.info("程序已退出");
                System.exit(0);
            }
        }, "shutdown-hook");
        hook.setDaemon(false);
        hook.start();
    }

    private static void submitRoomFileModel(Path path) {
        try {
            if (path == null) {
                log.warn("监听文件path为null，无法重新启动监听");
                return;
            }
            ThreadPool.execute(() -> {
                startMonitor(path);
            });
        } catch (RejectedExecutionException e) {
            // 处理任务被拒绝的情况（如线程池关闭、队列满等）
            String s = path + " 监听任务提交失败，线程池可能已关闭或任务队列已满: " + e.getMessage();
            log.warn(s);
            throw new RuntimeException(s, e);
        } catch (Exception e) {
            String s = path + " 提交监听任务发生异常: " + e.getMessage();
            log.error("提交任务发生异常: {}", s);
            throw new RuntimeException(s, e);
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
        String baseName = bean.getName() + "-" + bean.getPlatform().name() + "-" + bean.getId();
        String suffix = fileSuffix + ".del";
        Path target = filePath.resolveSibling(baseName + suffix);
        if (Files.exists(target)) {
            // 回收站已有同名文件时追加时间戳，避免把上一次回收的监听文件覆盖掉
            target = filePath.resolveSibling(baseName + "-" + System.currentTimeMillis() + suffix);
        }
        Path rename = FileUtil.rename(file.toPath(), target.getFileName().toString(), false);
        roomFileMap.remove(filePath);
        log.info("{}直播监听已删除,监听文件重命名:{}", key, rename.toString());
    }

    public static void addMain(RoomJson object) throws WarnException {
        if (StrUtil.isBlank(object.getId())) throw new WarnException("直播间ID不能为空!");
        if (object.getPlatform() == null) throw new WarnException("直播平台不能为空!");
        // 房间ID会拼进文件名，禁止路径分隔符和 .. ，防止越界写文件
        if (object.getId().contains("/") || object.getId().contains("\\")
                || object.getId().contains("..") || object.getId().matches("^[A-Za-z]:.*"))
            throw new WarnException("直播间ID包含非法字符!");
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
        // 只允许回收站目录下的普通文件名，防止 ../ 造成越界读写
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains(".."))
            throw new WarnException("恢复的监听文件名不合法!");
        Path delPath = Paths.get(getBasePath(), fileName);
        if (!Files.exists(delPath))
            throw new WarnException("回收站文件不存在:" + fileName);

        String read = FileUtil.readString(delPath.toFile(), StandardCharsets.UTF_8);
        RoomJson bean = JSONUtil.toBean(read, RoomJson.class);
        if (bean.getPlatform() == null || StrUtil.isBlank(bean.getId()))
            throw new WarnException("回收站文件内容不完整，无法恢复!");

        // 提交体允许为空（只点恢复、不改配置时），此时全部沿用文件里的原配置
        RoomJson submit = roomJson == null ? new RoomJson() : roomJson;
        RoomJson.Setting submitSetting = submit.getSetting() == null ? new RoomJson.Setting() : submit.getSetting();
        if (bean.getSetting() == null) bean.setSetting(new RoomJson.Setting());
        BeanUtil.copyProperties(submitSetting, bean.getSetting(), CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));

        // 房间级字段：名称、保存路径、自动录制也要写回，否则前端改了不生效
        if (StrUtil.isNotBlank(submit.getName())) bean.setName(submit.getName().trim());
        if (StrUtil.isNotBlank(submit.getSavePath())) bean.setSavePath(submit.getSavePath().trim());
        if (roomJson != null) bean.setAutoRecord(submit.isAutoRecord());
        // 启用状态由程序控制：能恢复出来就是启用
        bean.setEnable(true);
        bean.check();

        // 先判重再动文件，避免重复恢复时文件被改名后留下一个没人管的正式文件
        if (getModelById(bean.getPlatform().name() + "-" + bean.getId()) != null)
            throw new RuntimeException("恢复的直播监听已在运行中!");

        String baseName = StrUtil.isBlank(bean.getName())
                ? fileName.substring(0, fileName.length() - suffix.length())
                : bean.getName().trim();
        Path target = delPath.resolveSibling(baseName + "-" + bean.getPlatform().name() + "-" + bean.getId() + fileSuffix);
        if (Files.exists(target))
            throw new WarnException("目标监听文件已存在，请先处理:" + target.getFileName());

        FileUtil.writeString(JSONUtil.toJsonPrettyStr(bean), delPath.toFile(), StandardCharsets.UTF_8);
        // 不覆盖模式改名，防止同名文件被静默覆盖
        Path rename = FileUtil.rename(delPath, target.getFileName().toString(), false);
        submitRoomFileModel(rename);
        log.info("恢复直播监听文件{}成功!", rename);
    }

    public static RoomFileModel getModelById(String id) {
        return roomFileMap.values().stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public static Map<String, Object> getCounter() {
        List<Map<String, Object>> platformList = new ArrayList<>();
        Map<String, Object> platformData = new HashMap<>();
        long total = 0L;
        for (RoomFileModel model : roomFileMap.values()) {
            Map<String, Object> counter = new HashMap<>();
            Room room = model.getMain().getMonitorMain().getRoom();
            counter.put("id", model.getId());
            counter.put("name", room.getNickname());
            counter.put("url", room.getRoomUrl());
            counter.put("platform", room.getPlatform().getName());
            counter.put("living", room.isLiving());
            counter.put("running", model.isRunning());
            counter.put("intervalSec", room.getSetting().getDelayIntervalSec());
            int count = model.getMain().getMonitorMain().getRoomMonitor().getRefreshCount();
            total += count;
            counter.put("count", count);
            counter.put("updateTime", TimeUtil.toTime(room.getUpdateTime()));
            counter.put("startTime", TimeUtil.toTime(model.getStartTime()));
            counter.put("endTime", model.getEndTime() > 0 ? TimeUtil.toTime(model.getEndTime()) : null);
            platformList.add(counter);
        }

        platformData.put("list", platformList);
        platformData.put("listSize", platformList.size());
        platformData.put("totalCount", total);
        return platformData;
    }
}
