package cn.zhangheng.douyin.util;

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
    /**
     * 从字符串中提取抖音链接
     *
     * @param input 包含抖音链接的字符串
     * @return 提取到的抖音链接，若未找到则返回null
     */
    public static String extractDouyinLink(String input) {
        if (input == null || input.isEmpty()) return null;
        // 正则表达式：匹配以https://v.douyin.com/开头，后面跟非空白字符的链接
//        String regex = "https://v\\.douyin\\.com/[\\w\\-]+/?";
        String regex = "https://[\\w\\.]+douyin\\.com/[\\w\\-/?&=]+";

        // 编译正则表达式
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1");

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
        Pattern pattern = Pattern.compile("note/(\\d+)");
        Matcher matcher = pattern.matcher(reflowUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
