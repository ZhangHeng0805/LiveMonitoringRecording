package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.cache.CacheWithOnceLoadHelper;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.douyin.bean.work.DouYinWork;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.handle.JSONHandler;
import cn.zhangheng.record.httpServer.util.HandlerUtils;
import cn.zhangheng.record.record.Recorder;
import cn.zhangheng.douyin.browser.DouYinVideoParse;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.util.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.zhangheng.lmr.http_server.util.CheckUtils.checkReadAuth;
import static cn.zhangheng.lmr.http_server.util.CheckUtils.checkRequestAuth;
import static cn.zhangheng.lmr.http_server.util.CheckUtils.isAuthenticated;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/26 星期五 11:31
 * @version: 1.0
 * @description:
 */
@Slf4j
public class ApiHandler extends JSONHandler {
    /**
     * 抖音作品解析限流：固定 1 分钟窗口，阈值取 server.api.parsePerMinute
     */
    private static final RateLimiter PARSE_LIMITER = new RateLimiter(60_000L);
    private final CacheWithOnceLoadHelper<String, List<Map<String, Object>>> roomsQueryHelper;

    public ApiHandler(String prefix) {
        super(prefix);
        roomsQueryHelper = CacheWithOnceLoadHelper.<String, List<Map<String, Object>>>builder(
                        key -> getRooms()
                )
                .cacheExpireMs(10_000)
                .cacheMaxSize(1)
                .loadTimeoutMs(5000)
                .pool(1, 2, 10)
                .holderMapMaxSize(10)
                .build();
    }

