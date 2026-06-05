package cn.zhangheng.douyin.browser;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.browser.UserAgentUtil;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.douyin.bean.DouYinCounter;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.record.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;
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
 * @description: 抖音直播弹幕
 */
@Slf4j
public class DouYinWebcast {
    public static final String TARGET_WSS = ".douyin.com/webcast/im/push/v2/";
    private final DouYinRoom room;
    @Getter
    private volatile boolean isRunning = false;
    private Thread webcastThread;
    private MessageListener messageListener;
    private SocketListener socketListener;
    private DouYinWebSocket socket;


    public DouYinWebcast(DouYinRoom room) {
        this.room = room;
    }

    public static void main(String[] args) throws IOException {

        System.out.println("===== 抖音直播弹幕获取 =====");
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入抖音直播间ID: ");
        String id = scanner.nextLine();
        if (StrUtil.isBlank(id)) {
            System.out.println("直播间ID不能为空！");
            return;
        }
        System.out.print("请输入抖音cookie(选填): ");
        String cookie = scanner.nextLine();
        Setting setting = new Setting();
        if (StrUtil.isNotBlank(cookie)) {
            setting.setCookieDouYin(cookie);
        }
        DouYinRoom room = new DouYinRoom(id);
        room.setSetting(setting);
        room.setCounter(new DouYinCounter());
        DouYinWebcast webcast = new DouYinWebcast(room);
        webcast.setSocketListener(new SocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket ws, okhttp3.Response response) {
                try {
                    String fileName = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd HH-mm-ss") + "直播弹幕.log";
                    LogUtil logUtil = new LogUtil(room, fileName);
                    webcast.setMessageListener(getDouYinWebSocketMessageListener(room, webcast, logUtil));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        webcast.start();
    }

    private static MessageListener getDouYinWebSocketMessageListener(DouYinRoom room, DouYinWebcast webcast, LogUtil logUtil) throws IOException {

        return new MessageListener() {
            @Override
            public void chat(String info, DouyinMessageOuter.ChatMessage msg) {
                String x = "【聊天】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void gift(String info, DouyinMessageOuter.GiftMessage msg) {
                String x = "【礼物】" + info;
                System.out.println(x);
                logUtil.highLog(x);
                room.getCounter().setTotalGift(info);
            }

            @Override
            public void stats(String info, DouyinMessageOuter.RoomUserSeqMessage msg) {
                String x = "【统计】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void online(String info, DouyinMessageOuter.RoomStatsMessage msg) {
                String x = "【在线】" + info;
                System.out.println(x);
                logUtil.highLog(x);
                room.getCounter().setMaxOnlineUsers(msg.getTotal());
            }

            @Override
            public void roomRank(String info, DouyinMessageOuter.RoomRankMessage msg) {
                String x = "【排名】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void member(String info, DouyinMessageOuter.MemberMessage msg) {
                String x = "【进场】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void like(String info, DouyinMessageOuter.LikeMessage msg) {
                String x = "【点赞】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void social(String info, DouyinMessageOuter.SocialMessage msg) {
                String x = "【关注】" + info;
                System.out.println(x);
                logUtil.highLog(x);
            }

            @Override
            public void control(String info, DouyinMessageOuter.ControlMessage msg) {
                String x = "【状态】" + info;
                x += room.getCounter().toString();
                logUtil.highLog(x);
                if (msg.getStatus() == 3) {
                    webcast.stop();
                    logUtil.close();
                }
                System.out.println(x);
            }
        };
    }

    public void start() {
        isRunning = true;
        this.webcastThread = new Thread(() -> {
            try {

                WebSocketModel wss = getWss(room);
                if (room.isLiving()) {
                    int tryCount = 0;
                    while (isRunning && !wss.isUse() && tryCount++ < 5) {
                        log.debug("获取直播弹幕wss重试{}次。。。", tryCount);
                        wss = getWss(room);
                    }
                    if (wss.isUse()) {
                        room.setSubtitleRunning(true);
                        socket = new DouYinWebSocket();
                        socket.setMessageListener(messageListener);
                        socket.setSocketListener(socketListener);
                        log.info((room.getNickname() + " 直播间已开启！弹幕连接中。。。"));
                        socket.connect(wss);
                    } else {
                        log.warn("获取直播弹幕wss重试{}次,仍然获取失败！", tryCount);
                    }
                }
            } finally {
                log.debug("直播弹幕结束！");
                room.setSubtitleRunning(false);
                isRunning = false;
            }
        });
        webcastThread.setName(room.getNickname() + "[" + room.getId() + "]" + "弹幕监听");
        webcastThread.start();
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
        if (socket != null) socket.setMessageListener(messageListener);
    }

    public void setSocketListener(SocketListener socketListener) {
        this.socketListener = socketListener;
        if (socket != null) socket.setSocketListener(socketListener);
    }

    public void stop() {
        isRunning = false;
        if (socket != null) {
            socket.close();
        }
        webcastThread.interrupt();
    }

    public static WebSocketModel getWss(DouYinRoom room) {
        WebSocketModel wss = browserGetWssUrl1(room);
//        if (room.getBrowserApi() != null && room.getBrowserApi().getHeaders() != null) {
//            Map<String, String> headers = room.getBrowserApi().getHeaders();
//            wss.setCookie(headers.get("cookie"));
//            wss.setUserAgent(headers.get("user-agent"));
//        } else {
//            DouYinUtils utils = new DouYinUtils();
//            wss.setCookie("ttwid=" + utils.getTtwid());
//            wss.setUserAgent(utils.getUserAgent());
//        }
        return wss;
    }

    public static WebSocketModel browserGetWssUrl2(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return null;
        }
        AtomicReference<String> wss = new AtomicReference<>(null);
        Page page = null;
        Consumer<WebSocket> handler = null;
        boolean headless = true;
        String userAgent = UserAgentUtil.getRandomUser_Agent();
        if (room.getSetting() != null) {
            headless = !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(userAgent, headless));
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(userAgent);
            BrowserContext context = browser.newContext(contextOptions);
            page = context.newPage();
            setRoomCookie(room, page, roomUrl);
            CountDownLatch latch = new CountDownLatch(1);

            handler = ws -> {
                String url = ws.url();
                if (url.indexOf(TARGET_WSS) > 0) {
                    wss.set(url);
                    latch.countDown();
                    log.info("成功捕获目标webSocket!");
                }
            };

            page.onWebSocket(handler);

            BrowserUtil.navigatePage(room.getRoomUrl(), page, WaitUntilState.LOAD);
            //提取界面信息
            extractRoomInfo(room, page);
            if (room.isLiving()) {
                try {
                    latch.await(15, TimeUnit.SECONDS);
                    if (wss.get() != null) {
                        WebSocketModel webSocketModel = new WebSocketModel();
                        webSocketModel.setUserAgent(userAgent);
                        webSocketModel.setUrl(wss.get());
                        webSocketModel.setCookie(BrowserUtil.toCookieStr(page.context().cookies()));
                        return webSocketModel;
                    }
                } catch (Exception e) {
                    log.error("等待捕获webSocket失败！", e);
                }
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

    private static WebSocketModel browserGetWssUrl1(DouYinRoom room) {
        String roomUrl = room.getRoomUrl();
        WebSocketModel wss = new WebSocketModel();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return wss;
        }
        Page page = null;
        String userAgent = UserAgentUtil.getRandomUser_Agent();
        boolean headless = room.getSetting() == null || !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(userAgent, headless))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(userAgent);
            BrowserContext context = browser.newContext(contextOptions);
            page = context.newPage();
            setRoomCookie(room, page, roomUrl);
            BrowserUtil.navigatePage(room.getRoomUrl(), page, WaitUntilState.DOMCONTENTLOADED);
            //提取界面信息
            extractRoomInfo(room, page);
            if (room.isLiving()) {
                WebSocket webSocket;
                try {
                    webSocket = BrowserUtil.waitForTargetWebSocket(page, TARGET_WSS, 10_000);
                } catch (Exception e) {
                    log.info("页面刷新，重新监听请求！");
                    page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    webSocket = BrowserUtil.waitForTargetWebSocket(page, TARGET_WSS, 15_000);
                }

                wss.setUserAgent(userAgent);
                wss.setUrl(webSocket.url());
                wss.setCookie(BrowserUtil.toCookieStr(page.context().cookies()));
            }
        } catch (Exception e) {
            log.error("获取直播弹幕wss失败！{}", e.getMessage());
        } finally {
            if (page != null && !page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }
        }
        return wss;
    }


}
