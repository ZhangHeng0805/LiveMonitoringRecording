package cn.zhangheng.record.util;

import cn.hutool.http.HttpUtil;
import cn.zhangheng.record.bean.Room;
import com.zhangheng.util.TimeUtil;
import com.zhangheng.log.AsyncBatchLogger;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 05:00
 * @version: 1.0
 * @description:
 */
public class LogUtil {

    private static final Logger LOG = LoggerFactory.getLogger(LogUtil.class);

    /**
     * 封面下载专用守护线程池。
     * 封面只是附属文件，绝不能在监听线程里同步等待网络：一旦 CDN 连接半开，监听循环会被永久钉死。
     * 队列有界，满了直接丢弃（丢封面不影响监听）。
     */
    private static final ExecutorService COVER_DOWNLOADER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(20),
            r -> {
                Thread t = new Thread(r, "cover-downloader");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    @Getter
    private final Path logPath;
    private boolean isInit;

    private final AsyncBatchLogger logger;

    public static Path getBasePath(Room roomInfo) {
        return Paths.get(getBasePathStr(roomInfo));
    }

    public static String getBasePathStr(Room room) {
        if (room.getStartTime() == null) {
            throw new IllegalArgumentException("room start time is null");
        }
        return RoomUtils.getBaseSavePathStr(room);
    }

    {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    public LogUtil(Room room, String fileName) throws IOException {
        Path path = getBasePath(room);
        String basePath = path.toFile().getAbsolutePath();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        this.logPath = Paths.get(basePath, fileName);
        logger = new AsyncBatchLogger(logPath);
    }

    public LogUtil(String log_path) throws IOException {
        this.logPath = Paths.get(log_path);
        logger = new AsyncBatchLogger(logPath);
        if (!Files.exists(logPath.getParent())) {
            Files.createDirectories(logPath.getParent());
        }
    }


    /**
     * 直播监听日志 非通用
     *
     * @param room
     * @throws IOException
     */
    public LogUtil(Room room) throws IOException {
        String nowTime = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd HH-mm-ss");
        Path path = getBasePath(room);
        String basePath = path.toFile().getAbsolutePath();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        this.logPath = Paths.get(basePath, nowTime + "监听.log");
        logger = new AsyncBatchLogger(logPath);
        Path coverPath = Paths.get(basePath, nowTime + "封面.jpg");
        if (!Files.exists(coverPath) && room.getCover() != null) {
            String coverUrl = room.getCover();
            // 异步下载并设置 5 秒超时：Hutool 的 downloadFile 默认无超时，同步调用一旦遇到连接半开，
            // 会把整条监听线程永久阻塞在 socketRead 上（停止指令的中断对它也无效）
            COVER_DOWNLOADER.execute(() -> {
                if (Files.exists(coverPath)) {
                    return;//队列中的重复任务，文件已存在则跳过
                }
                try {
                    HttpUtil.downloadFile(coverUrl, coverPath.toFile(), 5000);
                } catch (Throwable e) {
                    LOG.warn("直播间[{}]封面下载失败（忽略，不影响监听）：{} - {}", room.getNickname(), coverUrl, e.getMessage());
                }
            });
        }
        if (room.isLiving()) {
            init(room);
        } else {
            log(room.getPlatform().getName() + "直播间: " + room.getNickname() + "[" + room.getId() + "],未开播！监听中...");
        }
    }

    public void init(Room room) {
        if (isInit) {
            return;
        }
        isInit = true;
        List<String> lines = new ArrayList<>();
        lines.add("【" + room.getNickname() + "】 - " + room.getTitle());
        lines.add("直播流地址：");
        if (room.getStreams() == null || room.getStreams().isEmpty()) {
            // 开播初期拿不到直播源时也要能正常建日志，避免空指针把监听判死
            lines.add("暂未获取到直播流地址");
        } else {
            for (Map.Entry<String, String> entry : room.getStreams().entrySet()) {
                lines.add("【" + entry.getKey() + "】 " + entry.getValue());
            }
        }
        lines.add("");
        log(lines);
        for (String s : lines) {
            System.out.println(s);
        }
    }

    public void log(Iterable<? extends CharSequence> lines) {
        logger.asyncLog(lines);
    }

    public void highLog(String info) {
        logger.highLog(info);
    }

    public void log(String info) {
        if (info == null) return; // 过滤空日志
        String line = TimeUtil.getNowTime() + " : " + info;
        logger.highLog(line);
        System.out.println(line);
    }

    /**
     * 使用完毕后必须调用
     */
    public void close() {
        if (logger != null) {
            logger.shutdown();
        }
    }

}
