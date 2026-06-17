package cn.zhangheng.douyin.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.service.RoomService;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.douyin.bean.DouYinCounter;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.douyin.browser.DouYinWebcast;
import cn.zhangheng.douyin.browser.DouyinPlaywright;
import cn.zhangheng.douyin.subtitle.AssGenerator;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 03:01
 * @version: 1.0
 * @description:
 */
public class DouYinRoomService extends RoomService<DouYinRoom> {

    //    private String cookieStr;
    private Map<String, String> headers;
    private String url;
    private DouYinWebcast webcast = null;
    private LogUtil subtitleLog;
    private LogUtil chatLog;

    public DouYinRoomService(DouYinRoom room) {
        super(room);
//        this.browser = new DouYinBrowser(room);
//        refresh();

    }

    public static void main(String[] args) {
        long sta = System.currentTimeMillis();
        Setting setting = new Setting();
        DouYinRoom info = new DouYinRoom("541976200890");
//        DouYinRoom info = new DouYinRoom("兰小美认证信息.txt");
//        DouYinRoom info = new DouYinRoom("870887192950-7547373102502578971");
//        DouYinRoom info = new DouYinRoom("816560967344-7549211978108029696-uu子.txt");
        info.setSetting(setting);
        DouYinRoomService douYinRoomService = new DouYinRoomService(info);
//        douYinRoomService.refresh();
        System.out.println("耗时：" + (System.currentTimeMillis() - sta));
        System.out.println(JSONUtil.toJsonPrettyStr(info));
    }


    private void init() {
        url = room.getBrowserApi().getDataUrl();
        headers = room.getBrowserApi().getHeaders();
        headers.put("accept-language", "zh-CN,zh;q=0.9");
        headers.remove("accept-encoding");
//        cookieStr = room.getCookie();
//        if (StrUtil.isBlank(cookieStr)) {
//            cookieStr = "ttwid=1%7CB1qls3GdnZhUov9o2NxOMxxYS2ff6OSvEWbv0ytbES4%7C1680522049%7C280d802d6d478e3e78d0c807f7c487e7ffec0ae4e5fdd6a0fe74c3c6af149511; my_rd=1; passport_csrf_token=3ab34460fa656183fccfb904b16ff742; passport_csrf_token_default=3ab34460fa656183fccfb904b16ff742; d_ticket=9f562383ac0547d0b561904513229d76c9c21; n_mh=hvnJEQ4Q5eiH74-84kTFUyv4VK8xtSrpRZG1AhCeFNI; store-region=cn-fj; store-region-src=uid; LOGIN_STATUS=1; __security_server_data_status=1; FORCE_LOGIN=%7B%22videoConsumedRemainSeconds%22%3A180%7D; pwa2=%223%7C0%7C3%7C0%22; download_guide=%223%2F20230729%2F0%22; volume_info=%7B%22isUserMute%22%3Afalse%2C%22isMute%22%3Afalse%2C%22volume%22%3A0.6%7D; strategyABtestKey=%221690824679.923%22; stream_recommend_feed_params=%22%7B%5C%22cookie_enabled%5C%22%3Atrue%2C%5C%22screen_width%5C%22%3A1536%2C%5C%22screen_height%5C%22%3A864%2C%5C%22browser_online%5C%22%3Atrue%2C%5C%22cpu_core_num%5C%22%3A8%2C%5C%22device_memory%5C%22%3A8%2C%5C%22downlink%5C%22%3A10%2C%5C%22effective_type%5C%22%3A%5C%224g%5C%22%2C%5C%22round_trip_time%5C%22%3A150%7D%22; VIDEO_FILTER_MEMO_SELECT=%7B%22expireTime%22%3A1691443863751%2C%22type%22%3Anull%7D; home_can_add_dy_2_desktop=%221%22; __live_version__=%221.1.1.2169%22; device_web_cpu_core=8; device_web_memory_size=8; xgplayer_user_id=346045893336; csrf_session_id=2e00356b5cd8544d17a0e66484946f28; odin_tt=724eb4dd23bc6ffaed9a1571ac4c757ef597768a70c75fef695b95845b7ffcd8b1524278c2ac31c2587996d058e03414595f0a4e856c53bd0d5e5f56dc6d82e24004dc77773e6b83ced6f80f1bb70627; __ac_nonce=064caded4009deafd8b89; __ac_signature=_02B4Z6wo00f01HLUuwwAAIDBh6tRkVLvBQBy9L-AAHiHf7; ttcid=2e9619ebbb8449eaa3d5a42d8ce88ec835; webcast_leading_last_show_time=1691016922379; webcast_leading_total_show_times=1; webcast_local_quality=sd; live_can_add_dy_2_desktop=%221%22; msToken=1JDHnVPw_9yTvzIrwb7cQj8dCMNOoesXbA_IooV8cezcOdpe4pzusZE7NB7tZn9TBXPr0ylxmv-KMs5rqbNUBHP4P7VBFUu0ZAht_BEylqrLpzgt3y5ne_38hXDOX8o=; msToken=jV_yeN1IQKUd9PlNtpL7k5vthGKcHo0dEh_QPUQhr8G3cuYv-Jbb4NnIxGDmhVOkZOCSihNpA2kvYtHiTW25XNNX_yrsv5FN8O6zm3qmCIXcEe0LywLn7oBO2gITEeg=; tt_scid=mYfqpfbDjqXrIGJuQ7q-DlQJfUSG51qG.KUdzztuGP83OjuVLXnQHjsz-BRHRJu4e986";
//        }
//        headers.put("cookie", cookieStr);
//        System.out.println(JSONUtil.parseObj(headers).toStringPretty());
    }


