package cn.zhangheng.douyin.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.douyin.browser.DouYinDataHandler;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.okhttp.OkHttpUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/07 星期三 10:56
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinDataService {
    private final DouYinRoom room;
    private Map<String, String> headers;
    private String url;
    private final DouYinDataHandler handler;

    public DouYinDataService(DouYinRoom room) {
        this.room = room;
        handler = new DouYinDataHandler(room);
    }

    public static void main(String[] args) {
        long sta = System.currentTimeMillis();
        Setting setting = ConfigKit.get(Setting.class);
        DouYinRoom info = new DouYinRoom("142381563944");
//        DouYinRoom info = new DouYinRoom("兰小美认证信息.txt");
//        DouYinRoom info = new DouYinRoom("870887192950-7547373102502578971");
//        DouYinRoom info = new DouYinRoom("816560967344-7549211978108029696-uu子.txt");
        info.setSetting(setting);
        DouYinDataService douYinRoomService = new DouYinDataService(info);
        douYinRoomService.refresh(true);
        System.out.println("耗时：" + (System.currentTimeMillis() - sta));
        System.out.println(JSONUtil.toJsonPrettyStr(info));
    }

    public boolean refresh(boolean force) {
        boolean success = false;
        if (!room.isLiving() || room.getBrowserApi().getDataUrl() == null) {
            success = handler.request();
        }
        if (room.isLiving() && room.getBrowserApi().getDataUrl() != null) {
            getData(force);
        }
        return success;
    }


    private void init() {
        url = room.getBrowserApi().getDataUrl();
        headers = room.getBrowserApi().getHeaders();
        headers.put("accept-language", "zh-CN,zh;q=0.9");
        headers.remove("accept-encoding");
    }

    private void getData(boolean force) {
        if (url == null) {
            init();
        }
        try (Response response = OkHttpUtil.getResponse(url, headers)) {
            String body = Objects.requireNonNull(response.body()).string();
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
//                        System.out.println(data1.toStringPretty());
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
                int responseCode = response.code();
                System.err.println(Thread.currentThread().getName() + "-" + responseCode + ": " + body);
            }
        } catch (Exception e) {
            log.error("请求刷新失败！{}", ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 拉取各清晰度流地址。
     * 排序完全依据接口响应里的档位参数（分辨率、帧率、码率、level），不额外拉流探测、也不写死档位名：
     * 抖音“原画”的分辨率字段常是空的、码率也未必最高，“蓝光臻彩”这类新档位只要有参数就能正常参与比较。
     * 返回的 Map 按画质从高到低排列，第一个就是录制选用的流。
     */
    private Map<String, String> handleStream(JSONObject stream_data) {
//        System.out.println(stream_data.toStringPretty());
        JSONArray qualities = stream_data.getJSONObject("options").getJSONArray("qualities");
        JSONObject data = JSONUtil.parseObj(stream_data.getStr("stream_data")).getJSONObject("data");
        if (data == null || qualities == null) {
            log.warn("直播流数据缺少 qualities/data 节点，跳过清晰度解析");
            return new LinkedHashMap<>();
        }
        List<QualityInfo> candidates = new ArrayList<>();
        for (int i = 0; i < qualities.size(); i++) {
            JSONObject quality = qualities.getJSONObject(i);
            if (quality == null || intValue(quality, "disable") > 0) continue;
            String sdkKey = StrUtil.blankToDefault(quality.getStr("sdk_key"), "");
            JSONObject stream = data.getJSONObject(sdkKey);
            if (stream == null) continue;
            JSONObject main = stream.getJSONObject("main");
            if (main == null) continue;
            String url = main.getStr("flv");
            if (StrUtil.isBlank(url)) continue;
            candidates.add(new QualityInfo(quality, main, sdkKey, url));
        }
        candidates.sort(DouYinDataService::compareQuality);
        Map<String, String> map = new LinkedHashMap<>();
        for (QualityInfo info : candidates) {
//            log.debug("清晰度【{}】{}px/{}fps/声明码率{}kbps/实时码率{}kbps（level={}）",
//                    info.name, info.pixels, info.fps, info.vBitRate / 1000, info.realKbps, info.level);
            map.put(info.name, info.url);
        }
        return map;
    }

    /**
     * 画质比较：帧率 → 分辨率（为0跳过比较） → 接口声明码率 → 实时码率 → level。
     * 以码率优先是因为抖音各房间的实际转码码率差异很大（同为“原画”，有的房间 9M、有的只有 3M）；
     * “原画”的 resolution 字段常常为空，码率相同时再由分辨率决定，空分辨率自然排在 1080p 档之后。
     */
    private static int compareQuality(QualityInfo a, QualityInfo b) {
        int c = Integer.compare(b.fps, a.fps);
        if (c != 0) return c;
        if (a.pixels > 0 && b.pixels > 0) {
            c = Long.compare(b.pixels, a.pixels);
            if (c != 0) return c;
        }
        c = Long.compare(b.vBitRate, a.vBitRate);
        if (c != 0) return c;
        if (a.realKbps > 0 && b.realKbps > 0) {
            c = Long.compare(b.realKbps, a.realKbps);
            if (c != 0) return c;
        }
        return Integer.compare(b.level, a.level);
    }

    private static int intValue(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static long longValue(JSONObject object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static long parsePixels(String resolution) {
        if (StrUtil.isBlank(resolution)) return 0L;
        String[] parts = resolution.toLowerCase().trim().split("x");
        if (parts.length != 2) return 0L;
        try {
            return Long.parseLong(parts[0].trim()) * Long.parseLong(parts[1].trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 接口里的单个清晰度档位：参数取自 qualities 条目，缺失时用 main.sdk_params / templateRealTimeInfo 兜底
     */
    private static class QualityInfo {
        final String name;
        final String url;
        long pixels;//分辨率
        int fps;//帧率
        long vBitRate;//声明码率
        long realKbps;//实时码率
        int level;//标签

        QualityInfo(JSONObject quality, JSONObject main, String sdkKey, String url) {
            this.url = url;
            this.name = StrUtil.blankToDefault(quality.getStr("name"), sdkKey);
            this.level = intValue(quality, "level");
            this.fps = intValue(quality, "fps");
            this.vBitRate = longValue(quality, "v_bit_rate");
            this.pixels = parsePixels(quality.getStr("resolution"));
            try {
                String sdkParams = main.getStr("sdk_params");
                if (StrUtil.isNotBlank(sdkParams) && JSONUtil.isTypeJSON(sdkParams)) {
                    JSONObject params = JSONUtil.parseObj(sdkParams);
                    if (pixels == 0) pixels = parsePixels(params.getStr("resolution"));
                    if (fps == 0) fps = intValue(params, "fps");
                    if (vBitRate == 0) vBitRate = longValue(params, "vbitrate");
                }
                JSONObject realTime = main.getJSONObject("templateRealTimeInfo");
                if (realTime != null) {
                    Object kbps = realTime.get("bitrateKbps");
                    if (kbps instanceof Number) realKbps = ((Number) kbps).longValue();
                }
            } catch (Exception ignored) {
                // 参数缺失不影响主排序
            }
        }
    }
}
