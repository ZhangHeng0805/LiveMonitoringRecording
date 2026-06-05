package cn.zhangheng.douyin.browser;

import cn.zhangheng.browser.*;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.douyin.bean.DouYinRoom;
import cn.zhangheng.douyin.util.DouYinUtils;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cn.zhangheng.douyin.browser.DouYinBrowserFactory.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/21 星期日 05:02
 * @version: 1.0
 * @description: 抖音直播间信息获取工具类
 * 浏览器对象类，请求重复调用，需手动关闭浏览器
 */
public class DouYinBrowser implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DouYinBrowser.class);

    private static final Map<String, BrowserCounter> counters = new ConcurrentHashMap<>();
    private static final Map<String, DouYinUtils> apis = new ConcurrentHashMap<>();
    private static final AtomicInteger totalCount = new AtomicInteger(0);

    // 线程安全的浏览器实例（volatile确保多线程可见性）
    private PlaywrightBrowser browser;

    DouYinBrowser() {
        browser = createBrowser();
    }


    public Map<String, Object> getCount() {
        Map<String, Object> data = new HashMap<>();
        if (browser != null) {
            Map<String, Integer> browserCount = browser.getAllCount();
            data.put("browserCount", browserCount);
        }
        Map<String, Object> totalCounter = new HashMap<>();
        for (Map.Entry<String, BrowserCounter> entry : counters.entrySet()) {
            BrowserCounter counter = entry.getValue();
            Map<String, Object> map = new HashMap<>();
            String url = counter.getUrl();
            map.put("url", url);
            map.put("totalCount", counter.getCount());
            int count = apis.containsKey(url) ? apis.get(url).getCount() : 0;
            map.put("apiCount", count);
            map.put("lastTime", TimeUtil.toTime(counter.getLastTimestamp()));
            totalCounter.put(counter.getThreadName(), map);
        }
        totalCounter.put("totalCount", totalCount.get());
        data.put("totalCounter", totalCounter);

        return data;
    }


    boolean isFetch = true;

    /**
     * 发起请求并提取直播间信息
     */
    public boolean request(DouYinRoom room) {
        // 校验房间URL有效性
        String roomUrl = room.getRoomUrl();
        if (roomUrl == null || roomUrl.trim().isEmpty()) {
            log.error("直播间URL为空，无法发起请求");
            return false;
        }
        try {
            if (isFetch) {
                DouYinUtils api = apis.getOrDefault(roomUrl, new DouYinUtils());
                if (!apis.containsKey(roomUrl)) {
                    apis.put(roomUrl, api);
                }
                String pageBody = api.fetchRoomPageBody(room.getId());
                Boolean is = extractRoomInfo(room, pageBody);
                if (Boolean.FALSE.equals(is)) return false;
                if (is != null && !room.isLiving()) return true;
                if (is == null) isFetch = false;
            }
            return browserRequest1(room);
        } finally {
            //统计请求
            BrowserCounter counter = counters.getOrDefault(roomUrl, new BrowserCounter(roomUrl));
            if (!counters.containsKey(roomUrl)) {
                counters.put(roomUrl, counter);
            }
            counter.add();
            totalCount.incrementAndGet();
            if (totalCount.get() % 100 == 0) {
                isFetch = true;
            }
            //关闭浏览器的请求次数
            if (totalCount.get() % 1000 == 0) {
                closeBrowser();
                log.debug("请求总次数达到{}次，关闭浏览器释放资源！", totalCount.get());
                isFetch = true;
            }
        }
    }



    public boolean browserRequest1(DouYinRoom room) {
        String roomUrl = room.getRoomUrl();
        Page page = null;
        try {
            checkAndInitBrowser();
            synchronized (this) {
                page = browser.newPage();
            }
            setRoomCookie(room, page, roomUrl);
            // 导航到直播间页面
            boolean navigated = BrowserUtil.navigatePage(roomUrl, page, WaitUntilState.DOMCONTENTLOADED);
            if (!navigated) return false;
            //提取界面信息
            boolean b = extractRoomInfo(room, page);
            // 若直播中，等待目标请求完成（替代固定休眠，更高效）
            if (room.isLiving()) {
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
            if (!b) {
                //为了应对手动验证码
                TimeUnit.SECONDS.sleep(RandomUtil.createRandom(5, 10));
            }
            return b;
        } catch (PlaywrightException e1) {
            log.error("处理直播间[{}]时浏览器发生异常:{}", roomUrl, ThrowableUtil.toString(e1, 128));
        } catch (Throwable e) {
            log.error("处理直播间[{}]时发生异常,{}", roomUrl, ThrowableUtil.getAllCauseMessage(e));
        } finally {
            // 确保页面关闭，释放资源
            if (browser != null) {
                browser.closePage(page);
            }
        }
        return false;
    }


    public boolean browserRequest2(DouYinRoom room) {
        String roomUrl = room.getRoomUrl();
        Page page = null;
        Consumer<Request> requestHandler = null;
        try {
            checkAndInitBrowser();
            synchronized (this) {
                page = browser.newPage();
            }
            setRoomCookie(room, page, roomUrl);
            // 【关键】把 latch 放在 原子引用 里，保证全局唯一
            CountDownLatch latch = new CountDownLatch(1);
            // 注册请求监听器（提取目标请求信息）
            requestHandler = request -> {
                String url = request.url();
                if (url.startsWith(TARGET_REQUEST_PREFIX)) {
                    getRequestApi(room, request);
                    latch.countDown();
                    log.info("已捕获目标请求！");
                }
            };
            page.onRequest(requestHandler);
            BrowserUtil.navigatePage(roomUrl, page, WaitUntilState.LOAD);
            Boolean is = extractRoomInfo(room, page);
            if (room.isLiving()) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("等待捕获目标失败！", e);
                }
                return room.getBrowserApi().getUpdateTimes() > 0;
            } else {
                latch.countDown();
            }
            return Boolean.TRUE.equals(is);
        } catch (PlaywrightException e1) {
            log.error("处理直播间[{}]时浏览器发生异常:{}", roomUrl, e1.getMessage().substring(0, 128));
        } catch (Throwable e) {
            log.error("处理直播间[{}]时发生异常,{}", roomUrl, ThrowableUtil.getAllCauseMessage(e)); // 记录完整堆栈
        } finally {
            if (requestHandler != null) {
                page.offRequest(requestHandler);
            }
            // 确保页面关闭，释放资源
            if (browser != null) {
                browser.closePage(page);
            }
        }
        return false;
    }

    /**
     * 检查抖音直播间页面核心元素是否显示
     *
     * @param page
     */
    private void checkliveSelectors(Page page) {
        // 抖音直播间核心元素（优先级从高到低，可根据实际情况调整）
        String[] liveSelectors = {
                ".xgplayer-fullscreen-parent",
        };
        for (String selector : liveSelectors) {
            try {
//                    System.out.println("等待核心元素加载：" + selector);
                page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.VISIBLE) // 必须可见，而非仅存在
                        .setTimeout(10_000));
                break; // 找到任意一个核心元素即可
            } catch (TimeoutError e) {
                log.warn("元素 " + selector + " 加载超时，尝试下一个...");
            }
        }
    }

    /**
     * 检查并初始化浏览器（线程安全）
     */
    private synchronized void checkAndInitBrowser() {
        if (browser == null || !browser.isRunning()) {
            // 关闭旧实例（若存在）
            closeBrowser();
            // 初始化新浏览器
            try {
                browser = createBrowser();
                log.info("浏览器实例初始化成功");
            } catch (Exception e) {
                throw new RuntimeException("初始化浏览器失败", e);
            }
        }
    }

    private PlaywrightBrowser createBrowser() {
        Setting setting = new Setting();
        boolean headless = !Objects.equals(setting.getBrowserHeadless(), Boolean.FALSE);
        PlaywrightBrowser browser = new PlaywrightBrowser(Constant.User_Agent, headless);
        browser.setIsPageClear(setting.getBrowserIsPageClear());
        browser.setUpdateContextCounts(setting.getUpdateContextCounts());
        browser.startAutoClearContextThread(300, new AutoClearContextThreadListener() {
            @Override
            public void triggerCheck(int intervalSec, Map<String, BrowserContent> allContextThread) {
                if (counters.isEmpty()) return;
                List<String> remove = counters.values().stream().filter(browserCounter -> {
                    long lastUsedTimestamp = browserCounter.getLastTimestamp();
                    return System.currentTimeMillis() - lastUsedTimestamp > intervalSec * 1000L;
                }).map(BrowserCounter::getUrl).collect(Collectors.toList());
                for (String url : remove) {
                    counters.remove(url);
                    apis.remove(url);
                }
                if (allContextThread.isEmpty()) {
                    closeBrowser();
                }
            }
        });
        return browser;
    }

    /**
     * 关闭浏览器实例（线程安全）
     */
    private synchronized void closeBrowser() {
        if (browser != null) {
            try {
                browser.close();
                log.info("浏览器实例已关闭");
            } catch (Exception e) {
                log.warn("关闭浏览器时发生异常", e);
            } finally {
                browser.stopAutoClearContextThread();
                browser = null;
            }
        }
    }

    public void closeContext() {
        if (browser != null) {
            browser.closeContext();
        }
    }

    public void threadLocalClear() {
        if (browser != null) {
            browser.threadLocalClear();
        }
    }

    /**
     * 关闭资源（实现Closeable，支持try-with-resources）
     */
    @Override
    public void close() {
        closeBrowser();
    }
}