    @Override
    protected boolean refresh(boolean force) {
        boolean success = false;
        if (!room.isLiving() || room.getBrowserApi().getDataUrl() == null) {
            if (RunMode.FILE.equals(room.getSetting().getRunMode())) {
                success = DouYinBrowserFactory.getBrowser().request(room);
            } else {
                success = DouyinPlaywright.request(room);
            }
        }
        if (room.isLiving() && room.getBrowserApi().getDataUrl() != null) {
            if (RunMode.FILE.equals(room.getSetting().getRunMode())) {
                DouYinBrowserFactory.getBrowser().closeContext();
            }
            getData(force);
        }
        return success;
    }

    public void getData(boolean force) {
        if (url == null) {
            init();
        }
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest(url, headers);
            String body = RequestUtils.responseAsString(connection);
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject entries = JSONUtil.parseObj(body);
                JSONObject data = entries.getJSONObject("data");
                Integer code = entries.getInt("status_code", -1);
                if (code == 0) {
                    room.setUpdateTime(new Date());
                    room.setLiving(data.getInt("room_status", -1) == 0);
                    if (force || StrUtil.isBlank(room.getNickname())) {
                        room.setNickname(data.getJSONObject("user").getStr("nickname"));
                    }
                    if (force || StrUtil.isBlank(room.getAvatar())) {
                        room.setAvatar(data.getJSONObject("user")
                                .getJSONObject("avatar_thumb")
                                .getJSONArray("url_list")
                                .get(0, String.class));
                    }
                    if (room.isLiving()) {
                        if (room.getStartTime() == null) {
                            room.setStartTime(new Date());
                        }
                        JSONObject data1 = data.getJSONArray("data").getJSONObject(0);
                        if (force || StrUtil.isBlank(room.getTitle())) {
                            room.setTitle(data1.getStr("title", ""));
                        }
                        if (force || room.getCoverList() == null) {
                            room.setCoverList(data1.getJSONObject("cover").getBeanList("url_list", String.class));
                        }

                        JSONObject stream_data = data1.getJSONObject("stream_url").getJSONObject("live_core_sdk_data").getJSONObject("pull_data");
                        if (force || room.getStreams() == null) {
                            room.setStreams(handleStream(stream_data));
                        }
                        JSONObject stats = data1.getJSONObject("stats");
                        boolean b = !room.isSubtitleRunning();
                        if (room.getTotalUserStr() == null || b) {
                            room.setTotalUserStr(stats.getStr("total_user_str"));
                        }
                        if (room.getUserCountStr() == null || b) {
                            room.setUserCountStr(stats.getStr("user_count_str"));
                        }
                        if (room.getLikeCount() == 0 || b) {
                            room.setLikeCount(data1.getLong("like_count"));
                        }
                    }
                } else {
                    String msg = data.getStr("prompts");
                    log.warn("refresh<" + room.getId() + ">: (" + code + ") " + msg);
                    if (msg.contains("直播已结束")) {
                        room.setLiving(false);
                    }
                    if (!code.equals(30003) || !"直播已结束".endsWith(msg)) {
                        throw new RuntimeException(room.getPlatform().getName() + " [" + room.getId() + "]刷新异常：" + msg);
                    }
                }
            } else {
                int responseCode = -1;
                responseCode = connection.getResponseCode();
                System.err.println(Thread.currentThread().getName() + "-" + responseCode + ": " + body);
            }
        } catch (Exception e) {
            log.error("请求刷新失败！{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Map<String, String> handleStream(JSONObject stream_data) {
        Map<String, String> map = new LinkedHashMap<>();
        JSONArray qualities = stream_data.getJSONObject("options").getJSONArray("qualities");
        JSONObject data = JSONUtil.parseObj(stream_data.getStr("stream_data")).getJSONObject("data");
        for (int i = qualities.size() - 1; i >= 0; i--) {
            JSONObject entries = qualities.getJSONObject(i);
            String name = entries.getStr("name");
            String sdk_key = entries.getStr("sdk_key");
            String url = data.getJSONObject(sdk_key).getJSONObject("main").getStr("flv");
            map.put(name, url);
        }
        return map;
    }


    @Override
    public void startSubtitle() {
        if (webcast != null && webcast.isRunning()) return;
        if (room.getSetting().isOpenSubtitle()) {
            room.setCounter(new DouYinCounter());
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

    @Override
    public synchronized void stopSubtitle() {
        if (webcast != null && webcast.isRunning() && subtitleLog != null) {
            webcast.stop();
            chatLog.close();
            subtitleLog.close();
            try {
                Path logPath = chatLog.getLogPath();
                if (Files.exists(logPath)) {
                    new AssGenerator(10, logPath.toString()).generate();
                }
            } catch (IOException e) {
                log.error("弹幕日志转换成字幕文件失败", e);
            }
            log.info("关闭弹幕日志记录!{}", subtitleLog.getLogPath());
        }
    }
}
