package cn.zhangheng.common.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.zhangheng.common.activation.ActivationUtil;
import cn.zhangheng.common.activation.DeviceInfoCollector;
import cn.zhangheng.common.activation.ErrorException;
import cn.zhangheng.common.activation.WarnException;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.util.ObjectPropertyUpdater;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/27 星期二 10:30
 * @version: 1.0
 * @description: 应用启动核心
 */
public abstract class ApplicationMain<R extends Room> {
    private static final Log log = LogFactory.get();
//    protected Setting setting;
    @Getter
    protected R room;
    @Getter
    protected MonitorMain<R, ?> monitorMain;
    @Getter
    protected String deviceUniqueId;


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
                "*****   程序：" + Constant.Application + " " + getProjectVersion() + "\n" +
                "*****   默认配置文件：" + Constant.Setting_Name + "\n" +
                "*****   默认激活文件：" + Constant.ActivateVoucherFilePath + "\n" +
                "*****   作者：星曦向荣       邮箱：zhangheng_0805@163.com\n" +
                "*****   作者GitHub主页：https://github.com/ZhangHeng0805";
    }

    public void start(Setting setting, String[] args) {
        System.out.println(getBanner());
        try {
            deviceUniqueId = new DeviceInfoCollector().getDeviceUniqueId();
            ActivationUtil.verifyActivationCodeFile(deviceUniqueId, new Setting().getActivateVoucherPath());
        } catch (ErrorException errorException) {
            String message = ThrowableUtil.getAllCauseMessage(errorException);
            log.error(message, errorException);
            try {
                System.out.println("程序即将自动退出......");
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.exit(0);
        } catch (WarnException warnException) {
            String message = warnException.getMessage();
            log.warn(message);
        }
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
                    } catch (Exception e) {
                        continue;
                    }
                    int indexOf = new ArrayList<>(Arrays.asList(platforms)).indexOf(platform);
                    if (indexOf < 0) {
                        System.out.println("错误: " + platform.getName() + " 不支持该平台");
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

                R room = getRoom(platform, roomID);
                room.setSetting(setting);
                if (isRecord.equalsIgnoreCase("y")) {
                    listen(room, true);
                } else {
                    listen(room, false);
                }
                break;
            }
        } else {
            //参数[直播间ID 是否录屏 平台]
            String id = args[0];
            boolean isRecord = args.length >= 2 && Boolean.parseBoolean(args[1]);
            Room.Platform platform = null;
            if (args.length >= 3) {
                String pla = args[2];
                try {
                    platform = Room.Platform.valueOf(pla);
                } catch (Exception e) {
                    log.error(pla + " 直播平台错误！支持的平台有: " + platformsStr, e);
                    return;
                }
                int indexOf = new ArrayList<>(Arrays.asList(platforms)).indexOf(platform);
                if (indexOf < 0) {
                    log.error("错误: " + platform.getName() + " 不支持该平台");
                }
            }
            R room = getRoom(platform, id);
            room.setSetting(setting);
            listen(room, isRecord);
        }
    }

    public void start(Setting setting, String id, Room.Platform platform, boolean isRecord) {
        R room = getRoom(platform, id);
        room.setSetting(setting);
        listen(room, isRecord);
    }

    protected abstract MonitorMain<R, ?> getMonitorMain(R room);

    protected abstract R getRoom(Room.Platform platform, String id);

    protected abstract Room.Platform[] supportedPlatforms();

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
        boolean isLoop;
        //是否循环监听
        do {
            Setting setting=room.getSetting();
            if (setting == null) {
                try {
                    this.room = room;
                    setting = new Setting();
                    if (room.getSetting() != null) {
                        ObjectPropertyUpdater.updateDifferentProperties(room.getSetting(), setting);
                    }
                } catch (Exception e) {
                    log.warn("读取配置文件异常：" + ThrowableUtil.getAllCauseMessage(e));
                }
            }
            room.reset();//重置直播间
            room.setSetting(setting);
            monitorMain = getMonitorMain(room);
            monitorMain.start(room, isRecord);
            isLoop = !monitorMain.getIsForceStop() && room.getSetting().isLoop();
        } while (isLoop);
        log.info("{}直播间 {}[{}]监听结束！", room.getPlatform().getName(), room.getNickname(), room.getId());
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
