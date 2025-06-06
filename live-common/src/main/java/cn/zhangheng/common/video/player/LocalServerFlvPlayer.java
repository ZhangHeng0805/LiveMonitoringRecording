package cn.zhangheng.common.video.player;

import cn.zhangheng.common.bean.Task;
import cn.zhangheng.common.util.LogUtil;
import com.sun.net.httpserver.HttpServer;
import com.zhangheng.util.NetworkUtil;
import lombok.Getter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/06 星期五 03:50
 * @version: 1.0
 * @description:
 */
public class LocalServerFlvPlayer extends Task {
    private HttpServer server;
    @Getter
    private final int port;
    @Getter
    private final String mainUrl;

    public LocalServerFlvPlayer(int port) {
        this.port = port;
        mainUrl = "http://localhost:" + port + "/?url=";
    }

    public String getUrlFromUrl(String url) {
        try {
            return mainUrl + URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return mainUrl + url;
        }
    }

    public String getUrlFromFile(String file) {
        try {
            return mainUrl + "/flv?file=" + URLEncoder.encode(file, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return mainUrl + "/flv?file=" + file;
        }
    }

    @Override
    public void run(boolean isAsync) throws ExecutionException {
        Future<?> future = mainExecutors.submit(() -> {
            Thread.currentThread().setName("LocalServerFlvPlayer-" + Thread.currentThread().getId() + "-" + port);
            if (!NetworkUtil.isPortUsed(port)) {
                try {
                    this.server = HttpServer.create(new java.net.InetSocketAddress(port), 0);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                server.createContext("/", new TextHandler("FLVPlayer.html", "text/html"));
                server.createContext("/js", new StreamHandler("/js/", "text/javascript"));
                server.createContext("/css", new StreamHandler("/css/", "text/css"));
                server.createContext("/fonts", new StreamHandler("/fonts/", "application/octet-stream"));
                server.createContext("/img", new StreamHandler("/img/", "application/octet-stream"));
                server.createContext("/static", new StreamHandler());
                server.createContext("/flv", new FlvFileHandler("./"));
            }
            if (server == null) {
                log.info("本地FLV播放服务已存在！访问地址：http://localhost:" + port + "/");
            } else {
                server.start();
                log.info("本地FLV播放服务已启动！访问地址：http://localhost:" + port + "/");
            }
        });
        isRunning.set(true);
        startTime = System.currentTimeMillis();
        if (!isAsync) {
            try {
                future.get();
            } catch (InterruptedException e) {
                log.error(e);
            }
        }
        mainExecutors.shutdown();
    }

    @Override
    public void stop(boolean force) {
        isRunning.set(false);
        if (server != null) {
            server.stop(0);
        }
        if (force) {
            mainExecutors.shutdownNow();
        } else {
            mainExecutors.shutdown();
        }
    }
}
