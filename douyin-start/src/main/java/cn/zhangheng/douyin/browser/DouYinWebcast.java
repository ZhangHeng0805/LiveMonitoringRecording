package cn.zhangheng.douyin.browser;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.browser.UserAgentUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import cn.zhangheng.douyin.DouYinUtils;
import cn.zhangheng.record.DouYinWebSocket;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.zhangheng.browser.PlaywrightBrowser.*;
import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.*;
import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.TARGET_REQUEST_PREFIX;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 04:48
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinWebcast {
    public static final String TARGET_WSS= ".douyin.com/webcast/im/push/v2/";
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
            DouYinUtils utils = new DouYinUtils();
            socket.connect(wss, utils.getTtwid(), utils.getUserAgent());
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
                    DouYinUtils utils = new DouYinUtils();
                    socket.connect(wss, utils.getTtwid(), utils.getUserAgent());
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
        return browserGet2(room);
    }

    public static String browserGet2(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return null;
        }
        AtomicReference<String> wss = new AtomicReference<>(null);
        Page page = null;
        Consumer<WebSocket> handler = null;
        boolean headless = !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(getLaunchOptions(Constant.User_Agent, headless));
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
            BrowserContext context = browser.newContext(contextOptions);
            page = context.newPage();
            setRoomCookie(room, page, roomUrl);
            CountDownLatch latch = new CountDownLatch(1);

            handler = ws -> {
                String url = ws.url();
//                System.out.println(url);
                if (url.indexOf(TARGET_WSS)>0) {
                    wss.set(url);
                    latch.countDown();
                    log.info("成功捕获目标webSocket!");
                }
            };

            page.onWebSocket(handler);

            navigatePage(room.getRoomUrl(), page, WaitUntilState.LOAD);

//            page.click("body", new Page.ClickOptions().setPosition(100,100));
            //提取界面信息
//            extractRoomInfo(room, page);
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

    private static String browserGet1(DouYinRoom room) {
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return null;
        }
        Page page = null;
        boolean headless = !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(getLaunchOptions(Constant.User_Agent, headless))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
            BrowserContext context = browser.newContext(contextOptions);
            page = context.newPage();
            setRoomCookie(room, page, roomUrl);
            navigatePage(room.getRoomUrl(), page, WaitUntilState.DOMCONTENTLOADED);
            //提取界面信息
//            extractRoomInfo(room, page);
            if (room.isLiving()) {
                WebSocket webSocket;
                try {
                    webSocket = waitForTargetWebSocket(page, TARGET_WSS, 10_000);
                } catch (Exception e) {
                    log.info("页面刷新，重新监听请求！");
                    page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    webSocket = waitForTargetWebSocket(page, TARGET_WSS, 15_000);
                }
                return webSocket.url();
            }
            System.out.println("直播间未开启直播！");
        } catch (Exception e) {
            log.error("获取直播弹幕wss失败！{}", e.getMessage());
        } finally {
            if (page != null && !page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }
        }
        return null;
    }

}
