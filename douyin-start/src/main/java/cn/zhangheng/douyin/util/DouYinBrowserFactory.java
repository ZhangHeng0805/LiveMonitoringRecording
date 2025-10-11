package cn.zhangheng.douyin.util;

import cn.hutool.core.text.UnicodeUtil;
import cn.zhangheng.douyin.DouYinRoom;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/23 星期二 22:34
 * @version: 1.0
 * @description:
 */
@Slf4j
public class DouYinBrowserFactory {

    static final Pattern STATUS_STR_PATTERN = Pattern.compile("\\\\\"status_str\\\\\":\\\\\"([^\"]+)\\\\\"");
    static final Pattern NICKNAME_PATTERN = Pattern.compile("\\\\\"nickname\\\\\":\\\\\"([^\"]+)\\\\\"");
    static final Pattern AVATAR_PATTERN = Pattern.compile("\\\\\"url_list\\\\\":\\[\\\\\"([^\"]+)\\\\\"");
    static final String TARGET_REQUEST_PREFIX = "https://live.douyin.com/webcast/room/web/enter/";

    private static volatile DouYinBrowser browser = new DouYinBrowser();

    public static DouYinBrowser getBrowser() {
        if (browser == null) {  // 第一次检查
            synchronized (DouYinBrowserFactory.class) {  // 加锁
                if (browser == null) {  // 第二次检查
                    browser = new DouYinBrowser();
                }
            }
        }
        return browser;
    }


    public static synchronized void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    /**
     * 提取直播间信息（独立方法，便于维护）
     */
    static void extractRoomInfo(DouYinRoom room, String pageSource) {
        if (pageSource == null) {
            log.warn("页面源码为空，无法提取房间信息");
            return;
        }
        pageSource=pageSource.substring(pageSource.lastIndexOf("\\\"homeStore\\\":"));
        // 提取直播状态
        String status = extractStr(pageSource, STATUS_STR_PATTERN, null);
        room.setLiving("2".equals(status));

        // 提取昵称（避免重复提取）
        if (room.getNickname() == null) {
            String nickname = extractStr(pageSource, NICKNAME_PATTERN,
                    new HashSet<>(Collections.singletonList("$undefined")));
            room.setNickname(nickname);
        }
        if (room.getAvatar() == null) {
            String avatar = UnicodeUtil.toString(extractStr(pageSource, AVATAR_PATTERN, null));
            room.setAvatar(avatar);
        }
    }

    /**
     * 通用正则提取方法（复用逻辑）
     *
     * @param content       原始字符串
     * @param excludeValues 需要排除的值集合（如{"", "0", "null"}）
     * @return 第一个不在排除集合中的值；若所有值都被排除，返回null
     */
    static String extractStr(String content, Pattern pattern, Set<String> excludeValues) {
        // 参数校验：避免空指针
        if (content == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(content);

        // 循环查找所有匹配项
        while (matcher.find()) {
            // 提取值并处理转义字符
            String value = matcher.group(1).trim();

            // 排除指定值
            if (excludeValues == null || !excludeValues.contains(value)) {
                return value; // 返回第一个有效匹配
            }
            // 若在排除集合中，继续查找下一个
        }

        // 所有匹配都被排除或无匹配
        return null;
    }
}
