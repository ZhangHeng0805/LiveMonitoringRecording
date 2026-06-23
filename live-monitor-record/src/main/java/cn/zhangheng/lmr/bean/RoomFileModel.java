package cn.zhangheng.lmr.bean;

import cn.zhangheng.lmr.Main;
import lombok.Data;

import java.nio.file.Path;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/11 星期一 17:56
 * @version: 1.0
 * @description:
 */
@Data
public class RoomFileModel {
    private String id;
    private Path filePath;
    private long startTime;
    private long endTime;
    private Main main;
    private boolean isRunning;

    public void setStartTime() {
        this.startTime = System.currentTimeMillis();
        isRunning = true;
    }

    public void setEndTime() {
        this.endTime = System.currentTimeMillis();
        isRunning = false;
    }
}
