package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.BrowserUtil;
import cn.zhangheng.browser.UserAgentUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.LogUtil;
import cn.zhangheng.douyin.DouYinRoom;
import cn.zhangheng.douyin.DouYinUtils;
import cn.zhangheng.record.DouYinWebSocket;
import cn.zhangheng.record.DouyinMessageOuter;
import cn.zhangheng.record.MessageListener;
import cn.zhangheng.record.WebSocketModel;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
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
    public static final String TARGET_WSS = ".douyin.com/webcast/im/push/v2/";
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

    public static void main(String[] args) throws IOException {


        DouYinRoom room = new DouYinRoom("308260295933");
        room.setSetting(new Setting());
        WebSocketModel wss = getWss(room);
        String cookie = "=douyin.com; __ac_nonce=06a063c690091d031eb76; __ac_signature=_02B4Z6wo00f01DnYhfwAAIDBElLeORrzfhQ5-IFAAGR22b; enter_pc_once=1; UIFID_TEMP=9d704f4367f8284d35e23aa47661f96cb95baabd6797c6af2acee0c4f19d037258f6435a61db05d29332eceff691f777fbd5ecb02fb96437934f1b4ea18d42e63c9ec90f206100530917d345dcee2961030cce4dc8069ec80d0d88a7c3d5158d824471b2352456226a3f7d6a9767dfcc; x-web-secsdk-uid=ca856d08-0786-4320-81f6-6c085c27e486; s_v_web_id=verify_mp5zrpkk_lVB7nvNH_G5Re_4qNz_ARxN_RhmUFS2Uhtn4; =douyin.com; device_web_cpu_core=8; device_web_memory_size=8; architecture=amd64; is_support_rtm_web_ts=1; home_can_add_dy_2_desktop=%220%22; dy_swidth=1280; dy_sheight=720; stream_recommend_feed_params=%22%7B%5C%22cookie_enabled%5C%22%3Atrue%2C%5C%22screen_width%5C%22%3A1280%2C%5C%22screen_height%5C%22%3A720%2C%5C%22browser_online%5C%22%3Atrue%2C%5C%22cpu_core_num%5C%22%3A8%2C%5C%22device_memory%5C%22%3A8%2C%5C%22downlink%5C%22%3A10%2C%5C%22effective_type%5C%22%3A%5C%224g%5C%22%2C%5C%22round_trip_time%5C%22%3A0%7D%22; strategyABtestKey=%221778793583.929%22; passport_csrf_token=4347928c095a68470b982511db4a73f4; passport_csrf_token_default=4347928c095a68470b982511db4a73f4; fpk1=U2FsdGVkX19Cp78aWKcaw1+W3rHkAxm0bolOk/5EnSeyPRGHcImZ6m9w0L78M50LapnmICAWsFxzkNPwcxztGg==; fpk2=dcb22b8e8d56fcecf8f8bdb19dc70c3b; biz_trace_id=583da0c2; ttwid=1%7Cu9PEZy18jJFHoSEynWJWNZZlbphBEnAf_AtuTHsHqwE%7C1778793585%7C7bda1cc9803cfc71007d20136936ee0278c3c2710241ef0aa15d45c9b02b42fe; bd_ticket_guard_client_web_domain=2; sdk_source_info=7e276470716a68645a606960273f276364697660272927676c715a6d6069756077273f276364697660272927666d776a68605a607d71606b766c6a6b5a7666776c7571273f275e58272927666a6b766a69605a696c6061273f27636469766027292762696a6764695a7364776c6467696076273f275e582729277672715a646971273f2763646976602729277f6b5a666475273f2763646976602729276d6a6e5a6b6a716c273f2763646976602729276c6b6f5a7f6367273f27636469766027292771273f2735323c333d30363c323d323234272927676c715a75776a716a666a69273f2763646976602778; bit_env=RbUCen6Qp_Hx3jlysMkpjOD6lZDdSFuSddtwRDmipHy5cylsmc_5NnmPUObi5sv82dDl4I1Ioq7uqXF9ZfyCrYatVlvRjDpKoaKXeIv1U2_pHZm7dEsZhb4jKjVPHNcsO_ZoiIfUSo6JLhTOnmxn1w11zrCFU8Zv7Ud3yVgk-MJBOr530CET09ssAbZOxHZzT2wfhJTEnn4Q0DUtcpd_QRNdefkVzeFhpWYgQ4W9n1lU_vVD8NY23Pj-DBoCJnrFIh-xBdkCgoloNja-lGQDNyypq57DbLM8Q6dBQEmxgNDfgyq2buL-4pt5XP3VVMpAKQfGcs1jrtOAhQSSOZq83ic_K0jIKjWEQNyQYpzS0FPgZL8hGxmVfikdW0IXMeEHPTuQCtcBmLGaUyVsIyDD_V_4z-jhcWjM6X70VDouAMNvexBMVKx4veQeh_dzv63cOTCvzWNrjHJEi21E0k7uWUU4mm98XnphPkSnaJ5V3xetHzJfgRnLL0vmRz-pKXyPEIolJXJee1ohhnwTRdPpannU5XZt5FvQl0AdVRMagMw%3D; gulu_source_res=eyJwX2luIjoiNDc0NDg1Y2ZhOGMxNjk4NTY1YzE0MWE0NTBkY2M2YzY2ZDdmMTc1NTBmZjY2NDQ0OTgxM2NlYmQzNWQxNzYxMiJ9; passport_auth_mix_state=e7qv78sjdd8ml2udk7plh4eewizkw25m; passport_assist_user=CktrBWKlhtWVBW7rg-xpmcEF10Mtnc96zCYKjfuihW8dEZZ6LmBslnYKLB9Row7ZMXNOWim2OSSEsoBGktkjCibIsmVhQTAaeyGE1E0aSgo8AAAAAAAAAAAAAFBraRQRx5QjEdc1DosdAn318a159q6Ne8A00zJSdTotjpKGJLjTD1RcAYI3LtO6mAybEMK8kQ4Yia_WVCABIgEDWHQyLw%3D%3D; n_mh=DMhsPybsa4Xz7E_lw-Cs3mJE0PJA8I_XDMxGKoxlrys; sid_guard=f18856d0d332a2279093cbf730809f42%7C1778793594%7C5184000%7CMon%2C+13-Jul-2026+21%3A19%3A54+GMT; uid_tt=ad5fe78b4cb14cf2153e6fbedb155ded; uid_tt_ss=ad5fe78b4cb14cf2153e6fbedb155ded; sid_tt=f18856d0d332a2279093cbf730809f42; sessionid=f18856d0d332a2279093cbf730809f42; sessionid_ss=f18856d0d332a2279093cbf730809f42; session_tlb_tag=sttt%7C3%7C8YhW0NMyoieQk8v3MICfQv_________bJRgcIbUbNgmte7eo3K4BZGZ0UTB9spiIqbtx7PrO2NQ%3D; is_staff_user=false; has_biz_token=false; sid_ucp_v1=1.0.0-KDlkYWUzNDM1YzJiZjU5MTg0OTg3YTBmZjc0MjBkNDUzODZjOTI4ZDMKIQjoxfDQkcyCBBD6-JjQBhjvMSAMMMXCxqgGOAdA9AdIBBoCbHEiIGYxODg1NmQwZDMzMmEyMjc5MDkzY2JmNzMwODA5ZjQy; ssid_ucp_v1=1.0.0-KDlkYWUzNDM1YzJiZjU5MTg0OTg3YTBmZjc0MjBkNDUzODZjOTI4ZDMKIQjoxfDQkcyCBBD6-JjQBhjvMSAMMMXCxqgGOAdA9AdIBBoCbHEiIGYxODg1NmQwZDMzMmEyMjc5MDkzY2JmNzMwODA5ZjQy; bd_ticket_guard_web_domain=2; _bd_ticket_crypt_cookie=9e05c376df3ef1cb9654384e739ae27f; __security_mc_1_s_sdk_sign_data_key_web_protect=67e976cf-4e0a-8090; __security_mc_1_s_sdk_cert_key=0583e35f-4428-8101; __security_mc_1_s_sdk_crypt_sdk=d95a07ec-4d45-ae6f; __security_server_data_status=1; login_time=1778793594404; DiscoverFeedExposedAd=%7B%7D; UIFID=9d704f4367f8284d35e23aa47661f96cb95baabd6797c6af2acee0c4f19d037258f6435a61db05d29332eceff691f777fbd5ecb02fb96437934f1b4ea18d42e69d4e7da6f8eaf7c3337f690e4f79ccd9e00ea1a03d7111744a9d68f83132a132941cbb7c60fd5edfe1c3f4845679b51b607c62991d1df6040b65548d107e895126da5c3bccdc21538ba4032f58c61c0813801d5622406d46ecdfa84a0288df6d862593ff4edb96ca115a729a9d40bdc2e4745de3a1f10217b3ba9ec44f2d931c; SelfTabRedDotControl=%5B%5D; FOLLOW_NUMBER_YELLOW_POINT_INFO=%22MS4wLjABAAAAD7EYPi2x9AUrmuJ9kdA6riElyMAyvvm9PwtgwoEdBeDYR1tyGPqO8SEgsD-AWO-f%2F1778860800000%2F0%2F1778793597765%2F0%22; publish_badge_show_info=%220%2C0%2C0%2C1778793599025%22; bd_ticket_guard_client_data=eyJiZC10aWNrZXQtZ3VhcmQtdmVyc2lvbiI6MiwiYmQtdGlja2V0LWd1YXJkLWl0ZXJhdGlvbi12ZXJzaW9uIjoxLCJiZC10aWNrZXQtZ3VhcmQtcmVlLXB1YmxpYy1rZXkiOiJCQk9FRUFjeE5LT1hXeUVvYmJ6cXU0bmlBSTlYM3VlTVd3dGkyT2pJa1JYeXdSYkVHMVd5RitnVE1pY0N2Wm5rWVBzU1RKYk5YVXp4UlFEMkpkR0thanc9IiwiYmQtdGlja2V0LWd1YXJkLXdlYi12ZXJzaW9uIjoyfQ%3D%3D; bd_ticket_guard_client_data_v2=eyJyZWVfcHVibGljX2tleSI6IkJCT0VFQWN4TktPWFd5RW9iYnpxdTRuaUFJOVgzdWVNV3d0aTJPaklrUlh5d1JiRUcxV3lGK2dUTWljQ3ZabmtZUHNTVEpiTlhVenhSUUQySmRHS2Fqdz0iLCJ0c19zaWduIjoidHMuMi44OWFlZjUzYWJlYTkyNGQ2MTY1YTNiYmZkZmU1YjE2MDY3NWRjNzg0YjkyNGFlZDkyYjQzMTNmMzhlN2E0ZGFkYzRmYmU4N2QyMzE5Y2YwNTMxODYyNGNlZGExNDkxMWNhNDA2ZGVkYmViZWRkYjJlMzBmY2U4ZDRmYTAyNTc1ZCIsInJlcV9jb250ZW50Ijoic2VjX3RzIiwicmVxX3NpZ24iOiJ0bVMxN01OaWxXUEtEUFAvUTh1R3c0VTdQcHhjQU9Pc0pPZUFQYmh5ekhNPSIsInNlY190cyI6IiNVZW5nZHNVdVVFVmtrbHQ0RGlSaHpYYXJFMkIwQUFObTNqeUtpcnF3aXpBQkMwYTNhOXFTUEl1N1FVdnEifQ%3D%3D; IsDouyinActive=false; odin_tt=5e3b205ef202031b34ccd7477f02baaf9d4379572c1126082dcf8e9d0357bb555f3298808ec0ed82d9fcefbcb3e017e8b00bc1f783e31ccc2e5caf63f7c4de43";
        wss.setCookie(cookie);
//        wss.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0");
        if (wss.isUse()) {
            String fileName = TimeUtil.toTime(room.getStartTime(), "yyyy-MM-dd HH-mm-ss") + "直播弹幕.log";
            LogUtil logUtil = new LogUtil(room, fileName);
            DouYinWebSocket socket = new DouYinWebSocket();
            socket.setMessageListener(new MessageListener() {
                @Override
                public void chat(String info, DouyinMessageOuter.ChatMessage msg) {
                    String x = "【聊天】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void gift(String info, DouyinMessageOuter.GiftMessage msg) {
                    String x = "【礼物】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void stats(String info, DouyinMessageOuter.RoomUserSeqMessage msg) {
                    String x = "【统计】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void online(String info, DouyinMessageOuter.RoomStatsMessage msg) {
                    String x = "【在线】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void roomRank(String info, DouyinMessageOuter.RoomRankMessage msg) {
                    String x = "【排名】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void member(String info, DouyinMessageOuter.MemberMessage msg) {
                    String x = "【进场】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void like(String info, DouyinMessageOuter.LikeMessage msg) {
                    String x = "【点赞】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void social(String info, DouyinMessageOuter.SocialMessage msg) {
                    String x = "【关注】" + info;
                    System.out.println(x);
                    if (logUtil != null)
                        logUtil.highLog(x);
                }

                @Override
                public void control(String info, DouyinMessageOuter.ControlMessage msg) {
                    String x = "【状态】" + info;
                    if (logUtil != null)
                        logUtil.highLog(x);
                    System.out.println(x);
                    if (msg.getStatus() == 3) {
                        System.out.println("直播间已关闭！");
                        socket.close();
                        if (logUtil != null)
                            logUtil.close();
                    }
                }
            });
            socket.connect(wss);
        }

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


    public void stop() {
        isRunning = false;
        if (socket != null) {
            socket.close();
        }
        webcastThread.interrupt();
    }

    public static WebSocketModel getWss(DouYinRoom room) {
        String url = browserGetWssUrl1(room);
        WebSocketModel wss = new WebSocketModel();
        wss.setUrl(url);
        if (room.getBrowserApi() != null && room.getBrowserApi().getHeaders() != null) {
            Map<String, String> headers = room.getBrowserApi().getHeaders();
            wss.setCookie(headers.get("cookie"));
            wss.setUserAgent(headers.get("user-agent"));
        } else {
            DouYinUtils utils = new DouYinUtils();
            wss.setCookie("ttwid=" + utils.getTtwid());
            wss.setUserAgent(utils.getUserAgent());
        }
        return wss;
    }

    public static String browserGetWssUrl2(DouYinRoom room) {
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
        if (room.getSetting() != null) {
            headless = !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(Constant.User_Agent, headless));
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
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
                    return wss.get();
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

    private static String browserGetWssUrl1(DouYinRoom room) {
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return null;
        }
        Page page = null;
        boolean headless = room.getSetting() == null || !Objects.equals(room.getSetting().getBrowserHeadless(), Boolean.FALSE);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(BrowserUtil.getLaunchOptions(Constant.User_Agent, headless))
        ) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(UserAgentUtil.getRandomUser_Agent());
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
