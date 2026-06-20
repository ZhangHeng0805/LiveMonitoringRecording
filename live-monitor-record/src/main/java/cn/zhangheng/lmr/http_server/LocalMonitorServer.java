package cn.zhangheng.lmr.http_server;

import cn.zhangheng.common.httpServer.handle.ProxyHandler;
import cn.zhangheng.common.httpServer.handle.StreamFileHandler;
import cn.zhangheng.common.httpServer.handle.TextFileHandler;
import cn.zhangheng.lmr.http_server.handler.ActionHandler;
import cn.zhangheng.lmr.http_server.handler.ApiHandler;
import cn.zhangheng.lmr.http_server.handler.ClientHandler;
import cn.zhangheng.lmr.http_server.handler.FileResourcesHandler;
import com.sun.net.httpserver.HttpServer;
import com.zhangheng.util.NetworkUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/26 星期五 11:23
 * @version: 1.0
 * @description: 直播监听API
 */
@Slf4j
public class LocalMonitorServer {
    private HttpServer server;
    @Getter
    private int port;

    public LocalMonitorServer(int port) {
        this.port = port;
    }

    public void start() {
        Thread thread = new Thread(() -> {
            while (NetworkUtil.isPortUsed(port)) {
                port++;
            }
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", new TextFileHandler("index.html", "text/html"));
                server.createContext("/DouYinVideoPares.html", new TextFileHandler("DouYinVideoPares.html", "text/html"));
                server.createContext("/FLVPlayer.html", new TextFileHandler("FLVPlayer.html", "text/html"));
                server.createContext("/img", new StreamFileHandler("/img/"));
                server.createContext("/js", new StreamFileHandler("/js/", "text/javascript"));
                server.createContext("/css", new StreamFileHandler("/css/", "text/css"));
                server.createContext("/fonts", new StreamFileHandler("/fonts/", "application/octet-stream"));
                server.createContext("/api", new ApiHandler("/api/"));
                server.createContext("/action", new ActionHandler("/action/"));
                server.createContext("/proxy", new ProxyHandler());
                server.createContext("/fileRes", new FileResourcesHandler("/fileRes/"));
                server.createContext("/client-info", new ClientHandler());
            } catch (Exception e) {
                log.error("本地API服务创建失败！{}", ThrowableUtil.getAllCauseMessage(e));
            }
            if (server != null) {
                server.start();
                log.info("本地API服务已启动！访问地址: {}", getMainUrl());
                System.setProperty("monitor.url", getMainUrl());
                System.setProperty("monitor.port", String.valueOf(getPort()));
            }
        });
        thread.setDaemon(true);
        thread.setName("LocalServerApi-" + port);
        thread.start();
    }

    public String getMainUrl() {
        String url = "http://localhost:" + port + "/";
//        String apiUrl = url + "api/";
        try {
            return url + "?url=" + URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return url + "?url=" + url;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("本地API服务已启动已停止！");
        }
    }
}
