package cn.zhangheng.douyin.util;

import com.zhangheng.util.UserAgentUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/19 星期六 19:50
 * @version: 1.0
 * @description:
 */
public class DouYinUtil {
    private static final Pattern DouyinLinkPattern = Pattern.compile("https://[a-zA-Z0-9.]+douyin\\.com/[^\\s]+");

    /**
     * 从字符串中提取抖音链接
     *
     * @param input 包含抖音链接的字符串
     * @return 提取到的抖音链接，若未找到则返回null
     */
    public static String extractDouyinLink(String input) {
        if (input == null || input.isEmpty()) return null;
        Matcher matcher = DouyinLinkPattern.matcher(input);

        // 查找匹配的链接
        if (matcher.find()) {
            return matcher.group();
        }

        // 未找到匹配的链接
        return null;
    }

    public static String getFinalUrl(String urlStr) throws IOException {
        String currentUrl = urlStr;
        int maxRedirect = 10;
        while (maxRedirect-- > 0) {

            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false); //关闭自动跳转
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            // 手机UA，非常重要
            conn.setRequestProperty("User-Agent", UserAgentUtil.getRandomMobile());

            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location");
                if (location == null) {
                    break;
                }
                currentUrl = new URL(url, location).toString();
                conn.disconnect();
            } else {
                conn.disconnect();
                break;
            }
        }
        return currentUrl;
    }

    public static String extractNoteId(String reflowUrl) {
        Pattern pattern = Pattern.compile("/(\\d+)/?");
        Matcher matcher = pattern.matcher(reflowUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


}
