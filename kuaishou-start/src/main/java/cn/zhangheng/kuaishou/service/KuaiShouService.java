package cn.zhangheng.kuaishou.service;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.service.RoomService;
import cn.zhangheng.common.util.HttpUtils;
import cn.zhangheng.common.util.RequestUtils;
import cn.zhangheng.common.util.UserAgentUtil;
import cn.zhangheng.kuaishou.bean.KuaiShouRoom;
import cn.zhangheng.kuaishou.util.InitialStateExtractor;
import com.zhangheng.util.HttpURLConnectionUtil;
import com.zhangheng.util.ThrowableUtil;

import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 19:09
 * @version: 1.0
 * @description:
 */
public class KuaiShouService extends RoomService<KuaiShouRoom> {
    private static String cookieStr = "did=web_bec7a4478cae400ca13b46730b6380ed; clientid=3; did=web_bec7a4478cae400ca13b46730b6380ed; client_key=65890b29; kpn=GAME_ZONE; kwpsecproductname=PCLive; kuaishou.live.bfb1s=3e261140b0cf7444a0ba411c6f227d88; kwssectoken=Unr42xCL187Gl5Y9UahgPIhxv9hE4BGTGme7esVao/zKtIk0pPFyPAFNEtojF6mNm1I7W33Vu/spUe4PKlIHTw==; kwscode=10051eaba4b351a85f5e7cd1a7a8d2e4d12be582296db3cd133b7afa2c78e711; kwfv1=PnGU+9+Y8008S+nH0U+0mjPf8fP08f+98f+nLlwnrIP9+Sw/ZFGfzY+eGlGf+f+e4SGfbYP0QfGnLFwBLU80mYG9pDG/cU8fbDG/QS8BPFPnrU+9LEwerl+9PIGf+jwnbfG/bYPnpf+0H7+/PM+0L7G/8f+/DhwBLUP0zSwBrl+9P=";

    private final UserAgentUtil ua = new UserAgentUtil();
    private final Map<String, String> header = new HashMap<>();


    protected KuaiShouService(KuaiShouRoom room) {
        super(room);
        header.put("Referer", "https://live.kuaishou.cn/");
        header.put("Origin", "https://live.kuaishou.cn");
        header.put("User-Agent", ua.get());
        header.put("Accept", "application/json,text/html,*/*");
        header.put("Accept-Language", "zh-CN,zh;q=0.9");
        header.put("sec-fetch-site", "same-origin");
        header.put("sec-fetch-mode", "cors");
        if (room.getCookie() != null) {
            header.put("Cookie", room.getCookie());
        } else {
            String cookie = getCookie();
            if (cookie != null) {
                cookieStr = cookie;
            }
            header.put("Cookie", cookieStr);
        }
    }

    public static void main(String[] args) {
//        KuaiShouRoom room = new KuaiShouRoom("liujian0627");
        KuaiShouRoom room = new KuaiShouRoom("KPL704668133");
//        KuaiShouRoom room = new KuaiShouRoom("3xg62wetq66kquy");
        KuaiShouService service = new KuaiShouService(room);
        service.getRoomData(false);
        System.out.println(JSONUtil.parseObj(room).toStringPretty());

//        String url = "https://live.kuaishou.cn/";
//        HttpResponse execute = HttpRequest.get(url)
//                .header(Header.USER_AGENT, Constant.User_Agent)
//                .header(Header.COOKIE, cokie)
//                .header(Header.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//                .execute();
//        System.out.println(JSONUtil.parseObj(execute.headers()));
    }

    @Override
    protected boolean refresh(boolean force) {
        try {
            JSONObject data = getData();
            return initRoom(data, force);
        } catch (Exception e) {
            log.error("快手refresh错误", e);
        } finally {
            if (counter.get() % 10 == 0) {
                header.put("User-Agent", ua.get());
                if (room.getCookie() != null) {
                    header.put("Cookie", room.getCookie());
                } else {
                    String cookie = getCookie();
                    if (cookie != null) {
                        cookieStr = cookie;
                    }
                    header.put("Cookie", cookieStr);
                }
            }
        }
        return false;
    }

    @Override
    public void startSubtitle() {

    }

    @Override
    public void stopSubtitle() {

    }

    private String getCookie() {
        HttpURLConnection connection = null;
        try {
            connection = RequestUtils.getRequest(Room.Platform.KuaiShou.getMainUrl(), header);
            return RequestUtils.parseCookie(connection);
        } catch (Exception e) {
            log.error("刷新cookie错误:{}", ThrowableUtil.getAllCauseMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private JSONObject getData() {
        String url = room.getRoomUrl();
        HttpURLConnection connection = null;
        try {
            connection = HttpURLConnectionUtil.getRequest(url, header);
            String body = HttpURLConnectionUtil.responseBodyStr(connection);
            String initialState = InitialStateExtractor.extractInitialState(body);
            if (StrUtil.isBlank(initialState)) {
                log.warn("[{}]获取body异常：{}", HttpURLConnectionUtil.responseCode(connection), body);
            }
            return new JSONObject(initialState);
        } catch (Exception e) {
            throw new RuntimeException("getData", e);
        } finally {
            HttpURLConnectionUtil.close(connection);
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
