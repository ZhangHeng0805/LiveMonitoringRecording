package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.map.MapUtil;
import cn.zhangheng.common.httpServer.handle.MyHandler;
import cn.zhangheng.common.httpServer.util.JWTUtil;
import cn.zhangheng.common.util.AsyncBatchLogger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/09 星期二 10:39
 * @version: 1.0
 * @description:
 */
public class ClientHandler extends MyHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private final AsyncBatchLogger logger = new AsyncBatchLogger(Paths.get("logs/client-info.log"));

    private final String prefix;
    private final static long expireSec = 2 * 60 * 60;

    // 允许的全部前端Origin白名单，按需新增
//    private final Set<String> ALLOW_ORIGINS = new HashSet<>();

    public ClientHandler(String prefix) {
        this.prefix = prefix;
        String port = System.getProperty("monitor.port");
//        ALLOW_ORIGINS.add("https://zhangzheng0805.github.io"); // GitHub线上
//        ALLOW_ORIGINS.add("http://127.0.0.1:"+port); // 本地服务1
//        ALLOW_ORIGINS.add("http://localhost:"+port);  // 本地服务2
//        ALLOW_ORIGINS.add("http://live-monitor-record.xxxr:"+port); // 局域网本地(配置host域名live-monitor-record.xxxr)
    }

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        super.filter(httpExchange);

        String clientIP = getClientIP(httpExchange);
        httpExchange.setAttribute("client-ip", clientIP);
        String userAgent = httpExchange.getRequestHeaders().getFirst("User-Agent");
        httpExchange.setAttribute("User-Agent", userAgent);

        return true;
    }

    @Override
    protected void request(HttpExchange exchange) throws IOException {
        String indexPath = getIndexPath(exchange, prefix);
        if (indexPath.startsWith("index")) {
            String cid = parseQuery(exchange).getOrDefault("cid", "cid");
            String sid = getRequestCookie(exchange, "_sid", null);
            if (sid == null) {
                sid = getSessionID();
                setResponseCookie(exchange, "_sid", sid, -1);
            }
            setResponseCookie(exchange, "_cid", cid, expireSec);
            String token = JWTUtil.generateToken(MapUtil.of(cid, sid), expireSec);
            setResponseCookie(exchange, "_token", token, expireSec);
        } else {
            String requestBodyStr = parseRequestBodyStr(exchange, charset);
            Object clientIP = exchange.getAttribute("client-ip");
            Object userAgent = exchange.getAttribute("User-Agent");
            logger.highLog(TimeUtil.getNowTime() + " [" +
                    clientIP + "] - " +
                    userAgent + "\n" +
                    requestBodyStr);
            log.info("client-info: {} - {}", clientIP, userAgent);
        }
        exchange.sendResponseHeaders(200, -1);
    }
}
