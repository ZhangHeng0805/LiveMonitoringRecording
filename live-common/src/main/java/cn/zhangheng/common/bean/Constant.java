package cn.zhangheng.common.bean;

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
    public final static String WeChatOfficialAccount = "https://mp.weixin.qq.com/s?__biz=MzIwMDQ2OTg4NA==&mid=2247484118&idx=1&sn=30dd3f7f2a4d93a6fdce4fb808e7c506&chksm=96fdfec5a18a77d35645d8e8f55477353aeb9a949fc73a2c302ca336155f8becae635e26f022#rd";
    //默认最小监听间隔延时（秒）
    public final static int delayIntervalSec = 10;
    //默认最大监听线程
    public final static int maxMonitorThreads = 10;
    //默认监听平台服务端口
    public final static int monitorServerPort=8005;


}
