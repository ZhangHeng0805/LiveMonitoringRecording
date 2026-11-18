package cn.zhangheng.record.okhttp;

import okhttp3.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
     * @param matchStartIndex 第几次匹配，从1开始；startFlag为空时忽略该参数
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

    private static void checkMaxLimit(StringBuilder sb, int max) {
        if (max <= 0) return;
        if (sb.length() > max) throw new IllegalStateException("收集内容超过最大限制：" + max);
    }

    public static String streamSubstringByFlag(Response response, String startFlag, String stopFlag, int matchStartIndex) throws IOException {
        if (!response.isSuccessful()) throw new IOException("http code=" + response.code());
        if (matchStartIndex < 1) matchStartIndex = 1;
        return streamExtractByNthMatch(response, startFlag, stopFlag, matchStartIndex, 2048, 0);
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
