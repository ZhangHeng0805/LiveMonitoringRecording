package cn.zhangheng.kuaishou.service;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.service.RoomService;
import cn.zhangheng.common.util.UserAgentUtil;
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
    private static String cookieStr = "did=web_bec7a4478cae400ca13b46730b6380ed; clientid=3; did=web_bec7a4478cae400ca13b46730b6380ed; client_key=65890b29; kpn=GAME_ZONE; kwpsecproductname=PCLive; kuaishou.live.bfb1s=3e261140b0cf7444a0ba411c6f227d88; kwssectoken=Unr42xCL187Gl5Y9UahgPIhxv9hE4BGTGme7esVao/zKtIk0pPFyPAFNEtojF6mNm1I7W33Vu/spUe4PKlIHTw==; kwscode=10051eaba4b351a85f5e7cd1a7a8d2e4d12be582296db3cd133b7afa2c78e711; kwfv1=PnGU+9+Y8008S+nH0U+0mjPf8fP08f+98f+nLlwnrIP9+Sw/ZFGfzY+eGlGf+f+e4SGfbYP0QfGnLFwBLU80mYG9pDG/cU8fbDG/QS8BPFPnrU+9LEwerl+9PIGf+jwnbfG/bYPnpf+0H7+/PM+0L7G/8f+/DhwBLUP0zSwBrl+9P=";

    private final UserAgentUtil userAgentUtil;

    private String userAgent = Constant.User_Agent;
    private int count = 0;


    protected KuaiShouService(KuaiShouRoom room) {
        super(room);
        this.userAgentUtil = new UserAgentUtil();
        callIndex(room.getPlatform().getMainUrl());
        refresh();
    }

    public static void main(String[] args) {
//        KuaiShouRoom room = new KuaiShouRoom("liujian0627");
//        KuaiShouRoom room = new KuaiShouRoom("KPL704668133");
//        KuaiShouRoom room = new KuaiShouRoom("3xg62wetq66kquy");
//        KuaiShouService service = new KuaiShouService(room);
//        service.refresh(false);
//        System.out.println(JSONUtil.parseObj(room).toStringPretty());


//        String url = "https://live.kuaishou.cn/";
//        HttpResponse execute = HttpRequest.get(url)
//                .header(Header.USER_AGENT, Constant.User_Agent)
//                .header(Header.COOKIE, cokie)
//                .header(Header.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//                .execute();
//        System.out.println(JSONUtil.parseObj(execute.headers()));
    }

    @Override
    public void refresh(boolean force) {
        try {
            JSONObject data = getData();
            if (initRoom(data, force)) {
                room.setUpdateTime(new Date());
            }
        } catch (Exception e) {
            log.error("快手refresh错误", e);
        }

    }

    @Override
    public HttpRequest get(String url) {
        String mainUrl = room.getPlatform().getMainUrl();
        HttpRequest header = HttpRequest.get(url)
                .header(Header.REFERER, mainUrl)
                .header(Header.ORIGIN, mainUrl.substring(0, mainUrl.lastIndexOf("/")));
        if (count % 10 == 0) {
            userAgent = userAgentUtil.get();
            callIndex(mainUrl);
        }
        count++;
        return setHeader(header);
    }

    private void callIndex(String mainUrl) {
        try (HttpResponse execute = setHeader(HttpRequest.get(mainUrl)).execute()) {
            int status = execute.getStatus();
            if (status != 200) {
                log.warn("访问{}主页响应状态码为:[{}]-{}", mainUrl, status, userAgent);
            }
        }
    }

    private HttpRequest setHeader(HttpRequest header) {
        header = header
                .timeout(30_000)
                .header(Header.USER_AGENT, userAgent)
                .header(Header.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header(Header.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                .header(Header.ACCEPT_ENCODING, "gzip, deflate, br")
//                .header(Header.CONNECTION, "keep-alive")
                .header(Header.CACHE_CONTROL, "max-age=0")
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
            if (StrUtil.isBlank(initialState)) {
                log.warn("[{}]获取body异常：{}",response.getStatus(), body);
            }
            return new JSONObject(initialState);
        }
    }

    private boolean initRoom(JSONObject jsonObject, boolean isStream) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            log.warn("获取的JsonObject为空");
            return false;
        }
        JSONObject playList = jsonObject.getJSONObject("liveroom").getJSONArray("playList").getJSONObject(0);

        if (!playList.isNull("errorType")) {
            log.warn("接口异常：" + playList.getJSONObject("errorType").toString());
            return false;
        }
        if (playList.getBool("isLiving") != null) {
            room.setLiving(playList.getBool("isLiving"));
        }
        JSONObject author = playList.getJSONObject("author");
        if (room.getNickname() == null) {
            room.setNickname(author.getStr("name"));
        }
        if (room.getAvatar() == null) {
            room.setAvatar(UnicodeUtil.toString(author.getStr("avatar")));
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

            JSONObject liveStream = playList.getJSONObject("liveStream");
            if (room.getCover() == null) {
                room.setCover(UnicodeUtil.toString(liveStream.getStr("poster")));
            }
            JSONObject counts = author.getJSONObject("counts");
            room.setFollowers(counts.getStr("fan"));
            room.setLikeCount(counts.getStr("liked"));

            if (room.getStreams() != null && !isStream) {
                return true;
            }
            initStream(liveStream.getJSONObject("playUrls").getJSONObject("h264"));
        }
        return true;
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
