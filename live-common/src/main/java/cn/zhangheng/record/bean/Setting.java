package cn.zhangheng.record.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.setting.PropertiesConfig;
import cn.zhangheng.common.setting.PropertyValue;
import cn.zhangheng.record.bean.enums.RecordType;
import cn.zhangheng.record.bean.enums.RunMode;

import com.zhangheng.file.FileUtil;
import lombok.Data;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 02:03
 * @version: 1.0
 * @description: 配置类
 */
@Data
@PropertiesConfig(path = Constant.Setting_Name)
public class Setting {

    private Setting() {
    }

    /**
     * 程序运行模式
     */
    @PropertyValue(value = "server.runMode", required = true)
    private RunMode runMode = RunMode.COMMAND;
    /**
     * 默认监听平台服务端口
     */
    @JmxAttribute(desc = "监听平台服务端口")
    @PropertyValue(value = "server.monitor.port", regex = "^(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{0,3})$", regexMessage = "端口号范围1-65535")
    private int monitorServerPort = 8005;

    /**
     * api接口会话有效期
     */
    @PropertyValue(value = "server.api.expireSec")
    private long apiExpireSec = Constant.apiExpireSec;

    /**
     * 是否允许公开获取普通令牌（action/token）
     * 关闭后普通接口也必须先用操作秘钥换秘钥令牌才能访问
     */
    @JmxAttribute(desc = "允许公开获取普通令牌")
    @PropertyValue("server.auth.publicTokenEnabled")
    private boolean publicTokenEnabled = true;

    /**
     * 普通令牌有效期（秒），默认 30 分钟
     * 普通令牌由公开接口签发，只覆盖只读接口，短有效期可降低泄露风险
     */
    @JmxAttribute(desc = "普通令牌有效期（秒）")
    @PropertyValue("server.auth.publicTokenExpireSec")
    private long publicTokenExpireSec = Constant.publicTokenExpireSec;

    /**
     * 普通令牌签发接口的限流阈值（每个来源 IP 每分钟允许次数，0 表示不限制）
     */
    @JmxAttribute(desc = "普通令牌签发每IP每分钟次数（0为不限）")
    @PropertyValue("server.api.publicTokenPerMinute")
    private int publicTokenPerMinute = 30;

    /**
     * 抖音作品解析接口的限流阈值（每个来源 IP 每分钟允许的调用次数）
     * 该接口对外公开且会占用本机浏览器解析资源，0 表示不限制
     */
    @JmxAttribute(desc = "作品解析每IP每分钟次数（0为不限）")
    @PropertyValue("server.api.parsePerMinute")
    private int parsePerMinute = 3;

    /**
     * 直播间链接解析接口（action/parseDouYinLink）的限流阈值
     * 每个来源 IP 每分钟允许调用次数，0 表示不限制
     */
    @JmxAttribute(desc = "直播间链接解析每IP每分钟次数（0为不限）")
    @PropertyValue("server.api.parseLinkPerMinute")
    private int parseLinkPerMinute = 10;
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
    @JmxAttribute(desc = "息知通知地址")
    private String xiZhiUrl;
    /**
     * 是否转换录制的视频
     */
    @JmxAttribute(desc = "是否转换录制的视频")
    @PropertyValue("record.FlvToMp4")
    private boolean convertFlvToMp4;
    /**
     * 录制类型：
     * 0-使用java编写的录制，
     * 1-使用ffmpeg工具录制
     */
    @PropertyValue(value = "record.type", required = true)
    private RecordType recordType;
    /**
     * ffmpeg工具的路径
     */
    @PropertyValue("record.ffmpegPath")
    private String ffmpegPath = Constant.FFmpegExePath;
    /**
     * 激活凭证文件的路径
     */
    @PropertyValue(value = "activation.filePath", required = true)
    private String activateVoucherPath = Constant.ActivateVoucherFilePath;
    /**
     * 是否循环监听直播（直播结束后，重新监听）
     */
    @JmxAttribute(desc = "是否循环监听直播（直播结束后，重新监听）")
    @PropertyValue("record.isLoop")
    private boolean isLoop;
    /**
     * 是否开启弹幕记录
     */
    @JmxAttribute(desc = "是否开启弹幕记录")
    @PropertyValue("record.openSubtitle")
    private boolean openSubtitle;

    /**
     * 是否隐藏监听浏览器，默认隐藏
     */
    @PropertyValue("monitor.browser.headless")
    private volatile Boolean browserHeadless;

    /**
     * 监听间隔延时（秒）
     */
    @JmxAttribute(desc = "监听间隔延时（秒）")
    @PropertyValue(value = "monitor.delayIntervalSec", regex = "^([1-9][0-9]+)$", regexMessage = "必须大于等于10")
    private int delayIntervalSec = Constant.minDelayIntervalSec;

    /**
     * 最大监听线程数
     */
    @PropertyValue(value = "monitor.maxMonitorThreads", regex = "^(100|[1-9][0-9]?)$", regexMessage = "必须大于1，小于等于100")
    private int maxMonitorThreads = Constant.maxMonitorThreads;

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
//    @ToString.Exclude
    private String cookieBili;


    public static String parseCookie(String cookie) {
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
//    @ToString.Exclude
    private String cookieDouYin;


    /**
     * 快手的Cookie
     */
    @PropertyValue("Cookie.KuaiShou")
//    @ToString.Exclude
    private String cookieKuaiShou;

}
