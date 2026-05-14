package cn.zhangheng.record;

import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 18:23
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinWebSocket {
    private WebSocketModel wss;
    private final OkHttpClient client;
    private WebSocket webSocket;
    private DouyinLiveDecoder douyinLiveDecoder;
    @Setter
    private MessageListener messageListener;
    // 心跳定时器（每30秒发一次ping）
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private volatile boolean isRunning = false;
    @Getter
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final CountDownLatch latch = new CountDownLatch(1);


    public DouYinWebSocket() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)   // 连接超时
                .readTimeout(0, TimeUnit.SECONDS)       // 读超时设为0（长连接）
                .writeTimeout(0, TimeUnit.SECONDS)      // 写超时设为0
                .pingInterval(30, TimeUnit.SECONDS)    // OkHttp内置心跳（可选）
                .build();
    }


    public void connect(WebSocketModel wss) {
        this.wss = wss;
        Request request = new Request.Builder()
                .url(wss.getUrl())
                .header("cookie", wss.getCookie())
                .header("user-agent", wss.getUserAgent())
                .build();
        this.douyinLiveDecoder = new DouyinLiveDecoder(messageListener);
        isRunning = true;
        this.webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                log.info("DouYinWebSocket【√】连接成功");
                isOpen.set(true);
                isRunning = true;
                startHeartbeat();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("DouYinWebSocket【×】连接关闭: " + reason);
                reconnect(); // 自动重连
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                isOpen.set(false);
                isRunning = false;
                stopHeartbeat();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isOpen.set(false);
                if (t instanceof java.io.EOFException) {
                    isRunning = false;
                    log.warn("正常关闭!{}", t.getMessage());
                    latch.countDown();
                } else {
                    log.error("DouYinWebSocket连接失败！{}", ThrowableUtil.getAllCauseMessage(t));
                }
                stopHeartbeat();
                reconnect();
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                douyinLiveDecoder.parse(webSocket, bytes.toByteArray());
            }
        });
        try {
            latch.await(Integer.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            log.info("wss连接结束！");
        }
    }

    // 启动心跳：每20秒发送一次Ping
    private void startHeartbeat() {
        if (isRunning) {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (webSocket != null) {
                    if (isOpen.get()) {
                        // 构建心跳 PushFrame
                        DouyinMessageOuter.PushFrame hbFrame = DouyinMessageOuter.PushFrame.newBuilder()
                                .setSeqId(System.currentTimeMillis())
                                .setPayloadType("hb") // 心跳类型
                                .build();
                        webSocket.send(ByteString.of(hbFrame.toByteArray()));
//                System.out.println("===== 发送心跳");
                    }
                }
            }, 0, 20, TimeUnit.SECONDS);
        }
    }

    // 停止心跳
    private void stopHeartbeat() {
        if (!heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
        }
    }

    // 自动重连（延迟3秒重连，避免频繁重试）
    private void reconnect() {
        if (isRunning) {
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    log.info("wss自动重连中。。。");
                    connect(wss);
                } catch (InterruptedException ignored) {
                }
            }).start();
        }
    }

    public void close() {
        isRunning = false;
        latch.countDown();
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
        }
        stopHeartbeat();
    }
}
