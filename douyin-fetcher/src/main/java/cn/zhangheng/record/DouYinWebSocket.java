package cn.zhangheng.record;

import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;

import java.util.concurrent.*;
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
    private MessageListener messageListener;
    @Setter
    private SocketListener socketListener;
    // 心跳定时器（每30秒发一次ping）
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    // 2. 心跳开关：控制暂停/发送
    private final AtomicBoolean heartbeatEnabled = new AtomicBoolean(false);
    // 3. 记录当前心跳任务，方便取消（避免重复任务）
    private ScheduledFuture<?> heartbeatFuture;
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
                if (socketListener != null) socketListener.onOpen(ws, response);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("DouYinWebSocket【×】连接关闭: " + reason);
                if (socketListener != null) socketListener.onClosed(webSocket, code, reason);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                isOpen.set(false);
                isRunning = false;
                stopHeartbeat();
                if (socketListener != null) socketListener.onClosing(webSocket, code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isOpen.set(false);
                if (t instanceof java.io.EOFException) {
                    isRunning = false;
                    log.warn("DouYinWebSocket正常关闭!{}", t.getMessage() != null ? t.getMessage() : "");
                    latch.countDown();
                } else {
                    log.error("DouYinWebSocket连接失败！{}", ThrowableUtil.getAllCauseMessage(t));
                }
                stopHeartbeat();
                reconnect();
                if (socketListener != null) socketListener.onFailure(webSocket, t, response);
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                douyinLiveDecoder.parse(webSocket, bytes.toByteArray());
                if (socketListener != null) socketListener.onMessage(webSocket, bytes);
            }
        });
        try {
            latch.await(Integer.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            log.info("DouYinWebSocket连接结束！");
        }
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
        if (douyinLiveDecoder != null) douyinLiveDecoder.setListener(messageListener);
    }

    // 启动心跳：每20秒发送一次Ping
    private void startHeartbeat() {
        if (isRunning) {
            // 允许心跳
            heartbeatEnabled.set(true);

            // 如果已有心跳任务，不重复创建
            if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
                return;
            }
            heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
                // 开关关闭 → 不发送心跳
                if (!heartbeatEnabled.get()) {
                    return;
                }
                // 连接未打开 → 不发送
                if (webSocket == null || !isOpen.get()) {
                    return;
                }
                try {
                    // 发送心跳
                    DouyinMessageOuter.PushFrame hbFrame = DouyinMessageOuter.PushFrame.newBuilder()
                            .setSeqId(System.currentTimeMillis())
                            .setPayloadType("hb")
                            .build();
                    webSocket.send(ByteString.of(hbFrame.toByteArray()));
                } catch (Exception e) {
                    log.error("心跳发送异常", e);
                }
            }, 0, 20, TimeUnit.SECONDS);
        }
    }

    // 停止心跳
    private void stopHeartbeat() {
        // 关闭开关
        heartbeatEnabled.set(false);
        // 取消当前任务
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    // 自动重连（延迟3秒重连，避免频繁重试）
    private void reconnect() {
        if (isRunning) {
            CompletableFuture.runAsync(() -> {
                try {
                    TimeUnit.SECONDS.sleep(3);
                    log.info("wss自动重连中...");
                    connect(wss);
                } catch (InterruptedException ignored) {
                }
            });
        }
    }

    public void close() {
        isRunning = false;
        latch.countDown();
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
        }
        heartbeatExecutor.shutdown();
    }
}
