package cn.zhangheng.common.service;

import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.MonitorStatus;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.common.record.RecorderTask;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.common.util.NotificationUtil;
import cn.zhangheng.common.util.TrayIconUtil;
import cn.zhangheng.common.video.FlvToMp4;
import cn.zhangheng.common.video.player.LocalServerFlvPlayer;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.EncryptUtil;
import com.zhangheng.util.NetworkUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.zhangheng.common.util.TrayIconUtil.openDirectory;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 05:58
 * @version: 1.0
 * @description: 监听核心
 */
public abstract class MonitorMain<R extends Room, M extends RoomMonitor<R, ?>> {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final TrayIconUtil trayIconUtil;
    protected final Setting setting;
    @Getter
    protected volatile Recorder recorder;
    @Getter
    protected volatile MonitorStatus status = MonitorStatus.READY;
    @Getter
    private M roomMonitor;

    protected RecorderTask recorderTask;
    protected AtomicBoolean isRunning = new AtomicBoolean(true);
    protected AtomicBoolean recordFlag = new AtomicBoolean(true);
    @Getter
    protected R room;
    protected FlvToMp4 flvToMp4;
    private final LocalServerFlvPlayer flvPlayer;
    private int tryMonitorSec = 1;
    private int tryRecordSec = 1;
    private final AtomicBoolean isForceStop = new AtomicBoolean(false);//是否强制停止

    public void setIsForceStop(boolean is) {
        isForceStop.set(is);
    }

    public boolean getIsRunning() {
        return isRunning.get();
    }

    public boolean getIsForceStop() {
        return isForceStop.get();
    }

    public MonitorMain(Setting setting) {
        this.trayIconUtil = TrayIconUtil.getInstance(Constant.Application);
        this.setting = setting;
        flvPlayer = new LocalServerFlvPlayer(setting.getFlvPlayerPort());
        startFlvPlayer();
    }

    /**
     * 启动FLV播放服务
     */
    private void startFlvPlayer() {
        if (!flvPlayer.isRunning() && !NetworkUtil.isPortUsed(setting.getFlvPlayerPort())) {
            try {
                flvPlayer.run(true);
            } catch (ExecutionException e) {
                log.error("LocalServerFlvPlayer启动失败：" + ThrowableUtil.getAllCauseMessage(e), e);
            }
        }
    }

    public void stop() {
        if (isRunning.get()) {
            roomMonitor.stop(true);
        }
    }

    protected abstract M getRoomMonitor(R room);

