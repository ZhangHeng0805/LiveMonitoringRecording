package cn.zhangheng.common.bean;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.enums.RecordType;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.setting.ConfigLoader;
import cn.zhangheng.common.setting.PropertiesConfig;
import cn.zhangheng.common.setting.PropertyValue;
import com.zhangheng.file.FileUtil;
import com.zhangheng.system.NetUtil;
import lombok.Data;
import lombok.ToString;
import lombok.Value;

import javax.annotation.Resource;
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
    private static volatile Setting setting;

    private Setting() {

    }
    private void loadConfig() {
        //自动加载配置
        ConfigLoader.load(this);
    }

    public static Setting getInstance() {
        return getInstance(false);
    }

    public static synchronized Setting getInstance(boolean isRefresh) {
        // 第一次创建实例
        if (setting == null) {
            setting = new Setting();
            setting.loadConfig();
        }
        // 复用对象重新加载配置（不新建实例，避免多对象）
        if (isRefresh) {
            setting.loadConfig();
        }
        return setting;
    }

    /**
     * 程序运行模式
     */
    @PropertyValue(value = "server.runMode", required = true)
    private RunMode runMode = RunMode.COMMAND;
    /**
     * 默认监听平台服务端口
     */
    @PropertyValue(value = "server.monitor.port", regex = "^(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{0,3})$", regexMessage = "端口号范围1-65535")
    private int monitorServerPort = 8005;

    /**
     * api接口会话有效期
     */
    @PropertyValue(value = "server.api.expireSec")
    private long apiExpireSec = Constant.apiExpireSec;
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
     * 更新BrowserContext的请求次数，请求次数达到时自动更换BrowserContext,最小为10，null或<=0时不更新
     */
    @PropertyValue("monitor.browser.updateContextCounts")
    private volatile Integer updateContextCounts;
    /**
     * 监听间隔延时（秒）
     */
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
