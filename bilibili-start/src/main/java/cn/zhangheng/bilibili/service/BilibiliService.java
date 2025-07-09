package cn.zhangheng.bilibili.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.RoomService;
import com.zhangheng.util.TimeUtil;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/25 星期日 22:24
 * @version: 1.0
 * @description:
 */
public class BilibiliService extends RoomService<BiliRoom> {


    private final Map<Integer, String> qn = new HashMap<>();

    public static void main(String[] args) {
        BiliRoom biliRoom = new BiliRoom("55");
        BilibiliService service = new BilibiliService(biliRoom);
        System.out.println(biliRoom.getNickname() + ":" + biliRoom.getAvatar());
    }

    public BilibiliService(BiliRoom room) {
        super(room);
        initQn();
        room_init_living();
        room_info();
        user_info();
        if (room.isLiving()) {
            room_stream();
        }
    }

    private void initQn() {
        qn.put(30000, "杜比");
        qn.put(20000, "4K");
        qn.put(10000, "原画");
        qn.put(400, "蓝光");
        qn.put(250, "超清");
        qn.put(150, "高清");
        qn.put(80, "流畅");
    }

    @Override
    public void refresh(boolean force) {
        if (!room.isLiving()){
            room_init_living();
        }
        if (room.isLiving()) {
            room_info();
            if (force || room.getStreams() == null || room.getStreams().isEmpty()) room_stream();
        }
    }

    public void room_init_living() {
        HttpRequest header = get("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + room.getId());
        if (room.getCookie() != null) {
            header = header.header("Cookie", room.getCookie());
        }
        try (HttpResponse execute = header.execute()) {
            String body = execute.body();
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject entries = JSONUtil.parseObj(body);
                Integer code = entries.getInt("code");
                if (code == 0) {
                    JSONObject data = entries.getJSONObject("data");
                    boolean isLiving = data.getInt("live_status") != 0;
                    room.setLiving(isLiving);
                    if (room.getRoom_id() == null) {
                        room.setRoom_id(data.getStr("room_id"));
                        room.setUid(data.getStr("uid"));
                    }
                } else {
                    String msg = entries.getStr("message");
                    log.warn("room_init_living失败<" + room.getId() + ">: (" + code + ") " + msg);
                    throw new RuntimeException(room.getPlatform().getName() + " [" + room.getId() + "]刷新异常：" + msg);
                }
            }
        }
    }

    private void room_info() {
        HttpRequest header = get("https://api.live.bilibili.com/room/v1/Room/get_info?id=" + room.getId());
        if (room.getCookie() != null) {
            header = header.header("Cookie", room.getCookie());
        }
        try (HttpResponse execute = header.execute()) {
            String body = execute.body();
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject entries = JSONUtil.parseObj(body);
                if (entries.getInt("code") == 0) {
                    JSONObject data = entries.getJSONObject("data");
                    boolean isLiving = data.getInt("live_status") != 0;
                    room.setLiving(isLiving);
                    if (room.getRoom_id() == null) {
                        room.setRoom_id(data.getStr("room_id"));
                        room.setUid(data.getStr("uid"));
                    }
                    if (room.getCover() == null) {
                        room.setCover(data.getStr("user_cover"));
                        room.setTitle(data.getStr("title"));
                    }
                    if (isLiving) {
                        if (room.getStartTime() == null) {
                            try {
                                room.setStartTime(TimeUtil.toDate(data.getStr("live_time")));
                            } catch (ParseException e) {
                                room.setStartTime(new Date());
                            }
                        }
                        room.setViewers(data.getInt("online"));
                    }
                } else {
                    log.warn("room_info失败：" + entries.getStr("message"));
                }
            }
        }
    }

    private void user_info() {
        HttpRequest header = get("https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + room.getUid());
        if (room.getCookie() != null) {
            header = header.header("Cookie", room.getCookie());
        }
        try (HttpResponse execute = header.execute()) {
            String body = execute.body();
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject entries = JSONUtil.parseObj(body);
                if (entries.getInt("code") == 0) {
                    JSONObject data = entries.getJSONObject("data");
                    JSONObject info = data.getJSONObject("info");
                    room.setNickname(info.getStr("uname"));
                    room.setAvatar(info.getStr("face"));
                    room.setFollowers(data.getInt("follower_num", 0));
                } else {
                    log.warn("user_info失败：" + entries.getStr("message"));
                }
            }
        }
    }

    //    private void room_stream1() {
//        try (HttpResponse execute = HttpRequest.get("https://api.live.bilibili.com/room/v1/Room/playUrl?quality=4&cid=" + room.getRoom_id()).header("User-Agent", Constant.User_Agent).execute()) {
//            String body = execute.body();
//            if (JSONUtil.isTypeJSON(body)) {
//                JSONObject entries = JSONUtil.parseObj(body);
//                if (entries.getInt("code") == 0) {
//                    JSONObject data = entries.getJSONObject("data");
//                    Integer currentQuality = data.getInt("current_quality");
//                    String desc = "原画";
//                    for (int i = 0; i < data.getJSONArray("quality_description").size(); i++) {
//                        JSONObject entries1 = data.getJSONArray("quality_description").getJSONObject(i);
//                        if (entries1.getInt("qn").equals(currentQuality)) {
//                            desc = entries1.getStr("desc");
//                        }
//                    }
//                    LinkedHashMap<String, String> streams = new LinkedHashMap<>();
//                    for (int i = 0; i < data.getJSONArray("durl").size(); i++) {
//                        JSONObject entries1 = data.getJSONArray("durl").getJSONObject(i);
//                        streams.put(desc + entries1.getInt("order"), entries1.getStr("url"));
//                    }
//                    room.setStreams(streams);
//                } else {
//                    log.warn("room_stream失败：" + entries.getStr("message"));
//                }
//            }
//        }
//    }
    private void room_stream() {
        room_stream(true);
    }

    private void room_stream(boolean isCookie) {
        HttpRequest header = get("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?protocol=0&format=0&codec=0&qn=30000&room_id=" + room.getRoom_id());
        if (room.getCookie() != null && isCookie) {
            header = header.header("Cookie", room.getCookie());
        }
        try (HttpResponse execute = header.execute()) {
            String body = execute.body();
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject entries = JSONUtil.parseObj(body);
                if (entries.getInt("code") == 0) {
                    JSONObject data = entries.getJSONObject("data");
                    JSONObject codec = data.getJSONObject("playurl_info").getJSONObject("playurl").getJSONArray("stream").getJSONObject(0).getJSONArray("format").getJSONObject(0).getJSONArray("codec").getJSONObject(0);
                    String desc = qn.get(codec.getInt("current_qn", 10000));
                    String base_url = codec.getStr("base_url");
                    LinkedHashMap<String, String> streams = room.getStreams() != null ? room.getStreams() : new LinkedHashMap<>();
                    JSONObject urls = codec.getJSONArray("url_info").getJSONObject(0);
                    String url = urls.getStr("host") + base_url + urls.getStr("extra");
                    streams.put(desc, url);
                    room.setStreams(streams);
                    if (isCookie) room_stream(false);
                } else {
                    log.warn("room_stream失败：" + entries.getStr("message"));
                }
            }
        }
    }


}
