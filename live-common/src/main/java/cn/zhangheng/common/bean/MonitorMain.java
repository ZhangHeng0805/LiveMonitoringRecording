package cn.zhangheng.common.bean;

import cn.zhangheng.common.record.FFmpegFlvRecorder;
import cn.zhangheng.common.record.FlvStreamRecorder;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.common.util.NotificationUtil;
import cn.zhangheng.common.util.TrayIconUtil;
import cn.zhangheng.common.video.FlvToMp4;
import cn.zhangheng.common.video.player.LocalServerFlvPlayer;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.EncryptUtil;
import com.zhangheng.util.NetworkUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
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

import static cn.zhangheng.common.util.TrayIconUtil.openDirectory;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 05:58
 * @version: 1.0
 * @description:
 */
public abstract class MonitorMain<R extends Room, M extends RoomMonitor<R, ?>> {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final TrayIconUtil trayIconUtil;
    protected final Setting setting;
    protected Recorder recorder;
    protected int delayIntervalSec;
    protected volatile boolean isRunning;
    protected boolean recordFlag = true;
    protected final boolean isConvert;
    //0-FlvStreamRecorder,1-FFmpegFlvRecorder
    protected final int recorderType;
    protected R room;
    protected FlvToMp4 flvToMp4;
    private M roomMonitor;
    private final LocalServerFlvPlayer flvPlayer;
    private int tryMonitorSec = 1;
    private int tryRecordSec = 1;


    public MonitorMain(Setting setting) {
        this.trayIconUtil = new TrayIconUtil(Constant.Application);
        this.setting = setting;
        delayIntervalSec = setting.getDelayIntervalSec();
        isConvert = setting.isConvertFlvToMp4();
        recorderType = setting.getRecordType();
        flvPlayer = new LocalServerFlvPlayer(setting.getFlvPlayerPort());
        startFlvPlayer();
        if (isConvert) {
            try {
                flvToMp4 = new FlvToMp4(setting.getFfmpegPath());
            } catch (IllegalArgumentException e) {
                log.warn(e.getMessage());
            }
        }
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
        if (isRunning) {
            roomMonitor.stop(false);
        }
    }

    protected abstract M getRoomMonitor(R room);

    public void start(R room, boolean isRecord) {
        try {
            room.initSetting(setting);
            this.room = room;
            roomMonitor = getRoomMonitor(room);
            Thread.currentThread().setName(room.getNickname() + "-main-" + Thread.currentThread().getId());
            RoomMonitor.RoomListener<R> listener = getRoomListener(room, isRecord);
            trayIconUtil.setClickListener(getClickListener());
            roomMonitor.setListener(listener);
            do {
                try {
                    roomMonitor.run(false);
                } catch (Exception e) {
                    log.error("直播间监听出现异常: {}", ThrowableUtil.getAllCauseMessage(e), e);
                    try {
                        TimeUnit.SECONDS.sleep(tryMonitorSec);
                    } catch (InterruptedException ignored) {
                    } finally {
                        if (tryMonitorSec < 10)
                            tryMonitorSec++;
                    }
                }
            } while (isRunning);
        } finally {
            trayIconUtil.shutdown();
        }
    }

    private M.RoomListener<R> getRoomListener(R room, boolean isRecord) {
        NotificationUtil notificationUtil = new NotificationUtil(setting);
        String owner = room.getPlatform().getName() + "直播间: " + room.getNickname() + " [" + room.getId() + "]";
        LogUtil[] logUtils = {null};
        return new M.RoomListener<R>() {
            @Override
            public void onStart() {
                log.info(Constant.Application + "开始监听! {}", owner);
                isRunning = true;
                tryMonitorSec = 1;
            }

            @Override
            public void onStop() {
                isRunning = false;
                String msg = "直播监听结束！" + owner;
                log.info(msg);
                if (recorder != null && recorder.isRunning()) {
                    recorder.stop(false);
                }

                while (flvToMp4 != null && flvToMp4.isRunning()) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ignored) {
                    }
                }
                flvPlayer.stop(true);
                trayIconUtil.notifyMessage(msg);
                xiZhiSendMsg(notificationUtil, room);
            }

