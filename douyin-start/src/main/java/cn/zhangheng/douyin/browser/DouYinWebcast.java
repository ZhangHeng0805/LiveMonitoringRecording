package cn.zhangheng.douyin.browser;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import cn.zhangheng.douyin.DouYinUtils;
import cn.zhangheng.record.DouYinWebSocket;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.WebSocket;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.*;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 04:48
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinWebcast {
    public static final String TARGET_REQUEST_PREFIX = "wss://webcast100-ws-web-lq.douyin.com/webcast/im/push/v2/";
    private final DouYinRoom room;
    @Getter
    private volatile boolean isRunning = false;
    private Thread webcastThread;
    @Setter
    private MessageListener messageListener;
    private DouYinWebSocket socket;


    public DouYinWebcast(DouYinRoom room) {
        this.room = room;

    }

    public static void main(String[] args) {
        DouYinRoom room = new DouYinRoom("988132154410");

        String wss = getWss(room);
        if (wss != null) {
            DouYinWebSocket socket = new DouYinWebSocket();
            socket.setMessageListener(new MessageListener() {
                @Override
                public void chat(String info, DouyinMessageOuter.ChatMessage msg) {
                    System.out.println("【聊天】" + info);
                }

                @Override
                public void control(String info, DouyinMessageOuter.ControlMessage msg) {
                    System.out.println("【状态】" + info);
                    if (msg.getStatus() == 3) {
                        socket.close();
                    }
                }
            });
            socket.connect(wss, DouYinUtils.fetchTtwid(), Constant.User_Agent);
        }
    }

    public void start() {
        isRunning = true;
        this.webcastThread = new Thread(() -> {
            String wss = getWss(room);
            if (room.isLiving()) {
                int tryCount = 0;
                while (isRunning && wss == null && tryCount++ < 5) {
                    log.debug("获取直播弹幕wss重试{}次。。。", tryCount);
                    wss = getWss(room);
                }
                if (StrUtil.isNotBlank(wss)) {
                    socket = new DouYinWebSocket();
                    socket.setMessageListener(messageListener);
                    socket.connect(wss, DouYinUtils.fetchTtwid(), Constant.User_Agent);
                    while (socket.isRunning()) {
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } else {
                    log.warn("获取直播弹幕wss重试{}次,仍然获取失败！", tryCount);
                }
            }
            isRunning = false;
        });
//        this.webcastThread.setDaemon(true);
        webcastThread.setName(room.getNickname() + "[" + room.getId() + "]" + "弹幕监听");
        webcastThread.start();
    }


    public void stop() {
        isRunning = false;
        if (socket != null) {
            socket.close();
        }
        webcastThread.interrupt();
    }

    public static String getWss(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return null;
        }
        AtomicReference<String> wss = new AtomicReference<>(null);
        Page page = null;
        Consumer<WebSocket> handler = null;
        try (PlaywrightBrowser browser = new PlaywrightBrowser(Constant.User_Agent, true)) {
            page = browser.newPage(Constant.User_Agent);
            setRoomCookie(room, page, roomUrl);
            CountDownLatch latch = new CountDownLatch(1);

            handler = ws -> {
                if (ws.url().startsWith(TARGET_REQUEST_PREFIX)) {
                    wss.set(ws.url());
                    latch.countDown();
                    log.info("成功捕获目标webSocket!");
                }
            };
            page.onWebSocket(handler);

            browser.navigatePage(room.getRoomUrl(), page);
            //提取界面信息
            extractRoomInfo(room, page);
            if (room.isLiving()) {
                try {
                    latch.await(15, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("等待捕获webSocket失败！", e);
                }
                return wss.get();
            } else {
                latch.countDown();
            }
            System.out.println("直播间未开启直播！");
        } catch (Exception e) {
            log.error("获取直播弹幕wss失败！{}", e.getMessage());
        } finally {
            if (handler != null) {
                page.offWebSocket(handler);
            }
        }
        return null;
    }

}
