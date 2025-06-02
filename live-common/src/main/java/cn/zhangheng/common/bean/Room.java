package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import com.zhangheng.util.SettingUtil;
import lombok.Data;
import lombok.Getter;

import java.util.Date;
import java.util.LinkedHashMap;

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
    protected String title;
    protected String owner;
    protected boolean living;
    //开始时间
    protected Date startTime;
//    protected Date updateTime;
    protected String cover;
    //直播流不同清晰度地址
    protected LinkedHashMap<String, String> streams;

    private String cookie;

    public void setCookie(String cookie) {
        if (StrUtil.isNotBlank(cookie))
            this.cookie = cookie;
    }

    public abstract void initSetting(Setting setting);

    public Room(String id) {
        this.id = id;
    }

    public abstract Platform getPlatform();

//    public String getFlvUrl() {
//        if (streams != null && !streams.isEmpty()) {
//            return getStreams().entrySet().iterator().next().getValue();
//        } else {
//            return null;
//        }
//    }

    public String getRoomUrl() {
        return getPlatform().getMainUrl() + getId();
    }

    @Getter
    public enum Platform {
        DouYin("抖音", "https://live.douyin.com/"),
        Bili("Bilibili", "https://live.bilibili.com/"),
        ;

        private final String name;
        private final String mainUrl;

        Platform(String name, String mainUrl) {
            this.name = name;
            this.mainUrl = mainUrl;
        }
    }

}
