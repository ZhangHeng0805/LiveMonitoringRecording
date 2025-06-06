package cn.zhangheng.common.util;

import cn.hutool.core.io.resource.ClassPathResource;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 04:54
 * @version: 1.0
 * @description:
 */
public class TrayIconUtil {
    private static final Logger log = LoggerFactory.getLogger(TrayIconUtil.class);
    @Getter
    private final TrayIcon trayIcon;
    @Getter
    private final PopupMenu pop = new PopupMenu();//创建弹出式菜单
    @Getter
    private MenuItem startRecordMenu, stopRecordMenu, closeMenu, openWebMenu, playVideo;
    @Setter
    private ClickListener clickListener;
    private final String title;

    public TrayIconUtil(String title) {
        this.title = title;
        ClassPathResource classPathResource = new ClassPathResource("/logo.png");
        try (InputStream inputStreamImg = classPathResource.getStream()) {
            Image iconImg = ImageIO.read(inputStreamImg);
            trayIcon = new TrayIcon(iconImg, title, pop);
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip(title);
            trayIcon.addActionListener(e -> {
                if (clickListener != null) {
                    log.debug("点击事件: 任务栏图标");
                    clickListener.iconClick(e);
                }
            });
            SystemTray tray = SystemTray.getSystemTray();
            int length = tray.getTrayIcons().length;
            if (length == 0)
                tray.add(trayIcon);
        } catch (IOException | AWTException e) {
            throw new RuntimeException(e);
        }
        addActionListener();

    }

    public void notifyMessage(String msg) {
        trayIcon.displayMessage(title, msg, TrayIcon.MessageType.INFO);
    }

    public void setToolTip(String title) {
        trayIcon.setToolTip(title);
    }

    public void shutdown() {
        SystemTray.getSystemTray().remove(trayIcon);
    }

    private void addActionListener() {
        startRecordMenu = new MenuItem("Start Recording");
        stopRecordMenu = new MenuItem("Stop Recording");
        closeMenu = new MenuItem("Exit");
        openWebMenu = new MenuItem("Open Web");
        playVideo = new MenuItem("Play Video");

        pop.add(closeMenu);
        pop.add(openWebMenu);
        pop.add(playVideo);
        startRecordMenu.addActionListener(e -> {
            if (clickListener == null || clickListener.startRecordClick(e)) {
                log.debug("点击事件: 开始录制");
                setStartRecordStatue(true);
            }
        });
        stopRecordMenu.addActionListener(e -> {
            if (clickListener == null || clickListener.stopRecordClick(e)) {
                log.debug("点击事件: 停止录制");
                setStartRecordStatue(false);
            }
        });
        closeMenu.addActionListener(e -> {
            if (clickListener == null || clickListener.closeClick(e)) {
                log.debug("点击事件: 程序退出");
                System.exit(0);
            }
        });
        openWebMenu.addActionListener(e -> {
            if (clickListener != null) {
                String url = clickListener.openWebClick(e);
                if (url != null) {
                    log.debug("点击事件: 打开网页>" + url);
                    openWebpage(url);
                }
            }
        });
        playVideo.addActionListener(e -> {
            if (clickListener != null) {
                String url = clickListener.playVideo(e);
                if (url != null) {
                    log.debug("点击事件: 播放视频>" + url);
                    openWebpage(url);
                }
            }
        });
    }

    public void setStartRecordStatue(boolean startRecordStatue) {
        if (startRecordStatue) {
            pop.remove(startRecordMenu);
            pop.add(stopRecordMenu);
        } else {
            pop.remove(stopRecordMenu);
            pop.add(startRecordMenu);
        }
    }

    /**
     * 打开指定路径的目录
     *
     * @param directoryPath 目录路径
     */
    public static void openDirectory(String directoryPath) {
        File directory = new File(directoryPath);

        // 检查目录是否存在且为目录类型
        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("错误：指定路径不存在或不是一个目录 - " + directoryPath);
            return;
        }

        // 检查当前系统是否支持Desktop API
        if (!Desktop.isDesktopSupported()) {
            log.warn("错误：当前系统不支持桌面操作");
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        try {
            // 打开指定目录
            desktop.open(directory);
            log.debug("成功打开目录：" + directoryPath);
        } catch (IOException e) {
            log.error("错误：无法打开目录 - " + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    public static void openWebpage(String url) {
        try {
            // 获取操作系统名称
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();

            if (os.contains("win")) {
                // Windows系统使用rundll32命令调用浏览器
                rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (os.contains("mac")) {
                // macOS系统使用open命令
                rt.exec("open " + url);
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux/Unix系统尝试多种浏览器
                String[] browsers = {"xdg-open", "firefox", "chrome", "mozilla"};
                boolean opened = false;

                for (String browser : browsers) {
                    try {
                        rt.exec(new String[]{browser, url});
                        opened = true;
                        break;
                    } catch (IOException e) {
                        // 尝试下一个浏览器
                    }
                }

                if (!opened) {
                    log.error("openWebpage无法找到合适的浏览器来打开网页");
                }
            } else {
                log.error("openWebpage不支持的操作系统");
            }
        } catch (Exception e) {
            log.error("openWebpage打开网页时出错: " + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    public interface ClickListener {
        default boolean startRecordClick(ActionEvent e) {
            return true;
        }

        default boolean stopRecordClick(ActionEvent e) {
            return true;
        }

        default boolean closeClick(ActionEvent e) {
            return true;
        }

        default String openWebClick(ActionEvent e) {
            return null;
        }

        default String playVideo(ActionEvent e) {
            return null;
        }

        //任务栏图标点击事件
        void iconClick(ActionEvent e);
    }


    public static void main(String[] args) {
        TrayIconUtil iconUtil = new TrayIconUtil("测试");
        System.out.println(1);
        iconUtil.getPop().add(iconUtil.getStartRecordMenu());
        System.out.println(2);
        iconUtil.notifyMessage("这是测试通知");
        System.out.println(3);
        iconUtil.getPop().add(iconUtil.getStopRecordMenu());
        System.out.println(4);
        new Thread(() -> {
            iconUtil.notifyMessage("123");
            iconUtil.shutdown();
        }).start();
        System.out.println(5);
    }

}
