package cn.zhangheng.record.okhttp;

import okhttp3.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/17 星期二 19:47
 * @version: 1.0
 * @description:
 */
public class OkHttpUtil {
    public static Response getResponse(String url, Map<String, String> headers) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .build();
        return OkHttpFactory.getClient().newCall(request).execute();
    }

    public static String getResponseBody(String url, Map<String, String> headers) throws IOException {

        try (Response response = getResponse(url, headers)) {
            if (!response.isSuccessful()) {
                throw new IOException("http code:" + response.code());
            }
            // 读取返回体
            return response.body() != null ? response.body().string() : null;
        }
    }

    public static String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(JSON, jsonBody);

        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .header("Content-Type", "application/json;charset=utf-8")
                .post(body)
                .build();

        try (Response resp = OkHttpFactory.getClient().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("code=" + resp.code() + ", msg=" + resp.message());
            }
            // 读取返回体
            return resp.body() != null ? resp.body().string() : null;
        }
    }

    public static String postForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }
        RequestBody body = formBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .post(body)
                .build();

        try (Response resp = OkHttpFactory.getClient().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("code=" + resp.code());
            }
            return resp.body() != null ? resp.body().string() : null;
        }
    }


    public static String parseResponseCookie(Response response) {
        List<Cookie> cookies = Cookie.parseAll(response.request().url(), response.headers());
        return joinCookieList(cookies);
    }

    /**
     * 把 List<Cookie> 拼接成 Cookie 请求头：k1=v1; k2=v2
     */
    private static String joinCookieList(List<Cookie> cookieList) {
        if (cookieList == null || cookieList.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookieList) {
            sb.append(cookie.name())
                    .append("=")
                    .append(cookie.value())
                    .append("; ");
        }
        // 删掉末尾多余的 ;
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    private static final int BUF_SIZE = 4096;

    /**
     * OkHttp流式读取，取【第 matchIndex 次匹配到 startFlag】之后，直到 stopFlag
     *
     * @param response        okhttp响应
     * @param startFlag       开始标记，可为空（为空时 matchIndex 强制=1，从开头收集）
     * @param stopFlag        结束标记，可为空
     * @param matchIndex 第几次匹配，从1开始；startFlag为空时忽略该参数
     * @param bufferSize      缓冲区大小
     * @param maxCollectSize  收集上限，0=不限制
     * @return 截取文本，匹配不到返回null
     * @throws IOException IO异常
     */
    public static String streamExtractByNthMatch(Response response,
                                                 String startFlag,
                                                 String stopFlag,
                                                 int matchIndex,
                                                 int bufferSize,
                                                 int maxCollectSize) throws IOException {
        ResponseBody body = response.body();
        if (body == null) return null;
        startFlag = startFlag == null ? "" : startFlag;
        stopFlag = stopFlag == null ? "" : stopFlag;
        int startLen = startFlag.length();
        int stopLen = stopFlag.length();
        if (matchIndex < 1) throw new IllegalArgumentException("matchIndex >= 1");

        KMPMatcher startMatcher = startLen > 0 ? new KMPMatcher(startFlag) : null;
        KMPMatcher stopMatcher = stopLen > 0 ? new KMPMatcher(stopFlag) : null;

        // 窗口大小：buffer + 最大标记长度，保证跨块标记可以完整容纳
        int windowSize = bufferSize + Math.max(startLen, stopLen);
        char[] windowBuf = new char[windowSize];
        int windowUsed = 0;

        StringBuilder collectBuf = new StringBuilder();
        int matchedCount = 0;
        boolean inCollect = (startLen == 0);

        try (Reader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)) {
            char[] readBuf = new char[bufferSize];
            int readLen;
            while ((readLen = reader.read(readBuf)) != -1) {
                // 把新数据追加到窗口尾部（窗口里前面是上一轮残留）
                // 残留数据在windowBuf[0, windowUsed)
                System.arraycopy(readBuf, 0, windowBuf, windowUsed, readLen);
                windowUsed += readLen;

                int scanPos = 0;
                if (!inCollect && startMatcher != null) {
                    while (scanPos <= windowUsed - startLen) {
                        int pos = startMatcher.search(windowBuf, scanPos, windowUsed);
                        if (pos == -1) break;
                        matchedCount++;
                        scanPos = pos + startLen;
                        if (matchedCount == matchIndex) {
                            inCollect = true;
                            break;
                        }
                    }
                }

                if (inCollect && stopMatcher != null) {
                    int stopPos = stopMatcher.search(windowBuf, scanPos, windowUsed);
                    if (stopPos != -1) {
                        collectBuf.append(windowBuf, scanPos, stopPos - scanPos);
                        drainBody(body);
                        return collectBuf.toString();
                    } else {
                        collectBuf.append(windowBuf, scanPos, windowUsed - scanPos);
                        checkMaxLimit(collectBuf, maxCollectSize);
                    }
                }

                // 保留窗口末尾 N-1 字符作为下一轮残留，覆盖到窗口头部
                int keep = Math.min(windowUsed, Math.max(startLen, stopLen) - 1);
                if (keep > 0) {
                    System.arraycopy(windowBuf, windowUsed - keep, windowBuf, 0, keep);
                }
                windowUsed = keep;
            }
            drainBody(body);
        }
        return inCollect && collectBuf.length() > 0 ? collectBuf.toString() : null;
    }
    private static void drainBody(ResponseBody body) {
        if (body == null) return;
        try {
            body.source().close();
        } catch (IOException ignored) {}
    }

    // ==================== 多段同时截取 ====================

    /**
     * 单段截取规则：第 matchIndex 次匹配到 startFlag 后开始收集，直到 stopFlag。
     * key 用于结果 Map 的键，可空（按 segment0、segment1… 自动命名）。
     */
    public static class SegmentRule {
        private final String key;
        private final String startFlag;
        private final String stopFlag;
        private final int matchIndex;

        public SegmentRule(String key, String startFlag, String stopFlag, int matchIndex) {
            this.key = key;
            this.startFlag = startFlag == null ? "" : startFlag;
            this.stopFlag = stopFlag == null ? "" : stopFlag;
            // startFlag 为空时从开头收集，matchIndex 无意义，统一按 1 处理
            this.matchIndex = this.startFlag.isEmpty() ? 1 : Math.max(matchIndex, 1);
        }

        public String getKey() {
            return key;
        }

        public String getStartFlag() {
            return startFlag;
        }

        public String getStopFlag() {
            return stopFlag;
        }

        public int getMatchIndex() {
            return matchIndex;
        }
    }

    /**
     * 一次响应中同时截取多段内容：对响应体只做一次流式扫描，
     * 每条规则独立维护自己的匹配进度与收集缓冲，互不影响。
     *
     * <p>返回 Map 保留传入顺序；匹配不到或收集为空时对应值为 null。
     * 若某段只匹配到 startFlag 而到流结束都没出现 stopFlag，则返回已收集到的内容。</p>
     *
     * @param response       okhttp 响应
     * @param rules          多段规则（key 不能重复，重复会抛 IllegalArgumentException）
     * @param bufferSize     每次读取字符数，&lt;=0 时用默认 4096
     * @param maxCollectSize 单段收集上限，0=不限制，超出抛 IllegalStateException
     * @return key -> 截取文本；rules 为空返回空 Map，body 为空返回 null
     * @throws IOException IO异常
     */
    public static Map<String, String> streamExtractSegments(Response response,
                                                            List<SegmentRule> rules,
                                                            int bufferSize,
                                                            int maxCollectSize) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (rules == null || rules.isEmpty()) return result;
        ResponseBody body = response.body();
        if (body == null) return null;

        int readSize = bufferSize > 0 ? bufferSize : BUF_SIZE;
        List<SegmentState> states = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            SegmentRule rule = rules.get(i);
            if (rule == null) continue;
            String key = rule.getKey();
            if (key == null || key.trim().isEmpty()) key = "segment" + i;
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("SegmentRule key 重复：" + key);
            }
            result.put(key, null);
            states.add(new SegmentState(key, rule, maxCollectSize));
        }

        try (Reader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)) {
            char[] readBuf = new char[readSize];
            int readLen;
            outer:
            while ((readLen = reader.read(readBuf)) != -1) {
                for (int i = 0; i < readLen; i++) {
                    boolean allDone = true;
                    for (SegmentState state : states) {
                        state.feed(readBuf[i]);
                        if (!state.done) allDone = false;
                    }
                    // 所有段都收完了，剩余响应体没必要继续解析
                    if (allDone) break outer;
                }
            }
        } finally {
            drainBody(body);
        }

        for (SegmentState state : states) {
            result.put(state.key, state.text());
        }
        return result;
    }

    /**
     * 多段截取的默认缓冲版本
     */
    public static Map<String, String> streamExtractSegments(Response response, List<SegmentRule> rules) throws IOException {
        if (!response.isSuccessful()) throw new IOException("http code=" + response.code());
        return streamExtractSegments(response, rules, BUF_SIZE, 0);
    }

    /** 单条规则的流式状态：独立的 KMP 进度、收集缓冲与结束标记 */
    private static final class SegmentState {
        private final String key;
        private final SegmentRule rule;
        private final int maxCollectSize;
        private final KMPStreamMatcher startMatcher;
        private final KMPStreamMatcher stopMatcher;
        private final StringBuilder buf = new StringBuilder();
        private int matchedCount;
        private boolean collecting;
        private boolean done;

        SegmentState(String key, SegmentRule rule, int maxCollectSize) {
            this.key = key;
            this.rule = rule;
            this.maxCollectSize = maxCollectSize;
            this.startMatcher = rule.startFlag.isEmpty() ? null : new KMPStreamMatcher(rule.startFlag);
            this.stopMatcher = rule.stopFlag.isEmpty() ? null : new KMPStreamMatcher(rule.stopFlag);
            // startFlag 为空时从第一个字符开始收集
            this.collecting = rule.startFlag.isEmpty();
        }

        void feed(char c) {
            if (done) return;
            if (!collecting) {
                if (startMatcher == null) {
                    collecting = true;
                } else {
                    if (startMatcher.feed(c)) {
                        matchedCount++;
                        if (matchedCount == rule.matchIndex) collecting = true;
                    }
                    // 匹配标记的字符本身不计入内容
                    return;
                }
            }
            // 正在收集：先判断结束标记，结束标记的字符也不能计入内容
            if (stopMatcher != null && stopMatcher.feed(c)) {
                // 命中结束标记的最后一个字符没有被 append，
                // 所以只需从缓冲里去掉结束标记的前 stopLen-1 个字符
                int stopLen = rule.stopFlag.length() - 1;
                int newLen = buf.length() - stopLen;
                buf.setLength(Math.max(newLen, 0));
                collecting = false;
                done = true;
                return;
            }
            buf.append(c);
            if (maxCollectSize > 0 && buf.length() > maxCollectSize) {
                throw new IllegalStateException("收集内容超过最大限制：" + maxCollectSize);
            }
        }

        String text() {
            // 已收集到结束标记，或流结束时仍在收集中（stopFlag 为空/未出现）都返回已收集内容
            if (!done && !collecting) return null;
            return buf.length() > 0 ? buf.toString() : null;
        }
    }

    /**
     * 逐字符 KMP 匹配器：每喂一个字符推进状态，匹配完成返回 true 并重新开始（非重叠匹配）
     */
    private static final class KMPStreamMatcher {
        private final char[] pattern;
        private final int[] next;
        private int j;

        KMPStreamMatcher(String pat) {
            this.pattern = pat.toCharArray();
            int m = pattern.length;
            next = new int[m];
            int k = 0;
            for (int i = 1; i < m; i++) {
                k = next[i - 1];
                while (k > 0 && pattern[i] != pattern[k]) k = next[k - 1];
                if (pattern[i] == pattern[k]) k++;
                next[i] = k;
            }
        }

        boolean feed(char c) {
            while (j > 0 && c != pattern[j]) j = next[j - 1];
            if (c == pattern[j]) j++;
            if (j == pattern.length) {
                j = 0;
                return true;
            }
            return false;
        }
    }

    private static void checkMaxLimit(StringBuilder sb, int max) {
        if (max <= 0) return;
        if (sb.length() > max) throw new IllegalStateException("收集内容超过最大限制：" + max);
    }

    public static String streamSubstringByFlag(Response response, String startFlag, String stopFlag, int matchStartIndex) throws IOException {
        if (!response.isSuccessful()) throw new IOException("http code=" + response.code());
        if (matchStartIndex < 1) matchStartIndex = 1;
        return streamExtractByNthMatch(response, startFlag, stopFlag, matchStartIndex, BUF_SIZE, 0);
    }

    /**
     * 清洗header值，去掉okhttp不允许的特殊字符
     */
    public static String cleanHeaderValue(String val) {
        if (val == null) return null;
        // 替换长破折号，还有其他容易踩坑的特殊横线
        val = val.replace('\u2011', '-');
        val = val.replace('\u2012', '-');
        val = val.replace('\u2013', '-');
        val = val.replace('\u2014', '-');
        val = val.replace('\u2015', '-');
        return val;
    }

    static class KMPMatcher {
        private final char[] pattern;
        private final int[] next;

        public KMPMatcher(String pat) {
            this.pattern = pat.toCharArray();
            int m = pattern.length;
            next = new int[m];
            int j = 0;
            for (int i = 1; i < m; i++) {
                j = next[i - 1];
                while (j > 0 && pattern[i] != pattern[j]) j = next[j - 1];
                if (pattern[i] == pattern[j]) j++;
                next[i] = j;
            }
        }

        int search(char[] src, int start, int end) {
            int j = 0;
            for (int i = start; i < end; i++) {
                while (j > 0 && src[i] != pattern[j]) j = next[j - 1];
                if (src[i] == pattern[j]) j++;
                if (j == pattern.length) {
                    return i - pattern.length + 1;
                }
            }
            return -1;
        }
    }
}
