package cn.zhangheng.douyin;


import cn.hutool.core.annotation.PropIgnore;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 02:23
 * @version: 1.0
 * @description: 直播间
 */
@Getter
@Setter
@ToString
public class DouYinRoom extends Room {
    private static final Logger log = LoggerFactory.getLogger(DouYinRoom.class);
    @PropIgnore
    private String data_url;
    @PropIgnore
    private String user_agent = Constant.User_Agent;
    //总观看人数
    private String totalUserStr;
    //当前在线人数
    private String userCountStr;
    //喜欢点赞数
    private int likeCount;
    //直播间封面
    private List<String> coverList;


    public DouYinRoom(String id) {
        super(id);
    }

    @Override
    public void initSetting(Setting setting) {
        String cookie = setting.getCookieDouYin();
        if (StrUtil.isNotBlank(cookie)) {
            setCookie(cookie);
        }
    }


    @Override
    public String getCover() {
        if (coverList != null && !coverList.isEmpty()) {
            return coverList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public Platform getPlatform() {
        return Platform.DouYin;
    }

    @Override
    public String initRoomUrl() {
        return getPlatform().getMainUrl() + getId();
    }

    @Override
    public Map<String, String> getRequestHead() {
        Map<String, String> header = super.getRequestHead();
        header.put("Referer", getPlatform().getMainUrl() + getId());
        header.put("Origin", getPlatform().getMainUrl());
        return header;
    }

    @Override
    public void reset() {
        super.reset();
        setData_url(null);
        setUser_agent(null);
        setTotalUserStr(null);
        setUserCountStr(null);
        setCoverList(null);
    }

    /**
     * 解析URL中的参数，返回键值对Map
     *
     * @param urlStr URL字符串（如 https://example.com/path?name=test&age=20&name=java）
     * @return 参数Map，支持多值参数（用逗号拼接）
     */
    public static Map<String, String> parseUrlParams(String urlStr) {
        Map<String, String> paramMap = new HashMap<>();
        try {
            URL url = new URL(urlStr);
            String query = url.getQuery(); // 获取查询字符串（如 "name=test&age=20"）

            if (query == null || query.isEmpty()) {
                return paramMap; // 无参数时返回空Map
            }

            // 分割参数对（按 & 分割）
            String[] paramPairs = query.split("&");
            for (String pair : paramPairs) {
                // 分割键和值（按第一个 = 分割，支持值中包含 =）
                int eqIndex = pair.indexOf("=");
                if (eqIndex == -1) {
                    // 只有键没有值（如 ?token）
                    String key = decode(pair);
                    paramMap.put(key, paramMap.getOrDefault(key, ""));
                } else {
                    String key = decode(pair.substring(0, eqIndex));
                    String value = decode(pair.substring(eqIndex + 1));
                    // 处理多值参数（如 ?name=test&name=java，合并为 "test,java"）
                    if (paramMap.containsKey(key)) {
                        value = paramMap.get(key) + "," + value;
                    }
                    paramMap.put(key, value);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解析URL参数失败：" + e.getMessage());
        }
        return paramMap;
    }

    // URL解码（处理空格、特殊字符）
    private static String decode(String str) {
        try {
            return java.net.URLDecoder.decode(str, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return str; // 解码失败时返回原始字符串
        }
    }
}
