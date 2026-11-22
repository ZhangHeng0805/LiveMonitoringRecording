package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.map.MapUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
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
    private final AsyncBatchLogger logger = new AsyncBatchLogger(Paths.get("logs/client-info.log"));

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
     * 前端埋点日志：请求体原文写盘
     */
    @Endpoint(value = "/", auth = AuthPolicy.PUBLIC)
    public void logRequest(RequestContext ctx) throws IOException {
        String clientIP = getClientIP(ctx.exchange());
        String requestBodyStr = ctx.body();
        String userAgent = ctx.exchange().getRequestHeaders().getFirst("User-Agent");
        logger.highLog(TimeUtil.getNowTime() + " [" +
                clientIP + "] - " +
                userAgent + "\n" +
                requestBodyStr);
        log.info("client-info: {} - {}", clientIP, userAgent);
        ctx.exchange().sendResponseHeaders(200, -1);
    }
}