    private static List<Map<String, Object>> getRooms() {
//        long sta = System.currentTimeMillis();
        List<Map<String, Object>> list = FileModeMain.getRoomFileMap().values().stream()
                .map(ApiHandler::getRoomsMap)
                .filter(m -> m != null && !m.isEmpty())
                .sorted(Comparator.comparing(m -> m.get("isRunning").equals(false)))
                .collect(Collectors.toList());
//        log.debug("getRooms耗时:{}ms", System.currentTimeMillis() - sta);
        return list;
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);
        String indexPath = getIndexPath(httpExchange, prefix);
        if (indexPath != null && indexPath.startsWith("rooms")) {
            // 房间列表：允许匿名只读（返回体自动脱敏），是否开放由 server.auth.publicRead 控制
            return checkReadAuth(httpExchange);
        }
        // 抖音作品解析等其它 api 接口：完全公开，任何人可用（不消耗本机秘钥体系）
        return true;
    }


    @Override
    public void request(HttpExchange httpExchange) throws Exception {
        try {
            String indexPath = getIndexPath(httpExchange, prefix);
            Message<Object> msg = new Message<>();

            Map<String, String> query = parseQuery(httpExchange);
            if (indexPath.startsWith("rooms")) {
                String roomsKey = "rooms";
                if ("true".equalsIgnoreCase(query.get("manual"))) roomsQueryHelper.invalidate(roomsKey);
                List<Map<String, Object>> collect = roomsQueryHelper.get(roomsKey, 5000);
                // 匿名访问时脱敏：去掉本地录制路径、Cookie、通知地址等隐私字段
                msg.setData(isAuthenticated(httpExchange) ? collect : maskForPublic(collect));
            } else if (indexPath.startsWith("videoParsing")) {
                // 公开接口，按来源 IP 做轻量限流，避免被刷爆本机浏览器解析资源
                int perMinute = ConfigKit.get(Setting.class).getParsePerMinute();
                String clientIp = HandlerUtils.getClientIP(httpExchange);
                long retryAfter = PARSE_LIMITER.tryAcquire(clientIp, perMinute);
                if (retryAfter > 0) {
                    PARSE_LIMITER.evictExpired();
                    httpExchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfter));
                    msg.setCode(429);
                    msg.setMessage("解析请求过于频繁，请 " + retryAfter + " 秒后再试");
                    responseJson(httpExchange, JSONUtil.toJsonStr(msg), 429);
                    return;
                }
                videoParsing(msg, query, HandlerUtils.getRequestUserAgent(httpExchange));
            } else {
                msg.setCode(1);
                msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
            }
            responseJson(httpExchange, msg);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            sendErrorResponse(httpExchange, e);
            throw e;
        }
    }

    private static Map<String, Object> getRoomsMap(RoomFileModel model) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("key", model.getId());
            Main main = model.getMain();
            map.put("isRunning", model.isRunning());
            map.put("startTime", model.getStartTime());
            map.put("endTime", model.getEndTime());
            map.put("status", main.getMonitorMain().getStatus());
            map.put("room-status", main.getMonitorMain().getRoomMonitor().getState());
            Room room = main.getMonitorMain().getRoom();
            JSONObject entries = JSONUtil.parseObj(room);
            entries.remove("cookie");
            entries.append("statistics", main.getMonitorMain().statistics(null, room));
            JSONObject setting = entries.getJSONObject("setting");
            String cookie = setting.getStr("cookie" + room.getPlatform().name(), "");
            if (!cookie.isEmpty()) {
                setting.set("cookie", cookie.length() > 100 ? cookie.substring(0, 100) + "......" : cookie);
            }
            setting.remove("cookieDouYin");
            setting.remove("cookieBili");
            setting.remove("cookieKuaiShou");
            setting.remove("ffmpegPath");
            setting.remove("activateVoucherPath");
            setting.remove("recordType");
            setting.remove("monitorServerPort");
            setting.remove("browserIsPageClear");
            setting.remove("browserHeadless");
            setting.remove("maxMonitorThreads");
            setting.set("isAutoRecord", main.getMonitorMain().isAutoRecord());
            String xiZhiUrl = setting.getStr("xiZhiUrl");
            if (StrUtil.isBlank(xiZhiUrl)) {
                setting.putOnce("isNotice", Boolean.FALSE);
            } else {
                setting.putOnce("isNotice", Boolean.TRUE);
                setting.set("xiZhiUrl", StrUtil.replace(xiZhiUrl, 25, 50, "***"));

            }
            map.put("room", entries);
            Recorder recorder = main.getMonitorMain().getRecorder();
            if (recorder != null) {
                Map<String, Object> record = new HashMap<>();
                record.put("msg", recorder.getProgressMsg());
                record.put("definition", recorder.getDefinition());
                record.put("path", recorder.getSaveFilePath());
                record.put("isRecord", recorder.isRunning());
                map.put("recorder", record);
            }
            return map;
        } catch (Exception e) {
            if (!(e instanceof NullPointerException)) {
                log.error(ThrowableUtil.getAllCauseMessage(e), e);
            }
        }
        return null;
    }

    /**
     * 匿名只读脱敏：不修改缓存对象，逐层复制后剔除隐私字段
     * - recorder.path：本机录制文件绝对路径（暴露磁盘结构与用户名）
     * - room.setting.cookie：平台 Cookie 片段
     * - room.setting.xiZhiUrl：息知通知地址
     */
    private static List<Map<String, Object>> maskForPublic(List<Map<String, Object>> source) {
        List<Map<String, Object>> out = new ArrayList<>(source == null ? 0 : source.size());
        if (source == null) return out;
        for (Map<String, Object> item : source) {
            Map<String, Object> copy = new HashMap<>(item);
            Object recorder = copy.get("recorder");
            if (recorder instanceof Map) {
                Map<String, Object> rec = new HashMap<>((Map<String, Object>) recorder);
                rec.remove("path");
                copy.put("recorder", rec);
            }
            Object room = copy.get("room");
            if (room instanceof JSONObject) {
                // 深拷贝，避免污染缓存里的共享对象
                JSONObject roomCopy = JSONUtil.parseObj(((JSONObject) room).toString());
                JSONObject setting = roomCopy.getJSONObject("setting");
                if (setting != null) {
                    setting.remove("cookie");
                    setting.remove("xiZhiUrl");
                }
                copy.put("room", roomCopy);
            }
            out.add(copy);
        }
        return out;
    }

    private void videoParsing(Message msg, Map<String, String> query, String userAgent) {
        try {
            String url = query.get("url");
            String isRefresh = query.get("isRefresh");
            if (StrUtil.isBlank(url)) {
                throw new IllegalArgumentException("缺少解析链接，请粘贴抖音作品分享内容");
            }
            DouYinWork parse = DouYinVideoParse.parse(url, "true".equals(isRefresh));
            if (parse == null) {
                throw new IllegalStateException("未解析到作品信息，请确认链接是否为抖音视频/图文作品");
            }
            msg.setData(parse);
            msg.setMessage("解析成功！");
        } catch (Throwable e) {
            // 线程池包装的异常要剥到最内层，否则前端只能看到 ExecutionException 这种无意义信息
            msg.setCode(1);
            msg.setMessage(describe(e));
            msg.setData(briefChain(e));
        }
    }

    /**
     * 取最内层异常的具体原因；原因为空时退回异常类名，保证前端永远有可读提示
     */
    private static String describe(Throwable e) {
        Throwable root = ThrowableUtil.getRootCause(e);
        if (root == null) root = e;
        String text = root.getMessage();
        if (StrUtil.isBlank(text) && root.getCause() != null) {
            text = root.getCause().getMessage();
        }
        if (StrUtil.isBlank(text)) {
            text = root.getClass().getSimpleName();
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    /**
     * 精简异常链（类名: 原因），最多保留三层，便于贴日志又不刷屏
     */
    private static String briefChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 3) {
            if (depth > 0) sb.append(" <- ");
            sb.append(cur.getClass().getSimpleName());
            String m = cur.getMessage();
            if (StrUtil.isNotBlank(m)) {
                m = m.replaceAll("\\s+", " ").trim();
                sb.append(": ").append(m.length() > 120 ? m.substring(0, 120) + "…" : m);
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

}
