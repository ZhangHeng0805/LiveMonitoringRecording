package cn.zhangheng.common.bean;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.zhangheng.util.ThrowableUtil;

import java.net.URL;
import java.util.Objects;
import java.util.Scanner;
import java.util.jar.Manifest;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/27 星期二 10:30
 * @version: 1.0
 * @description:
 */
public abstract class ApplicationMain<R extends Room> {
    private static final Log log = LogFactory.get();
    protected Setting setting;

    private String getBanner() {
        return "\n" +
                "            ***************       ***       ***\n" +
                "            **************        ***       ***\n" +
                "                    ***           ***       ***\n" +
                "                  ***             *************\n" +
                "                ***               *************\n" +
                "              ***                 ***       ***\n" +
                "            ***************       ***       ***\n" +
                "           ****************       ***       ***\n" +
                "\n" +
                "*****   程序：" + Constant.Application + "\n" +
                "*****   配置文件：" + Constant.Setting_Name + "\n" +
                "*****   作者：星曦向荣   版本：V" + getProjectVersion() + "       邮箱：zhangheng_0805@163.com\n" +
                "*****   作者GitHub主页：https://github.com/ZhangHeng0805";
    }

    public void start(Setting setting, String[] args) {
        this.setting = setting;
        System.out.println(getBanner());
        Room.Platform[] platforms = supportedPlatforms();
        String platformsStr = supportedPlatformsStr(platforms);
        System.out.println(Constant.Application + " - " + platformsStr);
        // 检查是否有参数传递
        if (args.length == 0) {
            Scanner scanner = new Scanner(System.in);
            // 提示用户输入
            while (true) {
                Room.Platform platform = null;
                if (platforms.length > 1) {
                    System.out.print("请输入要监听的直播间平台(" + platformsStr + "): ");
                    String p = scanner.nextLine();
                    // 读取一行文本
                    if (StrUtil.isBlank(p)) {
                        continue;
                    }
                    try {
                        platform = Room.Platform.valueOf(p);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }

                System.out.print("请输入要监听的直播间ID: ");
                String roomID = scanner.nextLine(); // 读取一行文本
                if (StrUtil.isBlank(roomID)) {
                    continue;
                }
                System.out.print("是否录制需要监听的抖音直播间视频？(y:是/n:否): ");
                String isRecord = scanner.nextLine(); // 读取一行文本

                if (isRecord.equalsIgnoreCase("y")) {
                    listen(getRoom(platform, roomID), true);
                } else {
                    listen(getRoom(platform, roomID), false);
                }
                break;
            }
        } else {
            //参数[直播间ID 是否录屏 平台]
            String id = args[0];
            boolean isRecord = args.length >= 2 && Boolean.parseBoolean(args[1]);
            Room.Platform platform = null;
            if (args.length >= 3) {
                try {
                    platform = Room.Platform.valueOf(args[2]);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("直播平台错误！" + platformsStr);
                }
            }
            listen(getRoom(platform, id), isRecord);
        }
    }

    protected abstract MonitorMain<R, ?> getMonitorMain(Setting settingUtil, R room);

    protected abstract R getRoom(Room.Platform platform, String id);

    protected abstract Room.Platform[] supportedPlatforms();

//    private String supportedPlatformsStr() {
//        return supportedPlatformsStr(supportedPlatforms());
//    }

    private String supportedPlatformsStr(Room.Platform[] platforms) {
        StringBuilder notice = new StringBuilder();
        for (int i = 0; i < platforms.length; i++) {
            if (i == platforms.length - 1) {
                notice.append(platforms[i].name()).append(":").append(platforms[i].getName());
            } else {
                notice.append(platforms[i].name()).append(":").append(platforms[i].getName()).append(" / ");
            }
        }
        return notice.toString();
    }

    private void listen(R room, boolean isRecord) {
        //是否循环监听
        boolean isLoop;
        do {
            if (setting == null) {
                try {
                    setting = new Setting();
                } catch (Exception e) {
                    log.warn("读取配置文件异常：" + ThrowableUtil.getAllCauseMessage(e));
                }
            }
            isLoop = setting.isLoop();
            room.reset();//重置直播间
            getMonitorMain(setting, room).start(room, isRecord);
        } while (isLoop);
    }

    public String getProjectVersion() {
        // 通过当前类的类加载器获取 MANIFEST.MF
        try {
            // 获取当前类所在的 JAR 包路径
            String className = getClass().getName().replace('.', '/') + ".class";
            String classPath = Objects.requireNonNull(getClass().getClassLoader().getResource(className)).toString();

            // 仅在 JAR 包中有效（shade 打包后是一个独立 JAR）
            if (classPath.startsWith("jar:")) {
                // 截取 MANIFEST.MF 的路径
                String manifestPath = classPath.substring(0, classPath.lastIndexOf('!') + 1) + "/META-INF/MANIFEST.MF";
                Manifest manifest = new Manifest(new URL(manifestPath).openStream());

                // 读取自定义的 Project-Version 属性
                String version = manifest.getMainAttributes().getValue("Project-Version");
                if (version == null) {
                    // 备选：读取标准的 Implementation-Version
                    version = manifest.getMainAttributes().getValue("Implementation-Version");
                }
                return version != null ? version : " ";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return " ";
    }

}
