package cn.zhangheng.douyin.bean;


import cn.hutool.core.annotation.PropIgnore;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.bean.BrowserAPI;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.Setting;
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
    private BrowserAPI browserApi;
    @JmxAttribute(desc = "总观看人数")
    //总观看人数
    private volatile String totalUserStr;
    @JmxAttribute(desc = "当前在线人数")
    //当前在线人数
    private volatile String userCountStr;
    @JmxAttribute(desc = "喜欢点赞数")
    //喜欢点赞数
    private volatile long likeCount;
    //直播间封面
    private List<String> coverList;
    @JmxAttribute(desc = "是否运行弹幕监听")
    //弹幕是否运行
    private volatile boolean isSubtitleRunning;
    @JmxAttribute(desc = "直播间统计")
    private DouYinRoomCounter counter;


    public DouYinRoom(String id) {
        super(id);
    }

    @Override
    public void initSetting(Setting setting) {
        String cookie = Setting.parseCookie(setting.getCookieDouYin());
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
    public String initRoomUrl() {
        this.platform = Platform.DouYin;
        return getPlatform().getMainUrl() + getId();
    }

    @Override
    public Map<String, String> getRequestHead() {
        Map<String, String> header = super.getRequestHead();
        header.put("Referer", getPlatform().getMainUrl() + getId());
        header.put("Origin", getPlatform().getMainUrl());
        if (getBrowserApi() != null && getBrowserApi().getHeaders() != null) {
            header.put("User-Agent", getBrowserApi().getHeaders().get("user-agent"));
        }
        return header;
    }

    @Override
    public void reset() {
        super.reset();
        setTotalUserStr(null);
        setUserCountStr(null);
        setCoverList(null);
        setBrowserApi(null);
    }

}