            @Override
            public void onChange(M.State state, R room) {
                if (state == M.State.NOT_LIVING) {
                    //未开播
                    String msg = owner + "\n未开播，直播间监听中...";
                    trayIconUtil.notifyMessage(msg);
                    trayIconUtil.setToolTip(msg);
                    log.info(msg);
                } else {
                    //已开播
                    boolean isFirst = recorder == null;
                    if (isRecord) {
                        recorder = getRecord(room, isConvert);
                        try {
                            recorder.run(true);
                        } catch (ExecutionException e) {
                            log.error("FLV录制发生异常：" + ThrowableUtil.getAllCauseMessage(e));
                        }
                    }
                    String msg = owner + "，已开始直播了！";
                    log.info(msg);
                    try {
                        if (logUtils[0] == null) {
                            try {
                                logUtils[0] = new LogUtil(room);
                            } catch (IOException e) {
                                log.warn("统计日志生成异常：{}", ThrowableUtil.getAllCauseMessage(e));
                            }
                        }
                        logUtils[0].log(msg);
                        logUtils[0].init(room);
                    } catch (IOException e) {
                        log.warn("统计日志产生异常：{}", ThrowableUtil.getAllCauseMessage(e));
                    }

                    if (isRecord) {
                        msg += "\n已开启【" + recorder.getDefinition() + "】录制！";
                    } else {
                        msg += "\n未开启录制！";
                        trayIconUtil.setStartRecordStatue(false);
                    }
                    trayIconUtil.notifyMessage(msg);
                    trayIconUtil.setToolTip(msg);
                    if (isFirst) {
                        xiZhiSendMsg(notificationUtil, room);
                        notificationUtil.weChatSendMsg(msg);
                    }
                }
            }

            @Override
            public void onProgress(R r) {
                String statistics = owner + "\n" + statistics(logUtils[0], r);
                if (recorder != null && recorder.isRunning() && recorder.getStartTime() != null) {
                    String tooltip = "【" + recorder.getDefinition() + "】" + recorder.getProgressMsg();
                    trayIconUtil.setToolTip(statistics + "\n" + tooltip);
                    log.info(tooltip);
                } else {
                    trayIconUtil.setToolTip(statistics);
                }
            }
        };
    }

    protected abstract String statistics(LogUtil logUtil, R room);


