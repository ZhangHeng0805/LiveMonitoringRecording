package cn.zhangheng.record.okhttp;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/17 星期二 21:40
 * @version: 1.0
 * @description:
 */
import com.zhangheng.util.UserAgentUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;
import java.io.IOException;

public class OkHttpShortLinkUtil {

    public static void main(String[] args) throws IOException {
        String url = "https://v.douyin.com/dw1TXU-LQ_A/";
        System.out.println(OkHttpShortLinkUtil.resolveShortUrl(url));
    }

    // 用全局client，不要每次build；client本身已经是单例
    private static final OkHttpClient NO_REDIRECT_CLIENT = OkHttpFactory.getClient()
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    /**
     * 解析短链接，追踪重定向，拿到最终落地URL
     * @param urlStr 短链接
     * @param maxRedirect 最大跳转次数，默认10
     * @return 最终url，追踪失败返回原始url
     */
    public static String resolveShortUrl(String urlStr, int maxRedirect) throws IOException {
        String currentUrl = urlStr;
        while (maxRedirect-- > 0) {
            Request req = new Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", OkHttpUtil.cleanHeaderValue(UserAgentUtil.getRandomMobile()))
                    .build();
            try (Response resp = NO_REDIRECT_CLIENT.newCall(req).execute()) {
                int code = resp.code();
                // 3xx 重定向
                if (code >= 300 && code < 400) {
                    String location = resp.header("Location");
                    if (location == null || location.isEmpty()) {
                        break;
                    }
                    HttpUrl base = HttpUrl.parse(currentUrl);
                    if (base == null) {
                        break;
                    }
                    HttpUrl next = base.resolve(location);
                    if (next == null) {
                        break;
                    }
                    currentUrl = next.toString();
                } else {
                    // 不是3xx，停止追踪
                    break;
                }
            }
        }
        return currentUrl;
    }

    public static String resolveShortUrl(String urlStr) throws IOException {
        return resolveShortUrl(urlStr, 10);
    }
}
