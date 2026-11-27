package cn.zhangheng.record.bean;

import cn.zhangheng.record.activation.DeviceInfoCollector;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/24 星期六 17:55
 * @version: 1.0
 * @description:
 */
public class Constant {
    public static final String Application = "【星曦向荣】直播监听工具";
    public static final String User_Agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    public static final String Setting_Name = "xxxr.setting";
    public final static String FFmpegExePath = "bin/ffmpeg.exe";
    public final static String ActivateVoucherFilePath = "xxxr-activation.lic";
    //登录 Cookie 文件的统一后缀（登录脚本生成和默认读取都用它）
    public final static String CookieFileSuffix = ".cookie";
    //登录脚本生成的默认 Cookie 文件：配置里不写 Cookie.xxx 时自动使用（存在才生效）
    public final static String DefaultDouYinCookieFile = "cookie/DouYin" + CookieFileSuffix;
    public final static String DefaultBiliCookieFile = "cookie/Bili" + CookieFileSuffix;
    //首次使用试用天数（没有激活文件时生效，小于等于0表示不开放试用）
    public final static int TrialDays = 7;
    public final static String WeChatOfficialAccount = "https://mp.weixin.qq.com/s?__biz=MzIwMDQ2OTg4NA==&mid=2247484118&idx=1&sn=30dd3f7f2a4d93a6fdce4fb808e7c506&chksm=96fdfec5a18a77d35645d8e8f55477353aeb9a949fc73a2c302ca336155f8becae635e26f022#rd";
    //默认最小监听间隔延时（秒）
    public final static int minDelayIntervalSec = 20;
    //默认最大监听线程
    public final static int maxMonitorThreads = 10;
    //设备标识ID
    public final static String deviceUniqueId = new DeviceInfoCollector().getDeviceUniqueId();
    //api接口会话有效期（秘钥令牌）
    public final static long apiExpireSec = 2 * 60 * 60;
    //普通令牌有效期（公开签发，30 分钟足够只读浏览，过期自动换新）
    public final static long publicTokenExpireSec = 30 * 60;

}
