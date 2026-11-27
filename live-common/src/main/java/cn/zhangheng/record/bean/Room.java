package cn.zhangheng.record.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.record.bean.enums.RecordQuality;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 00:40
 * @version: 1.0
 * @description: 直播间抽象类
 */
@Data
@ToString(exclude = {"cookie"})
public abstract class Room {
    private static final Logger log = LoggerFactory.getLogger(Room.class);
    protected String id;

    public void setId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IllegalArgumentException("id不能为空！");
        }
        this.id = id.trim();
    }

    @JmxAttribute(desc = "直播标题")
    //直播标题
    protected String title;

    @JmxAttribute(desc = "用户名")
    //用户名
    protected String nickname;

    @JmxAttribute(desc = "是否在直播")
    //是否在直播
    protected volatile boolean living;

    public void setLiving(boolean living) {
        this.living = living;
        if (living && startTime == null) {
            startTime = new Date();
        }
    }

    @JmxAttribute(desc = "开始时间")
    //开始时间
    protected Date startTime;
    @JmxAttribute(desc = "更新时间")
    //更新时间
    protected Date updateTime;
    //直播封面url
    protected String cover;
    //用户头像url
    protected String avatar;

    @JmxAttribute(desc = "直播流地址")
    //直播流不同清晰度地址（由高到低排序）
    protected Map<String, String> streams;

    @JmxAttribute(desc = "直播间cookie")
    private String cookie;
    @JmxAttribute(desc = "直播间的地址")
    //直播间的地址
    protected String roomUrl;

    @JmxAttribute(desc = "设置")
    private volatile Setting setting;

    @JmxAttribute(desc = "直播平台")
    //平台
    protected Platform platform;

    public void setSetting(Setting setting) {
        this.setting = setting;
        if (setting != null) {
            initSetting(setting);
        }
    }

    public void setCookie(String cookie) {
        if (StrUtil.isNotBlank(cookie)) {
            this.cookie = cookie;
            log.debug("{}直播间[{}]设置Cookie", getPlatform().name, id);
        } else {
            this.cookie = null;
        }
    }

    public void setNickname(String nickname) {
        if (nickname != null && !"null".equals(nickname)) {
            this.nickname = nickname;
        } else {
            this.nickname = null;
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
     * 获取直播流地址：按直播间设置的录制画质在已排序的清晰度档位里挑一个
     * （streams 由各平台按画质从高到低写入）
     *
     * @return
     */
    public String getFlvUrl() {
        Map.Entry<String, String> stream = getSelectedStream();
        return stream == null ? null : stream.getValue();
    }

    /**
     * 按录制画质选出要用的那一路流（键=清晰度名称，值=流地址），没有可用流时返回 null
     */
    public Map.Entry<String, String> getSelectedStream() {
        Map<String, String> streams = getStreams();
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(streams.entrySet());
        return entries.get(qualityIndex(entries.size(), getRecordQuality()));
    }

    /**
     * 预览播放、开播通知等场景固定用最高画质那一档，不受录制画质设置影响
     */
    public Map.Entry<String, String> getHighestStream() {
        Map<String, String> streams = getStreams();
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        return streams.entrySet().iterator().next();
    }

    /**
     * 最高画质的流地址（预览/通知用）
     */
    public String getHighestFlvUrl() {
        Map.Entry<String, String> stream = getHighestStream();
        return stream == null ? null : stream.getValue();
    }

    /**
     * 录制画质对应的档位下标：最高取第一档，最低取最后一档，中等取中间档（偶数档时偏下）
     */
    public static int qualityIndex(int size, RecordQuality quality) {
        return (quality == null ? RecordQuality.HIGH : quality).indexOf(size);
    }

    /**
     * 直播间当前设置的录制画质，没配置时按最高画质
     */
    public RecordQuality getRecordQuality() {
        Setting setting = getSetting();
        RecordQuality quality = setting == null ? null : setting.getRecordQuality();
        return quality == null ? RecordQuality.HIGH : quality;
    }

    /**
     * 初始化直播间的地址
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

    public String getRoomKey() {
        return platform.name() + "-" + id;
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
