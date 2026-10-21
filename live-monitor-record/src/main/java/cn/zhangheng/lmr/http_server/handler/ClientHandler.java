package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.map.MapUtil;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.handle.MyHandler;
import cn.zhangheng.record.httpServer.util.JWTUtil;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.log.AsyncBatchLogger;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.*;

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



    public ClientHandler(String prefix) {
        this.prefix = prefix;
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
        Object clientIP = exchange.getAttribute("client-ip");
        if (indexPath.startsWith("index")) {
            Map<String, String> query = parseQuery(exchange);
            String cid = query.getOrDefault("cid", "cid");
            String sid = getRequestCookie(exchange, "_sid", null);
            if (sid == null) {
                sid = getSessionID();
                setResponseCookie(exchange, "_sid", sid, Setting.getInstance().getApiExpireSec());
            }
            setResponseCookie(exchange, "_cid", cid, Setting.getInstance().getApiExpireSec());
            String token = JWTUtil.generateToken(MapUtil.of(cid, sid), Setting.getInstance().getApiExpireSec());
            setResponseCookie(exchange, "_token", token, Setting.getInstance().getApiExpireSec());
            log.info("client刷新Token: {} - {}，mainURL: {}，Origin: {}",
                    clientIP,cid,query.get("mainUrl"),exchange.getRequestHeaders().getFirst("Origin"));
        } else {
            String requestBodyStr = parseRequestBodyStr(exchange, charset);
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
