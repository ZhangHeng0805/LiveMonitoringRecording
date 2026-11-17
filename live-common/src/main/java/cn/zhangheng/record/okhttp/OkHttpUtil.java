package cn.zhangheng.record.okhttp;

import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
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

    public static String postJson(String url, String jsonBody,Map<String, String> headers) throws IOException {
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
    public static String postForm(String url, Map<String, String> params,Map<String, String> headers) throws IOException {
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
     * 流式读取，支持提前终止，找到homeStore标记就停止读取
     *
     * @param call      目标请求
     * @param stopFlag 终止标记：\"homeStore\":
     * @return 拼接的片段
     */
    public static String streamGetUntilFlag(Call call, String stopFlag) throws IOException {
        try (Response resp = call.execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("status=" + resp.code());
            }
            ResponseBody body = resp.body();
            if (body == null) {
                return null;
            }
            try (InputStream in = body.byteStream()) {
                byte[] buf = new byte[BUF_SIZE];
                ByteBuffer byteBuffer = ByteBuffer.allocate(BUF_SIZE);
                CharBuffer charBuffer = CharBuffer.allocate(BUF_SIZE);
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE);
                StringBuilder sb = new StringBuilder();
                int readLen;
                while ((readLen = in.read(buf)) != -1) {
                    byteBuffer.clear();
                    byteBuffer.put(buf, 0, readLen);
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()) {
                        CoderResult cr = decoder.decode(byteBuffer, charBuffer, false);
                        if (cr.isOverflow()) {
                            charBuffer.flip();
                            sb.append(charBuffer);
                            charBuffer.clear();
                        } else if (cr.isUnderflow()) {
                            break;
                        } else {
                            cr.throwException();
                        }
                    }
                    // 【核心】读到标记就停止，不再继续read
                    if (sb.indexOf(stopFlag) >= 0) {
                        break;
                    }
                }
                // 收尾解码
                CoderResult cr;
                do {
                    cr = decoder.decode(byteBuffer, charBuffer, true);
                    if (cr.isOverflow()) {
                        charBuffer.flip();
                        sb.append(charBuffer);
                        charBuffer.clear();
                    }
                } while (cr.isOverflow());
                cr = decoder.flush(charBuffer);
                if (!cr.isUnderflow()) {
                    cr.throwException();
                }
                charBuffer.flip();
                sb.append(charBuffer);
                return sb.toString();
            }
        } finally {
            // 如果提前break，取消call，关闭socket，不再接收剩余数据
            if (!call.isExecuted()) {
                call.cancel();
            }
        }
    }
    /** 清洗header值，去掉okhttp不允许的特殊字符 */
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

}
