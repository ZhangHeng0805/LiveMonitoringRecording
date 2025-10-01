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
    private String cookieStr = "did=web_bec7a4478cae400ca13b46730b6380ed; clientid=3; did=web_bec7a4478cae400ca13b46730b6380ed; client_key=65890b29; kpn=GAME_ZONE; kwpsecproductname=PCLive; kuaishou.live.bfb1s=3e261140b0cf7444a0ba411c6f227d88; kwssectoken=Unr42xCL187Gl5Y9UahgPIhxv9hE4BGTGme7esVao/zKtIk0pPFyPAFNEtojF6mNm1I7W33Vu/spUe4PKlIHTw==; kwscode=10051eaba4b351a85f5e7cd1a7a8d2e4d12be582296db3cd133b7afa2c78e711; kwfv1=PnGU+9+Y8008S+nH0U+0mjPf8fP08f+98f+nLlwnrIP9+Sw/ZFGfzY+eGlGf+f+e4SGfbYP0QfGnLFwBLU80mYG9pDG/cU8fbDG/QS8BPFPnrU+9LEwerl+9PIGf+jwnbfG/bYPnpf+0H7+/PM+0L7G/8f+/DhwBLUP0zSwBrl+9P=";

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

//        HttpResponse execute = HttpRequest.get("https://live.kuaishou.cn/").execute();
//        System.out.println(execute.getCookieStr());
    }

    @Override
    public void refresh(boolean force) {
        JSONObject data = getData();
        initRoom(data, force);
        room.setUpdateTime(new Date());
    }

    @Override
    public HttpRequest get(String url) {
        HttpRequest header = super.get(url)
                .header(Header.REFERER, room.getRoomUrl())
                .header(Header.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("dnt", "1")
                .header("sec-ch-ua", "\"Not;A=Brand\";v=\"99\", \"Microsoft Edge\";v=\"139\", \"Chromium\";v=\"139\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "document")
                .header("sec-fetch-mode", "navigate")
                .header("sec-fetch-site", "same-origin")
                .header("sec-fetch-user", "?1")
                .header("upgrade-insecure-requests", "1")
                ;
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
        if (room.getNickname() == null)
            room.setNickname(author.getStr("name"));
        if (!playList.isNull("errorType")) {
            log.warn("接口异常：" + playList.getJSONObject("errorType").toString());
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
