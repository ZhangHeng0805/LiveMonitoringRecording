package cn.zhangheng.douyin.util;

import com.microsoft.playwright.Browser;
import com.zhangheng.util.RandomUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.zhangheng.record.util.UserAgentUtil.PC_VIEWPORTS;


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

    public static String getDouyinFingerprintScript() {
        String initScript =
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n" +
                        "if (!window.chrome) {\n" +
                        "  window.chrome = {\n" +
                        "    runtime: {},\n" +
                        "    webstore: {}\n" +
                        "  };\n" +
                        "}\n" +
                        "Object.defineProperty(navigator, 'plugins', {\n" +
                        "  get: () => [\n" +
                        "    { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: '' },\n" +
                        "    { name: 'Widevine Content Decryption Module', filename: 'widevinecdm.dll', description: '' }\n" +
                        "  ]\n" +
                        "});\n" +
                        "Object.defineProperty(navigator, 'mimeTypes', {\n" +
                        "  get: () => [\n" +
                        "    { type: 'application/pdf', suffixes: 'pdf', description: '' }\n" +
                        "  ]\n" +
                        "});\n" +
                        "Object.defineProperty(navigator, 'languages', { get: () => ['zh‑CN', 'zh', 'en'] });\n";
        // java层随机硬件参数
        int mem = new int[]{4, 8, 16}[RandomUtil.randomInt(3)];
        int cpuCore = new int[]{4, 6, 8, 12}[RandomUtil.randomInt(4)];

        String script = initScript + String.format("Object.defineProperty(navigator, 'deviceMemory', { get: () => %d });\n" +
                "Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => %d });", mem, cpuCore);
        return script;
    }
    /**
     * 一键构建抖音PC网页Context配置：随机UA + 随机分辨率 + zh-CN + Asia/Shanghai
     */
    public static Browser.NewContextOptions buildDouyinPcContextOptions(String ua) {
        int[] vp = PC_VIEWPORTS[new Random().nextInt(PC_VIEWPORTS.length)];
        return new Browser.NewContextOptions()
                .setUserAgent(ua)
                .setViewportSize(vp[0], vp[1])
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai");
    }
}
