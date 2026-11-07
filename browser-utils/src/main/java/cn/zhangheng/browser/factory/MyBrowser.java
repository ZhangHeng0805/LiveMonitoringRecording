package cn.zhangheng.browser.factory;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.bean.BrowserContent;
import cn.zhangheng.browser.exception.PlaywrightErrorInfo;
import cn.zhangheng.browser.exception.PlaywrightExceptionParser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/06 星期五 18:35
 * @version: 1.0
 * @description:
 */
@Slf4j
public class MyBrowser {

    @Getter
    private final BrowserContent content;

    MyBrowser(BrowserContent content) {
        this.content = content;
    }

    public Page newPage(String initScript) {
        Page page = content.getContext().newPage();
        // 创建新页面并注入反检测脚本
        if (StrUtil.isNotBlank(initScript)) {
            page.addInitScript(initScript);
        }
        return page;
    }

    public void closePage(Page page) {
        if (page == null) return;
        try {
            if (!page.isClosed()) {
                // 关闭页面（可选：触发beforeunload事件）
                page.close(new Page.CloseOptions().setRunBeforeUnload(false));
            }

        } catch (PlaywrightException pe) {
            PlaywrightErrorInfo errorInfo = PlaywrightExceptionParser.parse(pe);
            log.error("关闭Page异常: {}", errorInfo.toSimpleString());
        } catch (Throwable t) {
            log.error("关闭页面失败：{}", t.getMessage(), t);
        }

        //异步 检查关闭空白僵尸页
        CompletableFuture.runAsync(() -> {
            if (content.getPageCount() % 5 != 0) {
                return;
            }
            try {
                BrowserContext context = content.getContext();
                List<Page> pages = context.pages();
                if (pages == null || pages.isEmpty()) {
                    return;
                }
                for (Page p : pages) {
                    if (p == null || p.isClosed()) {
                        continue;
                    }
                    String url;
                    try {
                        url = p.url();
                    } catch (Exception e) {
                        // 页面已经失效，跳过
                        continue;
                    }
                    if ("about:blank".equals(url)) {
                        try {
                            p.close();
                            log.info("成功关闭空白僵尸页, {}", p);
                        } catch (Exception e) {
                            log.error("关闭空白僵尸页异常, {}: {}", p, ThrowableUtil.getAllCauseMessage(e));
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

        });

    }


    void close() {
        if (content != null) {
            content.off();
            BrowserContext context = content.getContext();
            if (context != null && context.browser().isConnected()) {
                context.close();
            }
        }
    }
}
