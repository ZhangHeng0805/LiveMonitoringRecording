package cn.zhangheng.douyin;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.RoomService;

import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 03:01
 * @version: 1.0
 * @description:
 */
public class DouYinRoomService extends RoomService<DouYinRoom> {

    private String cookieStr;
    private Map<String, Object> forms;

    public DouYinRoomService(DouYinRoom room) {
        super(room);
        refresh();
    }

    public static void main(String[] args) {
        DouYinRoom info = new DouYinRoom("648541186");
        DouYinRoomService douYinRoomService = new DouYinRoomService(info);
        System.out.println(info.getNickname()+":"+info.getAvatar());
    }

    private void init() {
        if (StrUtil.isBlank(cookieStr)) {
            try (HttpResponse execute = get("https://live.douyin.com/").execute()) {
                this.cookieStr = execute.getCookieStr();
            }
        }
        if (forms == null) {
            forms = new HashMap<>();
            forms.put("web_rid", room.getId());
            forms.put("aid", 6383);
            forms.put("live_id", 1);
            forms.put("device_platform", "web");
            forms.put("language", "zh-CN");
            forms.put("enter_from", "web_live");
            forms.put("cookie_enabled", "true");
            forms.put("screen_width", 1920);
            forms.put("screen_height", 1080);
            forms.put("browser_language", "zh-CN");
            forms.put("browser_platform", "MacIntel");
            forms.put("browser_name", "Chrome");
            forms.put("browser_version", "108.0.0.0");
            forms.put("Room-Enter-User-Login-Ab", 0);
            forms.put("is_need_double_stream", "false");
        }
    }

    @Override
    public void refresh(boolean force) {
        if (cookieStr == null) {
            init();
        }
        String body = "";
        HttpRequest request = get("https://live.douyin.com/webcast/room/web/enter/")
                .header("Cookie", cookieStr)
                .form(forms);
        try (HttpResponse execute = request.execute()) {
            body = execute.body();
            if (JSONUtil.isTypeJSON(body)) {
                JSONObject data = JSONUtil.parseObj(body).getJSONObject("data");
                room.setLiving(data.getInt("room_status", -1) == 0);
                if (StrUtil.isBlank(room.getNickname())) {
                    room.setNickname(data.getJSONObject("user").getStr("nickname"));
                }
                if (room.getAvatar() == null) {
                    room.setAvatar(data.getJSONObject("user").getJSONObject("avatar_thumb").getJSONArray("url_list").get(0, String.class));
                }
                if (room.isLiving()) {
                    if (room.getStartTime() == null) {
                        room.setStartTime(new Date());
                    }
                    JSONObject data1 = data.getJSONArray("data").getJSONObject(0);
                    if (StrUtil.isBlank(room.getTitle())) {
                        room.setTitle(data1.getStr("title", ""));
                    }
                    if (room.getCoverList() == null) {
                        room.setCoverList(data1.getJSONObject("cover").getBeanList("url_list", String.class));
                    }

                    JSONObject stream_data = data1.getJSONObject("stream_url").getJSONObject("live_core_sdk_data").getJSONObject("pull_data");
                    if (force || room.getStreams() == null) {
                        room.setStreams(handleStream(stream_data));
                    }
                    JSONObject stats = data1.getJSONObject("stats");
                    room.setTotalUserStr(stats.getStr("total_user_str"));
                    room.setUserCountStr(stats.getStr("user_count_str"));
                    room.setLikeCount(data1.getInt("like_count"));
                }
            }
        } catch (Exception e) {
            System.err.println(body);
            throw e;
        }

    }

    private LinkedHashMap<String, String> handleStream(JSONObject stream_data) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
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
}