    public void start(R room, boolean isRecord) {
        try {
            this.room = room;
            roomMonitor = getRoomMonitor(room);
            while (getIsRunning() && room.getNickname() == null) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
                roomMonitor.refresh(false);
            }
            Thread.currentThread().setName(room.getNickname() + "-main-" + room.getPlatform().name());
            RoomMonitor.RoomListener<R> listener = getRoomListener(room, isRecord);
            trayIconUtil.setClickListener(getClickListener());
            roomMonitor.setListener(listener);
            do {
                try {
                    status = MonitorStatus.RUNNING;
                    roomMonitor.run(false);
                } catch (Exception e) {
                    status = MonitorStatus.ERROR;
                    log.error("直播间监听出现异常: {}", ThrowableUtil.getAllCauseMessage(e), e);
                    try {
                        TimeUnit.SECONDS.sleep(tryMonitorSec);
                    } catch (InterruptedException ignored) {
                    } finally {
                        if (tryMonitorSec < 10)
                            tryMonitorSec++;
                    }
                }
            } while (getIsRunning());
        } finally {
            isRunning.set(false);
            status = MonitorStatus.END;
            trayIconUtil.shutdown();
        }
    }

    private LogUtil logUtil = null;
    private boolean isFliestStart;

    private M.RoomListener<R> getRoomListener(R room, boolean isRecord) {
        NotificationUtil notificationUtil = new NotificationUtil(setting);
        String owner = room.getPlatform().getName() + "直播间: " + room.getNickname() + " [" + room.getId() + "]";
        isFliestStart = true;
        return new M.RoomListener<R>() {
            @Override
            public void onStart() {
                log.info(Constant.Application + "开始监听! {}", owner);
                isRunning.set(true);
                tryMonitorSec = 1;
                if (!RunMode.FILE.equals(setting.getRunMode())) {
                    if (trayIconUtil != null) {
                        trayIconUtil.setMenuVisible(trayIconUtil.getOpenMonitorMenu(), false);
                    }
                }
            }

            @Override
            public void onStop() {
                isRunning.set(false);
                String msg = "直播监听结束！" + owner;
                log.info(msg);
                if (!getIsForceStop()) {
                    trayIconUtil.notifyMessage(msg);
                    xiZhiSendMsg(notificationUtil, room);
                    notificationUtil.weChatSendMsg(msg);
                    notificationUtil.livingEndHandle();
                }
                exit();
            }

            @Override
            public void onChange(M.State state, R room) {
                log.info("\n{}", JSONUtil.toJsonPrettyStr(room));
                if (state == M.State.NOT_LIVING) {
                    //未开播
                    String msg = owner + "\n未开播，直播间监听中...";
                    trayIconUtil.notifyMessage(msg);
                    trayIconUtil.setToolTip(msg);
                    trayIconUtil.setStartLivingImage(false);
                    log.info(msg);
                } else {
                    while (getIsRunning() && (room.getStreams() == null || room.getStreams().isEmpty())) {
                        roomMonitor.refresh(true);
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (isRecord) {
                        recorderTask = getRecorderTask();
                        recorderTask.run(room);
                    }
                    String msg = owner + "，已开始直播了！";
                    log.info(msg);
                    if (isFliestStart) {
                        xiZhiSendMsg(notificationUtil, room);
                        notificationUtil.weChatSendMsg(msg);
                        notificationUtil.livingStartHandle();
                        isFliestStart = false;
                    }
                    if (logUtil == null) {
                        logUtil = getRoomLogUtil(room);
                    }
                    if (logUtil != null) {
                        logUtil.log(msg);
                        logUtil.init(room);
                    }
                    if (isRecord) {
                        trayIconUtil.setStartRecordStatue(true);
                        while (getIsRunning() && recorder == null) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(500);
                            } catch (InterruptedException ignored) {
                            }
                        }
                        msg += "\n已开启【" + recorder.getDefinition() + "】录制！";
                    } else {
                        msg += "\n未开启录制！";
                        trayIconUtil.setStartRecordStatue(false);
                    }
                    trayIconUtil.notifyMessage(msg);
                    trayIconUtil.setToolTip(msg);
                }
            }

            @Override
            public void onProgress(R r) {
                String statistics = owner + "\n" + statistics(logUtil, r);
                if (recorder != null && recorder.isRunning() && recorder.getStartTime() != null) {
                    String tooltip = "【" + recorder.getDefinition() + "】" + recorder.getProgressMsg();
                    trayIconUtil.setToolTip(statistics + "\n" + tooltip);
                    log.info(tooltip);
                    if (recorder.getDownloadSize() > 15L * 1024 * 1024 * 1024) {
                        recorder.stop(false);
                        log.info("录制文件大小超过15G，自动结束录制");
                    }
                } else {
                    trayIconUtil.setToolTip(statistics);
                }
                if (recordFlag.get() && recorder != null && !recorder.isRunning()) {
                    if (recorderTask != null) {
                        recorderTask.run(r);
                    }
                }
            }
        };
    }

    private LogUtil getRoomLogUtil(R room) {
        LogUtil logUtil = null;
        try {
            logUtil = new LogUtil(room);
        } catch (IOException e) {
            log.warn("统计日志生成异常：{}", ThrowableUtil.getAllCauseMessage(e));
        }
        return logUtil;
    }

    protected abstract String statistics(LogUtil logUtil, R room);


//    protected abstract FlvStreamRecorder getRecord(R room, boolean isConvert);

