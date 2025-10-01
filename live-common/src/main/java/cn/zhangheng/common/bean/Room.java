package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(Room.class);
    protected String id;

    public void setId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IllegalArgumentException("id不能为空！");
        }
        this.id = id.trim();
    }

    //直播标题
    protected volatile String title;
    //用户名
    protected volatile String nickname;
    //是否在直播
    protected volatile boolean living;
    //开始时间
    protected Date startTime;
    //更新时间
    protected Date updateTime;
    //直播封面
    protected String cover;
    //用户头像
    protected String avatar;

    //直播流不同清晰度地址（由高到低排序）
    protected volatile Map<String, String> streams;


    private String cookie;
    //直播间地址
    protected String roomUrl;

    private volatile Setting setting;
    //平台
    protected Platform platform;

    public void setSetting(Setting setting) {
        if (setting != null) {
            this.setting = setting;
            initSetting(setting);
        }
    }

    public void setCookie(String cookie) {
        if (StrUtil.isNotBlank(cookie)) {
            this.cookie = cookie;
            log.debug("{}直播间[{}]设置Cookie：{}", getPlatform().name, id, cookie);
        }
    }

    public void setNickname(String nickname) {
        if (nickname != null && !"null".equals(nickname)) {
            this.nickname = nickname;
        }
    }

    public Room(String id) {
        this.id = id;
        roomUrl = initRoomUrl();
    }

    /**
     * 初始化配置，设置Cookie
     *
     * @param setting
     */
    public abstract void initSetting(Setting setting);


    /**
     * 获取直播流地址
     *
     * @return
     */
    public String getFlvUrl() {
        if (streams != null && !streams.isEmpty()) {
            return getStreams().entrySet().iterator().next().getValue();
        } else {
            return null;
        }
    }

    /**
     * 初始化直播间地址
     *
     * @return
     */
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

    /**
     * 获取直播间请求头，可以重新添加
     *
     * @return
     */
    public Map<String, String> getRequestHead() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Constant.User_Agent);
        if (getCookie() != null) {
            header.put("Cookie", getCookie());
        }
        return header;
    }

    /**
     * 重置直播间
     */
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
