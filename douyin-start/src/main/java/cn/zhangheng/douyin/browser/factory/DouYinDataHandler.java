package cn.zhangheng.douyin.browser.factory;

import cn.zhangheng.browser.exception.PlaywrightErrorInfo;
import cn.zhangheng.browser.exception.PlaywrightExceptionParser;
import cn.zhangheng.browser.factory.MyBrowser;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.douyin.util.DouYinAPITools;
import cn.zhangheng.record.bean.Room;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static cn.zhangheng.douyin.browser.DouYinBrowserUtil.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/07 星期六 09:11
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinDataHandler {
    private final DouYinRoom room;
    private final DouYinAPITools api;
    private boolean isUseApi = true;
    @Getter
    private long requestCount = 0;

    private final String browserKey;
    private final String roomUrl;
    private boolean forceRefreshBrowser = false;

    public DouYinDataHandler(DouYinRoom room) {
        this.room = room;
        this.api = new DouYinAPITools(Room.Platform.DouYin.getMainUrl());
        this.browserKey = room.getRoomKey();
        this.roomUrl = room.getRoomUrl();
    }

    public boolean request() {
        // 校验房间URL有效性
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return false;
        }
        try {
            if (isUseApi) {
                String pageBody = api.fetchBody(room.getRoomUrl());
                Boolean is = extractRoomInfo(room, pageBody);
                if (Boolean.FALSE.equals(is)) return false;//异常:api获取未空
                if (is != null && !room.isLiving()) return true;//未开启直播
                if (is == null) isUseApi = false;//异常:api获取信息错误，可能被拦截，暂时停用API请求
            }
            //api获取失败 或 开播了，使用浏览器获取更详细的信息
            return browserRequest();
        } finally {
            requestCount++;
        }
    }

    private boolean browserRequest() {
        MyBrowser browser = PlaywrightBrowserFactory.getBrowser(browserKey, room.getSetting().getDelayIntervalSec() * 3000L, forceRefreshBrowser);
        Page page = null;
        try {
            page = browser.newPage(null);
//            setRoomCookie(room, page);
            // 导航到直播间页面
            boolean navigated = BrowserUtil.navigatePage(roomUrl, page, WaitUntilState.DOMCONTENTLOADED);
            if (!navigated) return false;
            //提取界面信息
            boolean success = extractRoomInfo(room, page);
            // 若直播中，等待目标请求完成（替代固定休眠，更高效）
            if (success && room.isLiving()) {
                Request request;
                try {
                    request = BrowserUtil.waitForTargetRequest(page, TARGET_REQUEST_PREFIX, 10_000);
                } catch (Exception e) {
                    log.info("页面刷新，重新监听请求！");
                    page.reload(new Page.ReloadOptions().setTimeout(10_000).setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    request = BrowserUtil.waitForTargetRequest(page, TARGET_REQUEST_PREFIX, 10_000);
                }
                getRequestApi(room, request);
            }
            navigateAfterHandle(browser);
            return success;
        } catch (PlaywrightException e1) {
            PlaywrightErrorInfo errorInfo = PlaywrightExceptionParser.parse(e1);
            log.error("处理直播间[{}]时浏览器发生异常:{}", roomUrl, errorInfo.toSimpleString());
        } catch (Throwable e) {
            log.error("处理直播间[{}]时发生异常,{}", roomUrl, ThrowableUtil.getAllCauseMessage(e));
        } finally {
            browser.closePage(page);
            if (forceRefreshBrowser) forceRefreshBrowser = false;
            if (browser.getContent().getPageCount() % 50 == 0) {
                forceRefreshBrowser = true;
                isUseApi = true;
            }
        }
        return false;
    }


}
