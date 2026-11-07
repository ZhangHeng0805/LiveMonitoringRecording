package cn.zhangheng.douyin.browser;

import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.bean.BrowserAPI;
import cn.zhangheng.browser.util.BrowserUtil;
import cn.zhangheng.douyin.bean.DouYinRoom;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/23 星期二 22:34
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinBrowserFactory {

    // 1. 必须声明为 null，volatile 保证多线程可见性、禁止指令重排
    private static volatile DouYinBrowser browser = null;

    // 私有构造，禁止外部实例化
    private DouYinBrowserFactory() {
    }

    // 2. 标准双重检查锁（DCL）单例，线程安全
    public static DouYinBrowser getBrowser() {
        // 第一次检查：不加锁，提升性能
        if (browser == null) {
            // 加锁：保证同一时间只有一个线程初始化
            synchronized (DouYinBrowserFactory.class) {
                // 第二次检查：防止多线程同时进入第一层判断
                if (browser == null) {
                    browser = new DouYinBrowser();
                }
            }
        }
        return browser;
    }

    // 3. 安全关闭：必须和getBrowser用同一把锁，保证原子性
    public static boolean closeBrowser() {
        synchronized (DouYinBrowserFactory.class) {
            if (browser != null) {
                try {
                    browser.close();
                    return true;
                } catch (Throwable e) {
                    log.error("Failed to close browser", e);
                } finally {
                    browser = null;
                }
            }
        }
        return false;
    }


}
