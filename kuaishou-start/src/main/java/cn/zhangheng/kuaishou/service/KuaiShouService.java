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
    private String cookieStr = "did=web_bec7a4478cae400ca13b46730b6380ed; clientid=3; did=web_bec7a4478cae400ca13b46730b6380ed; client_key=65890b29; kpn=GAME_ZONE; kwpsecproductname=PCLive; kuaishou.live.bfb1s=9b8f70844293bed778aade6e0a8f9942; kwssectoken=eYWujBV1vDQtpE8BCGNe9Himu8V2FykstfbtDvAr9ZrP4oj4A9zvLrU/ofKnTk4XoXYd+z1btjmhiNV9j7g8kA==; kwscode=4edb255b6a4abf408282d5383588890b8ff5de72e7945d25cee7d313be58bcfe; kwfv1=PnGU+9+Y8008S+nH0U+0mjPf8fP08f+98f+nLlwnrIP9+Sw/ZFGfzY+eGlGf+f+e4SGfbYP0QfGnLFwBLU80mYGAcEP/zYwe4DP0q9+eSS8fHAwBcIPfPIPfP7P9zY+eGEwBHUPAr98eQD+AZFPAZFG0DFweZUP/GUPBQY80rlweD=";

    protected KuaiShouService(KuaiShouRoom room) {
        super(room);
        refresh();
    }

    public static void main(String[] args) {
//        KuaiShouRoom room = new KuaiShouRoom("liujian0627");
//        KuaiShouRoom room = new KuaiShouRoom("KPL704668133");
        KuaiShouRoom room = new KuaiShouRoom("3xg62wetq66kquy");
        KuaiShouService service = new KuaiShouService(room);
//        service.refresh(false);
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
        } else if (cookieStr != null) {
            return header.header(Header.COOKIE, cookieStr);
        }
        return header;
    }

    private JSONObject getData() {
        HttpRequest httpRequest = get(room.getRoomUrl());
        try (HttpResponse response = httpRequest.execute()) {
            cookieStr = response.getCookieStr();
            String body = response.body();
            String initialState = InitialStateExtractor.extractInitialState(body);
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
        if (room.isLiving()) {
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