//    protected abstract FlvStreamRecorder getRecord(R room, boolean isConvert);

    protected Recorder getRecord(R room, boolean isConvert) {
        LinkedHashMap<String, String> streams = room.getStreams();
        Map.Entry<String, String> stream = streams.entrySet().iterator().next();
        String definition = stream.getKey();//清晰度
        String flvUrl = stream.getValue();
        String fileName = "【" + FileUtil.filterFileName(room.getNickname()) + "】" + room.getPlatform().getName() + "直播录制" + TimeUtil.toTime(new Date(), "yyyy-MM-dd HH-mm-ss") + "[" + FileUtil.filterFileName(room.getTitle()) + "].flv";
        String path = Paths.get(LogUtil.getBasePathStr(room), fileName).toFile().getPath();
        Recorder streamRecorder;
        switch (recorderType) {
            case 1:
                try {
                    String ffmpegPath = setting.getFfmpegPath();
                    streamRecorder = new FFmpegFlvRecorder(flvUrl, path, definition, ffmpegPath);
                } catch (IllegalArgumentException e) {
                    log.warn(e.getMessage());
                    streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
                }
                break;
            default:
                streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
                break;
        }
        FlvStreamRecorder.ProgressCallback progressCallback = new Recorder.ProgressCallback() {
            @Override
            public void onStart(String url, String saveFilePath, String definition) {
                Recorder.ProgressCallback.super.onStart(url, saveFilePath, definition);
                trayIconUtil.setStartRecordStatue(true);

            }

            @Override
            public void onComplete(String saveFilePath, long totalBytes, long totalDurationMS) {
                Recorder.ProgressCallback.super.onComplete(saveFilePath, totalBytes, totalDurationMS);
                flvToMp4(path);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
                tryRecord(room, isConvert);
            }

            @Override
            public void onError(Throwable throwable) {
                Recorder.ProgressCallback.super.onError(throwable);
                flvToMp4(path);
                if (throwable instanceof InterruptedException) {
                    return;
                }
                if (isRunning) {
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
                tryRecord(room, isConvert);
            }
        };

        streamRecorder.setProgressCallback(progressCallback);
        streamRecorder.setRoom(room);
        return streamRecorder;
    }

    private void flvToMp4(String path) {
        if (isConvert && flvToMp4 != null) {
            new Thread(() -> {
                String output = path.substring(0, path.lastIndexOf(".")) + ".mp4";
                boolean convert = flvToMp4.convert(path, output);
                if (convert) {
                    Thread.currentThread().setName(room.getNickname() + "-FlvToMp4-" + Thread.currentThread().getId());
                    try {
                        log.debug("FlvToMp4视频转换完成！删除源文件：" + path);
                        Files.deleteIfExists(Paths.get(path));
                        trayIconUtil.notifyMessage("录制文件保存路径：" + output);
                    } catch (IOException e) {
                        log.error("FlvToMp4视频转换完成后删除源文件失败！" + ThrowableUtil.getAllCauseMessage(e));
                    }
                }
            }).start();
        } else {
            trayIconUtil.notifyMessage("录制文件保存路径：" + path);
        }
    }


    private void tryRecord(R room, boolean isConvert) {
        trayIconUtil.setStartRecordStatue(false);
        roomMonitor.refresh(true);
        if (room.isLiving() && isRunning && recordFlag) {
            recorder.stop(true);
            log.warn("直播尚未结束，继续录制！");
            recorder = getRecord(room, isConvert);
            try {
                recorder.run(true);
            } catch (ExecutionException e) {
                log.error("重新录制失败！{}", ThrowableUtil.getAllCauseMessage(e), e);
            }
        } else {
            recorder.stop(false);
        }
    }


    protected TrayIconUtil.ClickListener getClickListener() {
        return new TrayIconUtil.ClickListener() {
            @Override
            public boolean startRecordClick(ActionEvent e) {
                if (recorder == null || !recorder.isRunning()) {
                    recordFlag = true;
                    if (recorder != null) {
                        recorder.stop(true);
                    }
                    try {
                        recorder = getRecord(room, isConvert);
                        recorder.run(true);
                    } catch (ExecutionException err) {
                        log.error("开始录制失败！" + ThrowableUtil.getAllCauseMessage(err));
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean stopRecordClick(ActionEvent e) {
                if (recorder != null && recorder.isRunning()) {
                    recordFlag = false;
                    recorder.stop(false);
                    return true;
                }
                return false;
            }

            @Override
            public boolean closeClick(ActionEvent e) {
                flvPlayer.stop(false);
                if (recorder != null && recorder.isRunning()) {
                    recordFlag = false;
                    recorder.stop(false);
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException ignored) {
                    }
                }
                return true;
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
        };
    }


    public void xiZhiSendMsg(NotificationUtil notificationUtil, Room room) {
        String title = "**" + room.getNickname() + ", " + room.getPlatform().getName() + (room.isLiving() ? "开播了！ " + room.getTitle() : "下播了！") + "**\t\n";
        String webUrl = room.isLiving() ? "- 直播间地址: [" + room.getRoomUrl() + "](" + room.getRoomUrl() + ")" : "";
        String playUrl = "";
        //B站请求头限制，无法在线播放
        if (room.isLiving() && room.getPlatform() != Room.Platform.Bili) {
            LinkedHashMap<String, String> streams = room.getStreams();
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
                    + " / [程序项目](https://github.com/ZhangHeng0805/LiveMonitoringRecording)"
                    ;
            notificationUtil.xiZhiSendMsg(Constant.Application, URLEncoder.encode(title + webUrl + playUrl + footer, "UTF-8"));
        } catch (Exception e) {
            log.error("xiZhiSendMsg发生异常：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }
}
