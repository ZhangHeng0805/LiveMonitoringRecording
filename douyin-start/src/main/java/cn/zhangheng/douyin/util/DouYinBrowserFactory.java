package cn.zhangheng.douyin.util;

import lombok.Getter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/23 星期二 22:34
 * @version: 1.0
 * @description:
 */
public class DouYinBrowserFactory {
    @Getter
    private static final DouYinBrowser browser = new DouYinBrowser();

    public static void closeBrowser() {
        browser.close();
    }
}
