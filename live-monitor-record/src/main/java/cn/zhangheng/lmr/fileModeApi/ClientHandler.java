package cn.zhangheng.lmr.fileModeApi;

import cn.zhangheng.common.httpServer.handle.MyHandler;
import cn.zhangheng.common.util.AsyncBatchLogger;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;

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

    @Override
    protected boolean filter(HttpExchange httpExchange) throws IOException {
        String clientIP = getClientIP(httpExchange);
        httpExchange.setAttribute("client-ip", clientIP);
        String userAgent = httpExchange.getRequestHeaders().getFirst("User-Agent");
        httpExchange.setAttribute("User-Agent", userAgent);
        log.info("client-info: {} - {}", clientIP, userAgent);
        return true;
    }

    @Override
    protected void request(HttpExchange exchange) throws IOException {
        String requestBodyStr = parseRequestBodyStr(exchange);
        logger.highLog(TimeUtil.getNowTime() + " " +
                exchange.getAttribute("client-ip") + " - " +
                exchange.getAttribute("User-Agent") + "\n" +
                requestBodyStr);
        exchange.sendResponseHeaders(200, -1);
    }
}
