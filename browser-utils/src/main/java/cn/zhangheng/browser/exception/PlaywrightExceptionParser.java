package cn.zhangheng.browser.exception;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/05 星期四 15:45
 * @version: 1.0
 * @description:
 */

import com.microsoft.playwright.PlaywrightException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaywrightExceptionParser {

    private static final int MSG_TRUNCATE_LEN = 1024;

    // 规则列表：【顺序非常重要，细分在前，兜底在后】
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 网络大类细分
        RULES.add(new Rule(Pattern.compile("net::ERR_INTERNET_DISCONNECTED", Pattern.CASE_INSENSITIVE),
                "NETWORK_DISCONNECTED", "本机网络断开，无互联网连接", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_NAME_NOT_RESOLVED", Pattern.CASE_INSENSITIVE),
                "NETWORK_DNS", "DNS域名解析失败", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_CONNECTION_RESET", Pattern.CASE_INSENSITIVE),
                "NETWORK_RESET", "连接被重置（对端断开）", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_CONNECTION_TIMED_OUT", Pattern.CASE_INSENSITIVE),
                "NETWORK_CONN_TIMEOUT", "TCP连接超时", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_PROXY_CONNECTION_FAILED", Pattern.CASE_INSENSITIVE),
                "NETWORK_PROXY", "代理连接失败，代理不可用", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_CERT_", Pattern.CASE_INSENSITIVE),
                "NETWORK_CERT", "证书错误，SSL校验失败", false));
        RULES.add(new Rule(Pattern.compile("net::ERR_NETWORK_CHANGED", Pattern.CASE_INSENSITIVE),
                "NETWORK_CHANGED", "网络链路发生切换", true));
        RULES.add(new Rule(Pattern.compile("net::ERR_CONNECTION_REFUSED", Pattern.CASE_INSENSITIVE),
                "NETWORK_REFUSED", "目标端口拒绝连接", true));

        // 原生Playwright业务异常
        RULES.add(new Rule(Pattern.compile("Timeout .* exceeded", Pattern.CASE_INSENSITIVE),
                "TIMEOUT", "操作超时，未在限定时间内完成", true));
        RULES.add(new Rule(Pattern.compile("Target page, context or browser has been closed", Pattern.CASE_INSENSITIVE),
                "TARGET_CLOSED", "页面/浏览器上下文已关闭", false));
        RULES.add(new Rule(Pattern.compile("Protocol error", Pattern.CASE_INSENSITIVE),
                "PROTOCOL", "CDP协议通信异常", true));
        RULES.add(new Rule(Pattern.compile("AssertionError|expected .* to be", Pattern.CASE_INSENSITIVE),
                "ASSERT", "断言校验失败", false));

        // 导航兜底
        RULES.add(new Rule(Pattern.compile("navigation.*failed|net::ERR_", Pattern.CASE_INSENSITIVE),
                "NAVIGATE", "页面跳转/未知网络错误", true));
    }

    // 提取用正则
    // message=' 后面第一行报错
    private static final Pattern PATTERN_MSG_LINE = Pattern.compile("message='([^'\n]+)", Pattern.CASE_INSENSITIVE);
    // 捕获两个等号分隔线中间的logs内容
    private static final Pattern PATTERN_LOG_BLOCK = Pattern.compile(
            "={27} logs ={27}\\s*\\r?\\n(.*?)\\r?\\n={60}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern PATTERN_SELECTOR = Pattern.compile("selector[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_URL = Pattern.compile("url:?\\s*([http][^\\s,;\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HOST = Pattern.compile("https?://([^/\\s:]+)", Pattern.CASE_INSENSITIVE);

    public static PlaywrightErrorInfo parse(Throwable ex) {
        return parse(ex, false);
    }

    public static PlaywrightErrorInfo parse(Throwable ex, boolean captureStack) {
        PlaywrightErrorInfo info = new PlaywrightErrorInfo();
        info.setExceptionClass(ex.getClass().getName());

        String rawMsg = ex.getMessage();
        if (rawMsg != null && rawMsg.length() > MSG_TRUNCATE_LEN) {
            rawMsg = rawMsg.substring(0, MSG_TRUNCATE_LEN) + "...[truncated]";
        }
        info.setRawMessage(rawMsg);
        // 组装 innerMessage：message首行 + logs块内容
        if (rawMsg != null) {
            String msgLine = null;
            String logContent = null;

            Matcher msgLineMatcher = PATTERN_MSG_LINE.matcher(rawMsg);
            if (msgLineMatcher.find()) {
                msgLine = msgLineMatcher.group(1).trim();
            }

            Matcher logBlockMatcher = PATTERN_LOG_BLOCK.matcher(rawMsg);
            if (logBlockMatcher.find()) {
                logContent = logBlockMatcher.group(1).trim();
            }

            StringBuilder sb = new StringBuilder();
            if (msgLine != null) {
                sb.append(msgLine);
            }
            if (logContent != null) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(logContent);
            }
            String combined = sb.toString();
            if (combined.length() > MSG_TRUNCATE_LEN) {
                combined = combined.substring(0, MSG_TRUNCATE_LEN) + "...[truncated]";
            }
            info.setInnerMessage(combined.isEmpty() ? null : combined);
        }

        if (captureStack) {
            info.setStackTrace(getStackTrace(ex));
        } else {
            info.setStackTrace(null);
        }

        Throwable root = getRootCause(ex);
        String rootMsg = root.getMessage();
        if (rootMsg != null && rootMsg.length() > MSG_TRUNCATE_LEN) {
            rootMsg = rootMsg.substring(0, MSG_TRUNCATE_LEN) + "...[truncated]";
        }
        info.setRootCauseMsg(rootMsg);

        String msg = ex.getMessage() == null ? "" : ex.getMessage();

        // 循环按顺序匹配规则，匹配即停止
        boolean matched = false;
        for (Rule rule : RULES) {
            if (rule.getPattern().matcher(msg).find()) {
                info.setErrorKind(rule.getErrorKind());
                info.setBriefMsg(rule.getBriefMsg());
                info.setRetryable(rule.isRetryable());
                matched = true;
                break;
            }
        }
        // 全部规则都没命中，走OTHER兜底
        if (!matched) {
            info.setErrorKind("OTHER");
            info.setBriefMsg("Playwright未知异常");
            info.setRetryable(false);
        }

        // 提取 selector
        Matcher selM = PATTERN_SELECTOR.matcher(msg);
        if (selM.find()) {
            info.setSelector(selM.group(1));
        }

        // 提取 url + host
        Matcher urlM = PATTERN_URL.matcher(msg);
        if (urlM.find()) {
            String url = urlM.group(1);
            info.setPageUrl(url);
            Matcher hostM = PATTERN_HOST.matcher(url);
            if (hostM.find()) {
                info.setFailedHost(hostM.group(1));
            }
        }

        return info;
    }

    public static PlaywrightErrorInfo parse(PlaywrightException ex, boolean captureStack, com.microsoft.playwright.Page page) {
        PlaywrightErrorInfo info = parse(ex, captureStack);
        if (page != null && !page.isClosed()) {
            String pageUrl = page.url();
            info.setPageUrl(pageUrl);
            Matcher hostM = PATTERN_HOST.matcher(pageUrl);
            if (hostM.find()) {
                info.setFailedHost(hostM.group(1));
            }
        }
        return info;
    }

    /**
     * 获取根cause
     */
    private static Throwable getRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * 抓取完整堆栈字符串
     */
    private static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
        }
        return sw.toString();
    }

    /**
     * 判断是否包含PlaywrightException（递归查cause）
     */
    public static boolean isPlaywrightException(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof PlaywrightException) {
            return true;
        }
        return isPlaywrightException(e.getCause());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class Rule {
        private Pattern pattern;
        private String errorKind;
        private String briefMsg;
        private boolean retryable;
    }
}