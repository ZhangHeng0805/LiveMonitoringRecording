package cn.zhangheng.kuaishou.service;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.RoomService;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import cn.zhangheng.kuaishou.util.InitialStateExtractor;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 19:09
 * @version: 1.0
 * @description:
 */
public class KuaiShouService extends RoomService<KuaiShouRoom> {
    protected KuaiShouService(KuaiShouRoom room) {
        super(room);
        refresh();
    }

    public static void main(String[] args) {
//        KuaiShouRoom room = new KuaiShouRoom("liujian0627");
//        KuaiShouRoom room = new KuaiShouRoom("KPL704668133");
        KuaiShouRoom room = new KuaiShouRoom("3xg62wetq66kquy");
        KuaiShouService service = new KuaiShouService(room);
        service.refresh(false);
        System.out.println(JSONUtil.parseObj(room).toStringPretty());
    }

    @Override
    public void refresh(boolean force) {
        JSONObject data = getData();
        initRoom(data, force);
    }

    @Override
    public HttpRequest get(String url) {
        HttpRequest header = super.get(url).header(Header.REFERER, room.getRoomUrl());
        if (room.getCookie() != null) {
            return header.header(Header.COOKIE, room.getCookie());
        }
        return header;
    }

    private JSONObject getData() {
        HttpRequest httpRequest = get(room.getRoomUrl());
        try (HttpResponse response = httpRequest.execute()) {
            String initialState = InitialStateExtractor.extractInitialState(response.body());
            return new JSONObject(initialState);
        }
    }

    private void initRoom(JSONObject jsonObject, boolean isStream) {
        JSONObject playList = jsonObject.getJSONObject("liveroom").getJSONArray("playList").getJSONObject(0);
        room.setLiving(playList.getBool("isLiving"));
        JSONObject author = playList.getJSONObject("author");
        if (room.getNickname() == null) {
            room.setNickname(author.getStr("name"));
        }
        if (room.getTitle() == null) {
            String title = author.getStr("description").replaceAll("[\\n\\r]", "");
            title = title.length() > 100 ? title.substring(0, 100) + "..." : title;
            room.setTitle(title);
        }
        if (room.isLiving() && room.getStartTime() == null) {
            room.setStartTime(new Date(author.getLong("timestamp")));
        }
        if (room.getAvatar() == null) {
            room.setAvatar(UnicodeUtil.toString(author.getStr("avatar")));
        }
        JSONObject liveStream = playList.getJSONObject("liveStream");
        if (room.getCover() == null) {
            room.setCover(UnicodeUtil.toString(liveStream.getStr("poster")));
        }
        JSONObject counts = author.getJSONObject("counts");
        room.setFollowers(counts.getStr("fan"));
        room.setLikeCount(counts.getStr("liked"));
        if (room.isLiving()) {
            if (room.getStreams() != null && !isStream) {
                return;
            }
            initStream(liveStream.getJSONObject("playUrls").getJSONObject("h264"));
        }
    }

    private void initStream(JSONObject jsonObject) {
        JSONArray streams = jsonObject.getJSONObject("adaptationSet").getJSONArray("representation");
        Map<String, String> streamMap = new LinkedHashMap<>();
        for (int i = streams.size() - 1; i >= 0; i--) {
            JSONObject stream = streams.getJSONObject(i);
            streamMap.put(stream.getStr("name"), UnicodeUtil.toString(stream.getStr("url")));
        }
        room.setStreams(streamMap);
    }
}
