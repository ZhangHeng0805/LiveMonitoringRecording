package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.Getter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 00:40
 * @version: 1.0
 * @description:
 */
@Data
public abstract class Room {
    protected String id;

    public void setId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IllegalArgumentException("id不能为空！");
        }
        this.id = id.trim();
    }

    protected volatile String title;
    //    protected String owner;
    protected volatile String nickname;
    protected volatile boolean living;
    //开始时间
    protected volatile Date startTime;
    //    protected Date updateTime;
    //直播封面
    protected volatile String cover;
    //用户头像
    protected volatile String avatar;

    //直播流不同清晰度地址（由高到低排序）
    protected volatile Map<String, String> streams;


    private String cookie;

    private final String roomUrl;

    public void setCookie(String cookie) {
        if (StrUtil.isNotBlank(cookie))
            this.cookie = cookie;
    }

    public Room(String id) {
        this.id = id;
        roomUrl = initRoomUrl();
    }

    public abstract void initSetting(Setting setting);

    public abstract Platform getPlatform();

    public String getFlvUrl() {
        if (streams != null && !streams.isEmpty()) {
            return getStreams().entrySet().iterator().next().getValue();
        } else {
            return null;
        }
    }

    public abstract String initRoomUrl();

    @Getter
    public enum Platform {
        DouYin("抖音", "https://live.douyin.com/"),
        Bili("Bilibili", "https://live.bilibili.com/"),
        KuaiShou("快手", "https://live.kuaishou.cn/"),
        ;

        private final String name;
        private final String mainUrl;

        Platform(String name, String mainUrl) {
            this.name = name;
            this.mainUrl = mainUrl;
        }
    }

    public Map<String, String> getRequestHead() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Constant.User_Agent);
        if (getCookie() != null) {
            header.put("Cookie", getCookie());
        }
        return header;
    }

    public void reset() {
        setStartTime(null);
        setCookie(null);
        setCover(null);
        setAvatar(null);
        setStreams(null);
        setTitle(null);
        setNickname(null);
        setLiving(false);
    }
}
