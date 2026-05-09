package cn.zhangheng.record;

import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import okio.ByteString;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 18:23
 * @version: 1.0
 * @description:
 */
public class DouYinWebSocket {
    private String wss, ttwid, userAgent;
    private final OkHttpClient client;
    private WebSocket webSocket;
    private DouyinLiveDecoder douyinLiveDecoder;
    @Setter
    private MessageListener messageListener;
    // 心跳定时器（每30秒发一次ping）
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private boolean isRunning = false;
    @Getter
    private boolean isOpen = false;

    public static void main(String[] args) {
        String ttwid = "1%7CFAsPXoeNtEw7bhDbtuGFEGR2z2q6J3C-6goTSFI2MJU%7C1778322530%7Ce02aa9c73e462cbad10db03b7316568af3aa8f83a53626fdb0fff53d8f7107bc";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0";

//        String wss = "wss://webcast100-ws-web-lq.douyin.com/webcast/im/push/v2/?app_name=douyin_web&version_code=180800&webcast_sdk_version=1.0.15&update_version_code=1.0.15&compress=gzip&device_platform=web&cookie_enabled=true&screen_width=1536&screen_height=864&browser_language=zh-CN&browser_platform=Win32&browser_name=Mozilla&browser_version=5.0%20(Windows%20NT%2010.0;%20Win64;%20x64)%20AppleWebKit/537.36%20(KHTML,%20like%20Gecko)%20Chrome/140.0.0.0%20Safari/537.36%20Edg/140.0.0.0&browser_online=true&tz_name=Asia/Shanghai&cursor=r-7637944894589332475_d-1_u-1_h-1_t-1778347629442&internal_ext=internal_src:dim|wss_push_room_id:7637049676450614051|wss_push_did:7317938591698191909|first_req_ms:1778347629385|fetch_time:1778347629442|seq:1|wss_info:0-1778347629442-0-0|wrds_v:7637944873114403831&host=https://live.douyin.com&aid=6383&live_id=1&did_rule=3&endpoint=live_pc&support_wrds=1&user_unique_id=7317938591698191909&im_path=/webcast/im/fetch/&identity=audience&need_persist_msg_count=0&insert_task_id=&live_reason=&room_id=7637049676450614051&heartbeatDuration=0&signature=6MBjlLKyZ4kGAIq9";
        String wss = "wss://webcast100-ws-web-lq.douyin.com/webcast/im/push/v2/?app_name=douyin_web&version_code=180800&webcast_sdk_version=1.0.15&update_version_code=1.0.15&compress=gzip&device_platform=web&cookie_enabled=true&screen_width=1536&screen_height=864&browser_language=zh-CN&browser_platform=Win32&browser_name=Mozilla&browser_version=5.0%20(Windows%20NT%2010.0;%20Win64;%20x64)%20AppleWebKit/537.36%20(KHTML,%20like%20Gecko)%20Chrome/140.0.0.0%20Safari/537.36%20Edg/140.0.0.0&browser_online=true&tz_name=Asia/Shanghai&cursor=h-7637990455889121280_t-1778358335288_r-7637990889394135389_d-7637990867919175683_u-1&internal_ext=internal_src:dim|wss_push_room_id:7637912231455263503|wss_push_did:7317938591698191909|first_req_ms:1778358335203|fetch_time:1778358335288|seq:1|wss_info:0-1778358335288-0-0|wrds_v:7637990885099045605&host=https://live.douyin.com&aid=6383&live_id=1&did_rule=3&endpoint=live_pc&support_wrds=1&user_unique_id=7317938591698191909&im_path=/webcast/im/fetch/&identity=audience&need_persist_msg_count=15&insert_task_id=&live_reason=&room_id=7637912231455263503&heartbeatDuration=0&signature=6phi4u0NyqiL9WZR";
        DouYinWebSocket socket = new DouYinWebSocket();
        socket.connect(wss, ttwid, userAgent);
    }

    public DouYinWebSocket() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)   // 连接超时
                .readTimeout(0, TimeUnit.SECONDS)       // 读超时设为0（长连接）
                .writeTimeout(0, TimeUnit.SECONDS)      // 写超时设为0
                .pingInterval(30, TimeUnit.SECONDS)    // OkHttp内置心跳（可选）
                .build();
    }

    public void connect(String wss, String ttwid, String userAgent) {
        this.wss = wss;
        this.ttwid = ttwid;
        this.userAgent = userAgent;
        Request request = new Request.Builder()
                .url(wss)
                .header("cookie", "ttwid=" + ttwid)
                .header("user-agent", userAgent)
                .build();
        this.douyinLiveDecoder = new DouyinLiveDecoder(messageListener);
        this.webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                System.out.println("【√】连接成功");
                isRunning = true;
                isOpen = true;
                startHeartbeat();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("【×】连接关闭");
                isOpen = false;
                stopHeartbeat();
                reconnect(); // 自动重连
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isOpen = false;
                System.out.println("连接失败！");
                t.printStackTrace();
                stopHeartbeat();
                reconnect();
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {

//                    System.out.println("接收消息：" );
                douyinLiveDecoder.parse(webSocket, bytes.toByteArray());

            }
        });
    }

    // 启动心跳：每20秒发送一次Ping
    private void startHeartbeat() {
        if (isRunning) {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (webSocket != null) {
                    if (isOpen) {
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
                    System.out.println("自动重连中。。。");
                    connect(wss, ttwid, userAgent);
                } catch (InterruptedException ignored) {
                }
            }).start();
        }
    }

    public void close() {
        isRunning = false;
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
        }
        stopHeartbeat();
    }
}
