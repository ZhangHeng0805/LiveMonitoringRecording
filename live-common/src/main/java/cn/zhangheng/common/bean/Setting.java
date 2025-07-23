package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.setting.ConfigLoader;
import cn.zhangheng.common.setting.PropertiesConfig;
import cn.zhangheng.common.setting.PropertyValue;
import com.zhangheng.file.FileUtil;
import lombok.Data;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 02:03
 * @version: 1.0
 * @description:
 */
@Data
@PropertiesConfig(path = Constant.Setting_Name)
public class Setting {
    public Setting() {
        //自动加载配置
        ConfigLoader.load(this);
    }

    /**
     * FLV播放器服务-端口号
     */
    @PropertyValue("server.flvPlayer.port")
    private int flvPlayerPort = 8000;
    /**
     * 微信客户端通用对象
     */
    @PropertyValue("notice.weChat.target")
    private String weChatTarget;
    /**
     * 息知通知地址
     * 详情查看：https://xz.qqoq.net/
     */
    @PropertyValue("notice.xiZhi.url")
    private String xiZhiUrl;
    /**
     * 是否转换录制的视频
     */
    @PropertyValue("record.FlvToMp4")
    private boolean convertFlvToMp4;
    /**
     * 录制类型：
     * 0-使用java编写的录制，
     * 1-使用ffmpeg工具录制
     */
    @PropertyValue("record.type")
    private int recordType = 0;
    /**
     * ffmpeg工具的路径
     */
    @PropertyValue("record.ffmpegPath")
    private String ffmpegPath = Constant.FFmpegExePath;
    /**
     * 是否循环监听直播（直播结束后，重新监听）
     */
    @PropertyValue("record.isLoop")
    private boolean isLoop;
    /**
     * 监听间隔延时（秒）
     */
    @PropertyValue("monitor.delayIntervalSec")
    private int delayIntervalSec = 10;
    /**
     * 直播开始时触发的快捷键
     */
    @PropertyValue("living.start.shortcut")
    private String livingStartShortcut;
    /**
     * 直播结束时触发的快捷键
     */
    @PropertyValue("living.end.shortcut")
    private String livingEndShortcut;
    /**
     * B站的Cookie
     */
    @PropertyValue("Cookie.Bilibili")
    private String cookieBili;

    public String getCookieBili() {
        return setCookie(cookieBili);
    }

    private String setCookie(String cookie) {
        if (StrUtil.isNotBlank(cookie)) {
            if (cookie.startsWith("file:")) {
                cookie = FileUtil.readString(new File(cookie.substring(5).trim()), StandardCharsets.UTF_8).trim();
            }
            return cookie;
        }
        return null;
    }


    /**
     * 抖音的Cookie
     */
    @PropertyValue("Cookie.DouYin")
    private String cookieDouYin;

    public String getCookieDouYin() {
        return setCookie(cookieDouYin);
    }
}
