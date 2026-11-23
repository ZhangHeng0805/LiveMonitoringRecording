package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RequestContext;
import cn.zhangheng.record.httpServer.util.JWTUtil;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import com.zhangheng.log.AsyncBatchLogger;
import com.zhangheng.util.TimeUtil;
import com.sun.net.httpserver.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.*;

/**
 * 客户端信息采集 + 会话 Cookie 刷新。
 * <p>
 * 两个接口都是公开的（不涉及业务数据，只写日志/下发会话 Cookie），
 * 规则直接写在方法注解上。
 *
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/09 星期二 10:39
 * @version: 1.0
 */
public class ClientHandler extends AnnotationHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    /** 日志接口默认只返回末尾这么多行，避免日志持续增长后一次拉全量 */
    private static final int DEFAULT_LOG_TAIL_LINES = 1000;
    private final AsyncBatchLogger logger;
    private final Path logPath = Paths.get("logs/client-info.log");

    {
        logger = new AsyncBatchLogger(logPath);
    }

    public ClientHandler(String prefix) {
        super(prefix, AuthResolvers.defaults());
    }

    /**
     * 刷新会话：下发 _sid/_cid/_token Cookie
     */
    @Endpoint(value = "/index", auth = AuthPolicy.PUBLIC)
    public void index(RequestContext ctx) throws IOException {
        Map<String, String> query = ctx.query();
        String clientIP = getClientIP(ctx.exchange());
        String cid = query.getOrDefault("cid", "cid");
        String sid = getRequestCookie(ctx.exchange(), "_sid", null);
        Setting setting = ConfigKit.get(Setting.class);
        if (sid == null) {
            sid = getSessionID();
            setResponseCookie(ctx.exchange(), "_sid", sid, setting.getApiExpireSec());
        }
        setResponseCookie(ctx.exchange(), "_cid", cid, setting.getApiExpireSec());
        String token = JWTUtil.generateToken(MapUtil.of(cid, sid), setting.getApiExpireSec());
        setResponseCookie(ctx.exchange(), "_token", token, setting.getApiExpireSec());
        log.info("client刷新Token: {} - {}，mainURL: {}，Origin: {}",
                clientIP, cid, query.get("mainUrl"), ctx.exchange().getRequestHeaders().getFirst("Origin"));
        // 无响应体：直接回 200，避免框架再写 JSON
        ctx.exchange().sendResponseHeaders(200, -1);
    }

    /**
     * 读取埋点日志原文，供可视化页面实时拉取。
     * <p>
     * 返回 text/plain（原始日志，前端自行解析，兼容新旧两种格式）；
     * 关联信息放在响应头里，避免把大段文本塞进 JSON 再转义一遍。
     *
     * @param ctx ctx.query().get("maxLines") 只取末尾 N 行；省略时默认只取末尾 {@value #DEFAULT_LOG_TAIL_LINES} 行，
     *                 传 0 表示全量（日志会一直增长，默认限量避免一次拉太多）
     */
    @Endpoint(value = "/log", auth = AuthPolicy.READ)
    public void logContent(RequestContext ctx) throws IOException {
        Path path = logPath;
        if (!Files.exists(path)) {
            ctx.exchange().sendResponseHeaders(404, -1);
            return;
        }
        int maxLines = DEFAULT_LOG_TAIL_LINES;
        String raw = ctx.query().get("maxLines");
        if (raw != null && !raw.isEmpty()) {
            try {
                maxLines = Math.max(0, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        List<String> all = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> lines = all;
        if (maxLines > 0 && all.size() > maxLines) {
            lines = all.subList(all.size() - maxLines, all.size());
        }
        // 保留一个空行，避免末行被前端 split 吞掉
        String text = String.join("\n", lines) + "\n";
        byte[] body = text.getBytes(StandardCharsets.UTF_8);

        Headers headers = ctx.exchange().getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        // 元信息走响应头，前端直接读，不用解析正文
        headers.set("X-Log-File", "logs/client-info.log");
        headers.set("X-Log-Lines", String.valueOf(lines.size()));
        headers.set("X-Log-Total-Lines", String.valueOf(all.size()));
        headers.set("X-Log-Size", String.valueOf(body.length));
        headers.set("X-Log-Modified", String.valueOf(Files.getLastModifiedTime(path).toMillis()));
        ctx.exchange().sendResponseHeaders(200, body.length);
        ctx.exchange().getResponseBody().write(body);
    }

    /**
     * 前端埋点日志：一行一条结构化 JSON（JSON Lines）。
     * <p>
     * 相比旧版「时间+UA 一行 + 原始请求体一行」的写法：
     * <ul>
     *     <li>单行 JSON，天然带分隔，不会因为没写换行导致多条记录粘连；</li>
     *     <li>字段扁平化归一，把 ip 归属地、系统/浏览器版本合并成可读字段；</li>
     *     <li>丢掉 sid、t（加密串）等无用字段，只留可分析的维度；</li>
     *     <li>埋点请求体异常（空 / 非 JSON）时不再整条丢日志，原文落在 raw 里。</li>
     * </ul>
     */
    @Endpoint(value = "/", auth = AuthPolicy.PUBLIC)
    public void logRequest(RequestContext ctx) throws IOException {
        String clientIP = getClientIP(ctx.exchange());
        String userAgent = ctx.exchange().getRequestHeaders().getFirst("User-Agent");
        // 每条记录独立成行，方便直接 grep / 逐行 JSON 解析
        logger.highLog(toJsonLine(clientIP, userAgent, ctx.body()));
        log.info("client-info: {} - {}", clientIP, userAgent);
        ctx.exchange().sendResponseHeaders(200, -1);
    }

    private String toJsonLine(String clientIP, String userAgent, String body) {
        JSONObject out = JSONUtil.createObj();
        out.set("t", TimeUtil.getNowTime());
        out.set("ip", clientIP);

        JSONObject client = null;
        JSONObject ipInfo = null;
        boolean parsed = false;
        try {
            if (body != null && body.trim().startsWith("{")) {
                JSONObject root = JSONUtil.parseObj(body);
                client = root.getJSONObject("client");
                ipInfo = root.getJSONObject("ip");
                parsed = client != null || ipInfo != null;
            }
        } catch (Exception ignored) {
            // 埋点数据异常不影响业务，原文后面落到 raw 字段
        }

        if (ipInfo != null) {
            out.set("geo", joinNonBlank(ipInfo.getStr("country"), ipInfo.getStr("province"),
                    ipInfo.getStr("city"), ipInfo.getStr("district")));
            out.set("isp", ipInfo.getStr("isp"));
        }

        if (client != null) {
            out.set("cid", client.getStr("cid"));
            out.set("title", client.getStr("dt"));
            out.set("url", client.getStr("dl"));
            out.set("ref", blankToNull(client.getStr("dr")));
            out.set("search", blankToNull(client.getStr("df")));
            out.set("kw", blankToNull(client.getStr("dw")));
            out.set("os", joinNonBlank(client.getStr("os"), client.getStr("osv")));
            String bs = client.getStr("bs");
            if ("Edg".equalsIgnoreCase(bs)) {
                bs = "Edge";
            }
            out.set("browser", joinNonBlank(bs, client.getStr("bsv")));
            out.set("lang", client.getStr("ul"));
            out.set("screen", client.getStr("sr"));
            out.set("viewport", client.getStr("vp"));
            out.set("depth", client.getStr("sd"));
            out.set("java", client.getInt("je"));
            out.set("app", blankToNull(client.getStr("app")));
            out.set("device", client.getStr("br"));
            out.set("ts", client.getLong("r"));
        }

        out.set("ua", truncate(oneLine(userAgent), 180));
        if (!parsed) {
            out.set("raw", truncate(oneLine(body), 600));
        }
        return out.toString();
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }

    private static String oneLine(String s) {
        return s == null ? null : s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
