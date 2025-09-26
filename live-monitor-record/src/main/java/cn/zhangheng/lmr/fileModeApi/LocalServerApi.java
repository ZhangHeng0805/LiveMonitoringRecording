package cn.zhangheng.lmr.fileModeApi;

import cn.zhangheng.common.video.player.TextHandler;
import com.sun.net.httpserver.HttpServer;
import com.zhangheng.util.NetworkUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
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
public class LocalServerApi {
    private HttpServer server;
    @Getter
    private int port;

    public LocalServerApi(int port) {
        this.port = port;
    }

    public void start() {
        Thread thread = new Thread(() -> {
            while (NetworkUtil.isPortUsed(port)) {
                port++;
            }
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", new TextHandler("Monitor.html", "text/html"));
                server.createContext("/api", new JsonHandler("/api/"));
            } catch (Exception e) {
                log.error("本地API服务创建失败！{}", ThrowableUtil.getAllCauseMessage(e));
            }
            if (server != null) {
                server.start();
                log.info("本地API服务已启动！访问地址: {}", getMainUrl());
                System.setProperty("monitor.url", getMainUrl());
            }
        });
        thread.setDaemon(true);
        thread.setName("LocalServerApi-" + port);
        thread.start();
    }

    public String getMainUrl() {
        String url = "http://localhost:" + port + "/";
        String apiUrl = url + "api/";
        try {
            return url + "?url=" + URLEncoder.encode(apiUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return url + "?url=" + apiUrl;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("本地API服务已启动已停止！");
        }
    }
}
