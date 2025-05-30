package cn.zhangheng.common.util;

import cn.hutool.http.HttpUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.TimeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 05:00
 * @version: 1.0
 * @description:
 */
public class LogUtil {


    private final Path logPath;
    private boolean isInit;

    public static Path getBasePath(Room roomInfo) {
        return Paths.get(getBasePathStr(roomInfo));
    }

    public static String getBasePathStr(Room room) {
        String nowTime = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd");
        return Constant.Application + "/" + room.getPlatform().getName() + "/[" + FileUtil.filterFileName(room.getOwner()) + "]/" + nowTime;
    }

    public LogUtil(Room room, String fileName) throws IOException {
        Path path = getBasePath(room);
        String basePath = path.toFile().getAbsolutePath();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        this.logPath = Paths.get(basePath, fileName);
    }

    public LogUtil(String log_path) throws IOException {
        this.logPath = Paths.get(log_path);
        if (!Files.exists(logPath.getParent())) {
            Files.createDirectories(logPath.getParent());
        }
    }


    public LogUtil(Room room) throws IOException {
        String nowTime = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd HH-mm-ss");
        Path path = getBasePath(room);
        String basePath = path.toFile().getAbsolutePath();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        this.logPath = Paths.get(basePath, nowTime + "监听.log");
        Path coverPath = Paths.get(basePath, "cover.jpg");
        if (!Files.exists(coverPath)) {
            HttpUtil.downloadFile(room.getCover(), coverPath.toFile());
        }
        if (room.isLiving()) {
            init(room);
        } else {
            log(room.getPlatform().getName() + "直播间: " + room.getOwner() + "[" + room.getId() + "],未开播！监听中...");
        }
    }

    public void init(Room room) throws IOException {
        if (isInit) {
            return;
        }
        isInit = true;
        List<String> lines = new ArrayList<>();
        lines.add("【" + room.getOwner() + "】 - " + room.getTitle());
        lines.add("直播流地址：");
        for (Map.Entry<String, String> entry : room.getStreams().entrySet()) {
            lines.add("【" + entry.getKey() + "】 " + entry.getValue());
        }
        lines.add("");
        log(lines);
        for (String s : lines) {
            System.out.println(s);
        }
    }

    public void log(Iterable<? extends CharSequence> lines) throws IOException {
        Files.write(logPath, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void log(String info) throws IOException {
        String line = TimeUtil.getNowTime() + " : " + info;
        System.out.println(line);
        log(Collections.singleton(line));
    }


}
