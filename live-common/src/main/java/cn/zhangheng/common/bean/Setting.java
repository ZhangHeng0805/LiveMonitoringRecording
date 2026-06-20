package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.setting.ConfigLoader;
import cn.zhangheng.common.setting.PropertiesConfig;
import cn.zhangheng.common.setting.PropertyValue;
import com.zhangheng.file.FileUtil;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

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
    public Setting() {
        //自动加载配置
        ConfigLoader.load(this);
    }

    /**
     * 程序运行模式
     */
    @PropertyValue("server.runMode")
    private RunMode runMode = RunMode.COMMAND;
    /**
     * 默认监听平台服务端口
     */
    @PropertyValue("server.monitor.port")
    private int monitorServerPort = 8005;
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
     * 激活凭证文件的路径
     */
    @PropertyValue("activation.filePath")
    private String activateVoucherPath = Constant.ActivateVoucherFilePath;
    /**
     * 是否循环监听直播（直播结束后，重新监听）
     */
    @PropertyValue("record.isLoop")
    private boolean isLoop;
    /**
     * 是否开启弹幕记录
     */
    @PropertyValue("record.openSubtitle")
    private boolean openSubtitle;

    /**
     * 是否隐藏监听浏览器，默认隐藏
     */
    @PropertyValue("monitor.browser.headless")
    private volatile Boolean browserHeadless;

    /**
     * 浏览器是否每次打开页面时重置状态，null时默认次数清理
     */
    @PropertyValue("monitor.browser.isPageClear")
    private volatile Boolean browserIsPageClear;
    /**
     * 更新BrowserContext的请求次数，请求次数达到时自动更换BrowserContext,最小为10，<=0时不更新
     */
    @PropertyValue("monitor.browser.updateContextCounts")
    private volatile Integer updateContextCounts;
    /**
     * 监听间隔延时（秒）
     */
    @PropertyValue("monitor.delayIntervalSec")
    private int delayIntervalSec = Constant.minDelayIntervalSec;

    public void setDelayIntervalSec(int delayIntervalSec) {
        //不能小于系统默认值
        if (delayIntervalSec < Constant.minDelayIntervalSec) {
            this.delayIntervalSec = Constant.minDelayIntervalSec;
        } else {
            this.delayIntervalSec = delayIntervalSec;
        }
    }

    public int getDelayIntervalSec() {
        //不能小于系统默认值
        if (delayIntervalSec < Constant.minDelayIntervalSec) {
            delayIntervalSec = Constant.minDelayIntervalSec;
        }
        return delayIntervalSec;
    }

    /**
     * 最大监听线程数
     */
    @PropertyValue("monitor.maxMonitorThreads")
    private int maxMonitorThreads = Constant.maxMonitorThreads;

    public int getMaxMonitorThreads() {
        //不能超过系统默认最大值
        if (maxMonitorThreads > Constant.maxMonitorThreads) {
            maxMonitorThreads = Constant.maxMonitorThreads;
        }
        return maxMonitorThreads;
    }

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
    @ToString.Exclude
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
    @ToString.Exclude
    private String cookieDouYin;


    /**
     * 快手的Cookie
     */
    @PropertyValue("Cookie.KuaiShou")
    @ToString.Exclude
    private String cookieKuaiShou;

}