//    protected Recorder getRecord(R room, boolean isConvert) {
//        LinkedHashMap<String, String> streams = room.getStreams();
//        Map.Entry<String, String> stream = streams.entrySet().iterator().next();
//        String definition = stream.getKey();//清晰度
//        String flvUrl = stream.getValue();
//        String fileName = "【" + FileUtil.filterFileName(room.getNickname()) + "】" + room.getPlatform().getName() + "直播录制" + TimeUtil.toTime(new Date(), "yyyy-MM-dd HH-mm-ss") + "[" + FileUtil.filterFileName(room.getTitle()) + "].flv";
//        String path = Paths.get(LogUtil.getBasePathStr(room), fileName).toFile().getPath();
//        Recorder streamRecorder;
//        switch (recorderType) {
//            case 1:
//                try {
//                    String ffmpegPath = setting.getFfmpegPath();
//                    streamRecorder = new FFmpegFlvRecorder(flvUrl, path, definition, ffmpegPath);
//                } catch (IllegalArgumentException e) {
//                    log.warn(e.getMessage());
//                    streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
//                }
//                break;
//            default:
//                streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
//                break;
//        }
//        FlvStreamRecorder.ProgressCallback progressCallback = new Recorder.ProgressCallback() {
//            @Override
//            public void onStart(String url, String saveFilePath, String definition) {
//                Recorder.ProgressCallback.super.onStart(url, saveFilePath, definition);
//                trayIconUtil.setStartRecordStatue(true);
//
//            }
//
//            @Override
//            public void onComplete(String saveFilePath, long totalBytes, long totalDurationMS) {
//                Recorder.ProgressCallback.super.onComplete(saveFilePath, totalBytes, totalDurationMS);
//                flvToMp4(saveFilePath);
//                try {
//                    TimeUnit.SECONDS.sleep(1);
//                } catch (InterruptedException ignored) {
//                }
//                tryRecord(room, isConvert);
//            }
//
//            @Override
//            public void onError(Throwable throwable, String saveFilePath) {
//                Recorder.ProgressCallback.super.onError(throwable, saveFilePath);
//                flvToMp4(saveFilePath);
//                if (throwable instanceof InterruptedException) {
//                    return;
//                }
//                if (isRunning.get()) {
//                    try {
//                        TimeUnit.SECONDS.sleep(tryRecordSec);
//                    } catch (InterruptedException ignored) {
//                    } finally {
//                        if (tryRecordSec < 10) {
//                            tryRecordSec++;
//                        } else {
//                            tryRecordSec = 1;
//                        }
//                    }
//                }
//                tryRecord(room, isConvert);
//            }
//        };
//
//        streamRecorder.setProgressCallback(progressCallback);
//        streamRecorder.setRoom(room);
//        return streamRecorder;
//    }

    private void flvToMp4(String path) {
        Path srcPath = Paths.get(path);
        if (!Files.exists(srcPath) || Files.isDirectory(srcPath)) {
            log.warn("{}文件不存在，或不是一个文件", path);
            return;
        }
        long size = -1;
        try {
            size = Files.size(srcPath);
            if (size <= 0) {
                log.warn("{}录制文件大小为：{}B,", path, size);
                if (size == 0) {
                    Files.deleteIfExists(srcPath);
                }
                return;
            }
        } catch (IOException e) {
            log.error(ThrowableUtil.getAllCauseMessage(e));
        }
        log.info("录制文件:{},大小:{}", path, FileUtil.fileSizeStr(size));

        if (setting.isConvertFlvToMp4()) {
            try {
                flvToMp4 = new FlvToMp4(setting.getFfmpegPath());
            } catch (IllegalArgumentException e) {
                log.warn(e.getMessage());
            }
            trayIconUtil.setToolTip("录制文件: " + path + " 视频转码中...");
            Thread thread = new Thread(() -> {
                String output = path.substring(0, path.lastIndexOf(".")) + ".mp4";
                boolean convert = flvToMp4.convert(path, output);
                if (convert) {
                    Thread.currentThread().setName(room.getNickname() + "-FlvToMp4-" + room.getPlatform().name());
                    try {
                        log.debug("FlvToMp4视频转换完成！删除源文件：" + path);
                        Files.deleteIfExists(srcPath);
                        trayIconUtil.notifyMessage("录制文件保存路径：" + output);
                    } catch (IOException e) {
                        log.error("FlvToMp4视频转换完成后删除源文件失败！" + ThrowableUtil.getAllCauseMessage(e));
                    }
                }
            });
            thread.start();
        } else {
            trayIconUtil.notifyMessage("录制文件保存路径：" + path);
        }
    }

    private RecorderTask getRecorderTask() {
        RecorderTask.ActionListener actionListener = new RecorderTask.ActionListener() {
            @Override
            public void recorderStart(String url, String saveFilePath, String definition) {
                trayIconUtil.setStartRecordStatue(true);
            }

            @Override
            public void recorderComplete(String saveFilePath, long totalBytes, long totalDurationMS) {
                flvToMp4(saveFilePath);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
                tryRecord(room);
            }

            @Override
            public void recorderError(Throwable throwable, String saveFilePath) {
                log.error("FLV录制中发生异常：" + ThrowableUtil.getAllCauseMessage(throwable));
                flvToMp4(saveFilePath);
                if (throwable instanceof InterruptedException) {
                    return;
                }
                if (isRunning.get()) {
                    try {
                        TimeUnit.SECONDS.sleep(tryRecordSec);
                    } catch (InterruptedException ignored) {
                    } finally {
                        if (tryRecordSec < 10) {
                            tryRecordSec++;
                        } else {
                            tryRecordSec = 1;
                        }
                    }
                }
                tryRecord(room);
            }

            @Override
            public void onFailure(Throwable e) {
                log.error("FLV录制发生异常：" + ThrowableUtil.getAllCauseMessage(e), e);
            }

            @Override
            public void onRecorderCreated(Recorder re) {
                if (recorder != null) {
                    recorder.stop(true);
                }
                recorder = re;
            }
        };
        RecorderTask task = new RecorderTask(setting);
        task.setActionListener(actionListener);
        return task;
    }


    private void tryRecord(R room) {
        trayIconUtil.setStartRecordStatue(false);
        try {
            roomMonitor.refresh(true);
        } catch (Exception e) {
            log.error("直播监听刷新异常：" + ThrowableUtil.getAllCauseMessage(e), e);
        }
        if (room.isLiving() && isRunning.get() && recordFlag.get()) {
            if (recorderTask != null) {
                recorderTask.run(room);
            }
        } else {
            if (recorder != null) {
                recorder.stop(false);
            }
        }
    }


    protected TrayIconUtil.ClickListener getClickListener() {

        return new TrayIconUtil.ClickListener() {
            @Override
            public boolean startRecordClick(ActionEvent e) {
                return startRecord();
            }

            @Override
            public boolean stopRecordClick(ActionEvent e) {
                return stopRecord();
            }

            @Override
            public boolean closeClick(ActionEvent e) {
                isForceStop.set(true);//退出监听循环
                exit();
                return !RunMode.FILE.equals(room.getSetting().getRunMode());//不要终止程序
            }

            @Override
            public void iconClick(ActionEvent e) {
                if (recorder == null || recorder.getSaveFilePath() == null) {
                    String home = System.getProperty("user.dir");
                    Path path = Paths.get(home, Constant.Application, room.getPlatform().getName(), "[" + room.getNickname() + "]");
                    if (Files.exists(path)) {
                        openDirectory(path.toString());
                    } else {
                        openDirectory(home);
                    }
                } else {
                    openDirectory(Paths.get(recorder.getSaveFilePath()).getParent().toString());
                }
            }

            @Override
            public String openWebClick(ActionEvent e) {
                return room.getRoomUrl();
            }

            @Override
            public String playVideo(ActionEvent e) {
                startFlvPlayer();
                if (room.isLiving()) {
                    return flvPlayer.getUrlFromUrl(room.getFlvUrl());
                } else {
                    return flvPlayer.getMainUrl();
                }
            }

            @Override
            public String openMonitor(ActionEvent e) {
                return System.getProperty("monitor.url");
            }
        };
    }

    public boolean stopRecord() {
        if (recorder != null && recorder.isRunning()) {
            recordFlag.set(false);
            recorder.stop(false);
            return true;
        }
        return false;
    }

    public boolean startRecord() {
        if (recorderTask == null) {
            recorderTask = getRecorderTask();
        }
        if (room.isLiving() && (recorder == null || !recorder.isRunning())) {
            recordFlag.set(true);
            recorderTask.run(room);
            return true;
        }
        return false;
    }

    public void exit() {
        stop();
        isRunning.set(false);
        if (recorder != null && recorder.isRunning()) {
            recordFlag.set(false);
            recorder.stop(false);
        }
        if (recorderTask != null) {
            recorderTask.shutdown();
        }
        while (flvToMp4 != null && flvToMp4.isRunning()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
        if (logUtil != null) logUtil.close();
        flvPlayer.stop(true);
    }

    public void xiZhiSendMsg(NotificationUtil notificationUtil, Room room) {
        String title = "**" + room.getNickname() + ", " + room.getPlatform().getName() + (room.isLiving() ? "开播了！ " + room.getTitle() : "下播了！") + "**\t\n";
        String webUrl = room.isLiving() ? "- 直播间地址: [进入直播间](" + room.getRoomUrl() + ")" : "";
        String playUrl = "";
        //B站请求头限制，无法在线播放
        if (room.isLiving() && room.getPlatform() != Room.Platform.Bili) {
            Map<String, String> streams = room.getStreams();
            Iterator<Map.Entry<String, String>> iterator = streams.entrySet().iterator();
            String qn, flvUrl;
            Map.Entry<String, String> entry = iterator.next();
            qn = entry.getKey();
            flvUrl = entry.getValue().startsWith("http:") ? entry.getValue().replace("http:", "https:") : entry.getValue();
            try {
//                String encode = URLEncoder.encode(flvUrl, "UTF-8");//使用URLEncoder编码
                String encode = EncryptUtil.enBase64Str(flvUrl);//使用Base64Encoder编码
                String url = "https://zhangheng0805.github.io/FLVPlayer/?url=" + encode;
                playUrl = "\t\n- 播放地址: [" + qn + " 纯享版在线观看](" + url + ")";
            } catch (Exception ignored) {
            }
        }
        try {
            String footer = "\t\n------\t\n"
                    + "\t\n **个人链接:**\t [微信公众号](" + Constant.WeChatOfficialAccount + ") / [Bilibili](https://b23.tv/fmqmfNv)"
//                    + " / [抖音](https://v.douyin.com/cubL5sg7sNE/)"
                    + " / [程序项目](https://github.com/ZhangHeng0805/LiveMonitoringRecording)";
            notificationUtil.xiZhiSendMsg(Constant.Application, URLEncoder.encode(title + webUrl + playUrl + footer, "UTF-8"));
        } catch (Exception e) {
            log.error("xiZhiSendMsg发生异常：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }
}
