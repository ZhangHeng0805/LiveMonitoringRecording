package cn.zhangheng.douyin.util;

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
}
