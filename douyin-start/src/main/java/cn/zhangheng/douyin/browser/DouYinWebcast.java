package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.UserAgentUtil;
import cn.zhangheng.common.bean.Constant;
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

import static cn.zhangheng.browser.PlaywrightBrowser.*;
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


        DouYinRoom room = new DouYinRoom("664141913258");
        WebSocketModel wss = getWss(room);
        String cookie = "bd_ticket_guard_client_web_domain=2; live_use_vvc=%22false%22; SelfTabRedDotControl=%5B%5D; is_staff_user=false; SEARCH_RESULT_LIST_TYPE=%22multi%22; theme=%22light%22; enter_pc_once=1; __druidClientInfo=JTdCJTIyY2xpZW50V2lkdGglMjIlM0EwJTJDJTIyY2xpZW50SGVpZ2h0JTIyJTNBMCUyQyUyMndpZHRoJTIyJTNBMCUyQyUyMmhlaWdodCUyMiUzQTAlMkMlMjJkZXZpY2VQaXhlbFJhdGlvJTIyJTNBMS4yNSUyQyUyMnVzZXJBZ2VudCUyMiUzQSUyMk1vemlsbGElMkY1LjAlMjAoV2luZG93cyUyME5UJTIwMTAuMCUzQiUyMFdpbjY0JTNCJTIweDY0KSUyMEFwcGxlV2ViS2l0JTJGNTM3LjM2JTIwKEtIVE1MJTJDJTIwbGlrZSUyMEdlY2tvKSUyMENocm9tZSUyRjEzNy4wLjAuMCUyMFNhZmFyaSUyRjUzNy4zNiUyMEVkZyUyRjEzNy4wLjAuMCUyMiU3RA==; UIFID_TEMP=5a9ddafc2df5b1d5b452c3de63aa171cea81dc4215a756cefc72ee80e24fb54c40dfc4b8981929ccfddf0653c3ec2e5f79351bddacd916948dd0411b0fe8925958cc17773e03f0cd890642d5f898bd9d; UIFID=5a9ddafc2df5b1d5b452c3de63aa171cea81dc4215a756cefc72ee80e24fb54c40dfc4b8981929ccfddf0653c3ec2e5fcdc039e87d31934a3fc35d81b221e88e262ab6f699c5a03ccfc0c9092d754c9f3825e61176c3b190dc2a6342eb992fe615767e90dbb4a9808c52731f6db9c240fccd77e3433612a88d8d11d3cb7d1fa3efce192d0678f974188617ae2fd38d95c7573b2fd1d388565db9e19788e28dcc; d_ticket=e5950e926d3ad780373ac55e04257c0f3f739; uid_tt=daf55c2e97ba1d280b7f8515ff724899; uid_tt_ss=daf55c2e97ba1d280b7f8515ff724899; sid_tt=e5b28bfbc928cda5651522f2301e7b01; sessionid=e5b28bfbc928cda5651522f2301e7b01; sessionid_ss=e5b28bfbc928cda5651522f2301e7b01; passport_assist_user=CkviyTRPBV4j9tv0awQSB8u_CdkvBvzpEITD_Ycr2OeLMsjLSAyiMT1sxUo3OysG-sKOMxLZ1JuDGA015unVB4S6tL_SRh12uljCEooaSgo8AAAAAAAAAAAAAE_HSZq_QzFjBrD6jGSh3bs_azusqInHJfY_vCjieiW1DAQ-iKvRYAJerPRNXBZ0xMBYEIGFgw4Yia_WVCABIgEDxphlcA%3D%3D; login_time=1764609186667; _bd_ticket_crypt_cookie=7861027d57c1c8c209fdc8fec22bccd1; is_dash_user=1; my_rd=2; has_biz_token=false; passport_csrf_token=ca80747193bd7e108afd5f79aea5792e; passport_csrf_token_default=ca80747193bd7e108afd5f79aea5792e; sid_guard=e5b28bfbc928cda5651522f2301e7b01%7C1777550479%7C5184000%7CMon%2C+29-Jun-2026+12%3A01%3A19+GMT; session_tlb_tag=sttt%7C4%7C5bKL-8kozaVlFSLyMB57Af________-vPmkmNxSi1531J8ZEH57cTFWvJ3LX05uOujM8clMRKw8%3D; sid_ucp_v1=1.0.0-KDEzZTY4Zjg3ODJiZjBhNDVmODQ4ZDkzNjFkY2VkODMyYTI0OWU1MjYKIQjoxfDQkcyCBBCPic3PBhjvMSAMMMXCxqgGOAVA-wdIBBoCbGYiIGU1YjI4YmZiYzkyOGNkYTU2NTE1MjJmMjMwMWU3YjAx; ssid_ucp_v1=1.0.0-KDEzZTY4Zjg3ODJiZjBhNDVmODQ4ZDkzNjFkY2VkODMyYTI0OWU1MjYKIQjoxfDQkcyCBBCPic3PBhjvMSAMMMXCxqgGOAVA-wdIBBoCbGYiIGU1YjI4YmZiYzkyOGNkYTU2NTE1MjJmMjMwMWU3YjAx; live_private_user=0; download_guide=%223%2F20260509%2F0%22; EnhanceDownloadGuide=%220_0_1_1778357803_0_0%22; FOLLOW_NUMBER_YELLOW_POINT_INFO=%22MS4wLjABAAAAD7EYPi2x9AUrmuJ9kdA6riElyMAyvvm9PwtgwoEdBeDYR1tyGPqO8SEgsD-AWO-f%2F1778428800000%2F0%2F1778405360309%2F0%22; h265ErrorNum=-1; playRecommendGuideTagCount=1; __live_version__=%221.1.5.1831%22; totalRecommendGuideTagCount=0; __security_mc_1_s_sdk_crypt_sdk=bc5bc24e-4ae2-9a66; __security_mc_1_s_sdk_cert_key=e1f38ac0-4053-b2c1; __security_mc_1_s_sdk_sign_data_key_web_protect=f85b0f74-4d0f-bec2; volume_info=%7B%22isMute%22%3Afalse%2C%22isUserMute%22%3Afalse%2C%22volume%22%3A0.985%7D; stream_recommend_feed_params=%22%7B%5C%22cookie_enabled%5C%22%3Atrue%2C%5C%22screen_width%5C%22%3A1536%2C%5C%22screen_height%5C%22%3A864%2C%5C%22browser_online%5C%22%3Atrue%2C%5C%22cpu_core_num%5C%22%3A8%2C%5C%22device_memory%5C%22%3A8%2C%5C%22downlink%5C%22%3A10%2C%5C%22effective_type%5C%22%3A%5C%224g%5C%22%2C%5C%22round_trip_time%5C%22%3A0%7D%22; PhoneResumeUidCacheV1=%7B%222263211979842280%22%3A%7B%22time%22%3A1778564915595%2C%22noClick%22%3A2%7D%7D; home_can_add_dy_2_desktop=%220%22; is_support_rtm_web_ts=1; strategyABtestKey=%221778635991.293%22; ttwid=1%7CFHllA_RPY2jQvNfl5Zp_ot0BYNe63k7dDXzthvEH68k%7C1778635992%7C8b573c4f5ebab807b640efa4f80d1ace7541e3bca236db61a88d9c5ef5962486; stream_player_status_params=%22%7B%5C%22is_auto_play%5C%22%3A1%2C%5C%22is_full_screen%5C%22%3A0%2C%5C%22is_full_webscreen%5C%22%3A0%2C%5C%22is_mute%5C%22%3A0%2C%5C%22is_speed%5C%22%3A1%2C%5C%22is_visible%5C%22%3A0%7D%22; bd_ticket_guard_client_data=eyJiZC10aWNrZXQtZ3VhcmQtdmVyc2lvbiI6MiwiYmQtdGlja2V0LWd1YXJkLWl0ZXJhdGlvbi12ZXJzaW9uIjoxLCJiZC10aWNrZXQtZ3VhcmQtcmVlLXB1YmxpYy1rZXkiOiJCREdwOW4wRXNYZy84T08yRk1lNzAwdzJqODd1K0pMZzd3RXduVS9PaEJscGtTQ1ZwckRmU2hsTHlsUUFVMUxIMEVnUW5KcU9yWmVzZFZlYnNNM2JSbUU9IiwiYmQtdGlja2V0LWd1YXJkLXdlYi12ZXJzaW9uIjoyfQ%3D%3D; publish_badge_show_info=%220%2C0%2C0%2C1778642863626%22; biz_trace_id=6a6c40c1; LivePausePop=%22%257B%2522todayCount%2522%253A7%252C%2522closeNum%2522%253A1%252C%2522todayShowRoom%2522%253A%25227639181306413796102%252C7639190699581213446%252C7639193425648126760%252C7639198454774680358%2522%252C%2522lastTimer%2522%253A1778644099464%257D%22; sdk_source_info=7e276470716a68645a606960273f276364697660272927676c715a6d6069756077273f276364697660272927666d776a68605a607d71606b766c6a6b5a7666776c7571273f275e58272927666a6b766a69605a696c6061273f27636469766027292762696a6764695a7364776c6467696076273f275e5827292771273f273c3c3d3634363031333d3232342778; bit_env=ZLaP6u3kRkxIC6P3iNoupUVeUE0qgXOuE8_qV9jlrK68lQbx1qz_I9HhTqzMX_6mZj22autwUkDwDxIa4DKgPRuMukbKZuGbdEMgsGoyDAhngDyzeNRSlxM5XRlpI4aTed0lgdXJJ3zfh5hgQIHoOYyaOmU5v0YKf9x8cNU1E6lycJigM_XtIkw-8LT3a1oZjViFCws1sG70dq8nAE8jaOSaR5eZObt72hwc5CKBg9GgrJxI2xEaWddrApPWxyxO9uSfe_x0ioQskq2f-RVikdWdWN107FmOuO99-Aqs9AEQIAREuZflwZBHZXSaKeXnGLKbO1Y5y8dxfO0h2DT8ylqPo9sBPLw-TKltphTiCW9sKuaUKAjAR-UCEDmiMhh9uUngtybdftyYJtWzl7_SfI3JI0nk_4rV0sE71oC1giTOD3Ud6dB2vQClfWitUjdBvJLqq9ZTARpx74aBbxVmAOTKwVqrwZLj1KUjsV4hmUB46WDxWlNFLzMu3MuVz7Je9zN3U3VoTXnRmQc2oTJh3pSJZInxR1TJyx4czFoQLog%3D; gulu_source_res=eyJwX2luIjoiMzczYjUwZjEwMjE1MTQ5YzM3YTMxYjVjNjA1ZDk0Y2JmYTI2YzkwZWE5MGIxMTNiN2JhMmU1ZTVjNjAyOTJhZiJ9; passport_auth_mix_state=fd60zdh0fir4wr6zk5bgk4zay5s0nd3kum10vsl22gnzcd9m; bd_ticket_guard_client_data_v2=eyJyZWVfcHVibGljX2tleSI6IkJER3A5bjBFc1hnLzhPTzJGTWU3MDB3Mmo4N3UrSkxnN3dFd25VL09oQmxwa1NDVnByRGZTaGxMeWxRQVUxTEgwRWdRbkpxT3JaZXNkVmVic00zYlJtRT0iLCJ0c19zaWduIjoidHMuMi45YzY0M2ZhZWFmZTdjNjM5Zjk3NGNiYTFhOTEwYmRkYzg3ZTdhMDYwN2YyNzQ2NzAxMWZmZDFkY2NiNzk1YjNkYzRmYmU4N2QyMzE5Y2YwNTMxODYyNGNlZGExNDkxMWNhNDA2ZGVkYmViZWRkYjJlMzBmY2U4ZDRmYTAyNTc1ZCIsInJlcV9jb250ZW50Ijoic2VjX3RzIiwicmVxX3NpZ24iOiJKTVJ2aHpJY3RLS1pZSEhkYklKQ1V3Z0FyTGhKZ3FSTWh4VUhiR0hIZDBvPSIsInNlY190cyI6IiM5OUNtM1pydzlXQjd2a09RUlFIb0lZaVBSOGNtOVYxeStwcFVmTU9xTUtPVENlK1M2Q2RlQVh3Vy9OcE8ifQ%3D%3D; live_can_add_dy_2_desktop=%220%22; odin_tt=b9387ff380336d2af8920e74c652490324ee09a653d9123e253124f042a0bfa6ebeed2266d490eb015c85516600d5b3e7368feefeae79982d093e82c531747e6; live_debug_info=%7B%22roomId%22%3A%227639198454774680358%22%2C%22resolution%22%3A%7B%22width%22%3A1088%2C%22height%22%3A1920%7D%2C%22fps%22%3A6%2C%22audioDataRate%22%3A48000%2C%22droppedFrames%22%3A9%2C%22totalFrames%22%3A149%2C%22videoBuffer%22%3A%5B%5D%2C%22src%22%3A%22https%3A%2F%2Fpull-flv-t95.douyincdn.com%2Fstage%2Fstream-119362475780473655_or4.flv%3Fexpire%3D1779250121%26sign%3Dc59f560c428ea38b1a0702c29603cd7a%26neq%3D1%26arch_hrchy%3Dh1%26major_anchor_level%3Dcommon%26exp_hrchy%3Dh1%26unique_id%3Dstream-119362475780473655_823_flv_or4%26t_id%3D037-202605131208405751228349C34F293154-2Whvka%26_session_id%3D037-202605131208405751228349C34F293154-2Whvka.1778645321109.24146%26rsi%3D1%26abr_pts%3D-800%22%2C%22linkmicInfo%22%3A%7B%22uiLayout%22%3A0%2C%22playModes%22%3A%5B%5D%2C%22allDevices%22%3A%22%E8%BF%9E%E7%BA%BF%E8%AE%BE%E5%A4%87%EF%BC%9A%E7%94%B3%E8%AF%B7%E8%BF%9E%E7%BA%BF%E5%90%8E%E6%89%8D%E8%8E%B7%E5%8F%96%22%2C%22audioInputs%22%3A%5B%5D%2C%22videoInputs%22%3A%5B%5D%7D%2C%22href%22%3A%22https%3A%2F%2Flive.douyin.com%2F510417099315%3Fanchor_id%3D930614155941547%26category_name%3Dall%26is_vs%3D0%26page_type%3Dmain_category_page%26vs_ep_group_id%3D%26vs_episode_id%3D%26vs_episode_stage%3D%26vs_season_id%3D%22%7D; IsDouyinActive=true";
        wss.setCookie(cookie);
        wss.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0");
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
             Browser browser = playwright.chromium().launch(getLaunchOptions(Constant.User_Agent, headless));
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

            navigatePage(room.getRoomUrl(), page, WaitUntilState.LOAD);
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
