package cn.zhangheng.douyin.browser;

import cn.hutool.core.map.MapUtil;
import cn.zhangheng.browser.BrowserAPI;
import cn.zhangheng.browser.PlaywrightBrowser;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.douyin.DouYinRoom;
import cn.zhangheng.douyin.DouYinUtils;
import cn.zhangheng.record.DouYinWebSocket;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.WebSocket;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.util.function.Consumer;

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
    public static final String TARGET_REQUEST_PREFIX = "wss://webcast100-ws-web-lq.douyin.com/webcast/im/push/v2/";

    public static void main(String[] args) {
        DouYinRoom room = new DouYinRoom("208823316033");

        boolean start = start(room);
        if (start) {
            String wss = room.getBrowserApi().getDataUrl();
            DouYinWebSocket socket = new DouYinWebSocket();
            socket.setMessageListener(new MessageListener() {
                @Override
                public void chat(String info, DouyinMessageOuter.ChatMessage msg) {
                    System.out.println(info);
                }
            });
            socket.connect(wss, DouYinUtils.fetchTtwid(), Constant.User_Agent);
        }
    }

    public static boolean start(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return false;
        }
        Page page = null;
        try (PlaywrightBrowser browser = new PlaywrightBrowser(Constant.User_Agent, true)) {
            page = browser.newPage(Constant.User_Agent);
            setRoomCookie(room, page, roomUrl);
            browser.navigatePage(room.getRoomUrl(), page);
            //提取界面信息
            extractRoomInfo(room, page);
            if (room.isLiving()) {
                WebSocket webSocket = null;
                try {
                    webSocket = browser.waitForTargetWebSocket(page, TARGET_REQUEST_PREFIX, 10_000);
                } catch (Exception ignored) {
                    log.info("页面刷新，重新监听！");
                    page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    webSocket = browser.waitForTargetWebSocket(page, TARGET_REQUEST_PREFIX, 10_000);
                }
                getWebSocketAPI(room, webSocket);
                return room.getBrowserApi().getUpdateTimes() > 0;
            }
            System.out.println("直播间未开启直播！");
            return false;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } finally {

        }
    }

    private static void getWebSocketAPI(DouYinRoom room, WebSocket webSocket) {
        if (webSocket == null) return;
        BrowserAPI browserAPI = new BrowserAPI(TARGET_REQUEST_PREFIX);
        browserAPI.setDataUrl(webSocket.url());
        room.setBrowserApi(browserAPI);
    }
}
