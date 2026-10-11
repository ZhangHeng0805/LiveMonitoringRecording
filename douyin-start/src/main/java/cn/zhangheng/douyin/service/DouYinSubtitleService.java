package cn.zhangheng.douyin.service;

import cn.zhangheng.douyin.bean.DouYinRoomCounter;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.douyin.browser.DouYinWebcast;
import cn.zhangheng.douyin.subtitle.AssGenerator;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import cn.zhangheng.record.util.LogUtil;
import com.zhangheng.util.TimeUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/07 星期三 11:21
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinSubtitleService {
    private final DouYinRoom room;
    private DouYinWebcast webcast = null;
    private LogUtil subtitleLog = null;
    private LogUtil chatLog = null;

    public DouYinSubtitleService(DouYinRoom room) {
        this.room = room;
    }


    public void startSubtitle() {
        if (!room.isLiving()) return;
        if (webcast != null && webcast.isRunning()) return;
        if (room.getSetting().isOpenSubtitle()) {
            if (room.getCounter() == null) {
                room.setCounter(new DouYinRoomCounter());
            }
            webcast = new DouYinWebcast(room);
        }
        if (webcast == null || webcast.isRunning()) return;
        if (subtitleLog == null || chatLog == null) {
            try {
                String time = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd HH-mm-ss");
                if (subtitleLog == null) subtitleLog = new LogUtil(room, time + "弹幕日志.log");
                if (chatLog == null) chatLog = new LogUtil(room, time + "聊天弹幕.log");
            } catch (IOException e) {
                log.error("弹幕日志生成失败！", e);
            }
        }

        webcast.setMessageListener(new MessageListener() {
            @Override
            public void chat(String info, DouyinMessageOuter.ChatMessage msg) {
                chatLog.highLog(info);
            }

            @Override
            public void member(String info, DouyinMessageOuter.MemberMessage msg) {
                String x = "【进场】" + info;
                subtitleLog.highLog(x);
            }

            @Override
            public void gift(String info, DouyinMessageOuter.GiftMessage msg) {
                String x = "【礼物】" + info;
                subtitleLog.highLog(x);
                room.getCounter().setTotalGift(info);
            }

            @Override
            public void like(String info, DouyinMessageOuter.LikeMessage msg) {
                String x = "【点赞】" + info;
                room.setLikeCount(msg.getTotal());
                subtitleLog.highLog(x);
            }

            @Override
            public void social(String info, DouyinMessageOuter.SocialMessage msg) {
                String x = "【关注】" + info;
                subtitleLog.highLog(x);
            }

            @Override
            public void stats(String info, DouyinMessageOuter.RoomUserSeqMessage msg) {
                String x = "【统计】" + info;
                room.setTotalUserStr(msg.getTotalUser() + "");
                subtitleLog.highLog(x);
            }

            @Override
            public void online(String info, DouyinMessageOuter.RoomStatsMessage msg) {
                String x = "【在线】" + info;
                long total = msg.getTotal();
                if (total > 0) {
                    room.setUserCountStr(total + "");
                    room.getCounter().setMaxOnlineUsers(total);
                }
                subtitleLog.highLog(x);
            }

            @Override
            public void roomRank(String info, DouyinMessageOuter.RoomRankMessage msg) {
                String x = "【排名】" + info;
                subtitleLog.highLog(x);
            }

            @Override
            public void control(String info, DouyinMessageOuter.ControlMessage msg) {
                String x = "【状态】" + info;
                x += room.getCounter().toString();
                subtitleLog.highLog(x);
                if (msg.getStatus() == 3) {
                    stopSubtitle();
                }
            }
        });

        webcast.start();
//        log.info("开启弹幕日志记录!{}", subtitleLog.getLogPath());
    }

    public synchronized void stopSubtitle() {
        if (webcast != null && webcast.isRunning() && subtitleLog != null) {
            webcast.stop();
            chatLog.close();
            subtitleLog.close();
            log.info("关闭弹幕日志记录!{}", subtitleLog.getLogPath());
            try {
                Path logPath = chatLog.getLogPath();
                if (Files.exists(logPath)) {
                    new AssGenerator(10, logPath.toString()).generate();
                }
            } catch (IOException e) {
                log.error("弹幕日志转换成字幕文件失败", e);
            } finally {
                webcast = null;
                chatLog = null;
                subtitleLog = null;
            }
        }
    }
}
