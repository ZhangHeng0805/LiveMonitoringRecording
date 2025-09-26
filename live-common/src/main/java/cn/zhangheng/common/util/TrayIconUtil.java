package cn.zhangheng.common.util;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.zhangheng.common.bean.Constant;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private TrayIcon trayIcon;
    @Getter
    private PopupMenu pop;//创建弹出式菜单
    @Getter
    private MenuItem startRecordMenu, stopRecordMenu, closeMenu, openWebMenu, playVideo, monitorMenu;
    @Setter
    @Getter
    private ClickListener clickListener;
    private final String title;
    private static final SystemTray systemTray;
    private final String threadKey;
    private static final int MAX_ICON_LIMIT = Constant.maxMonitorThreads; // 系统托盘最大图标数量（根据系统调整）
    // 全局注册表：key=线程唯一标识，value=托盘图标实例（线程安全）
    private static final Map<String, TrayIconUtil> iconRegistry = new ConcurrentHashMap<>();

    // 静态初始化系统托盘
    static {
        if (SystemTray.isSupported()) {
            systemTray = SystemTray.getSystemTray();
            log.info("系统支持托盘功能，最大图标限制: {}", MAX_ICON_LIMIT);
        } else {
            systemTray = null;
            log.warn("操作系统不支持系统托盘功能，所有托盘图标操作将被忽略");
        }
    }


    private TrayIconUtil(String title) {
        this.threadKey = Thread.currentThread().getName();
        this.title = title;
    }

    private TrayIconUtil(String threadKey, String title) {
        this.threadKey = threadKey;
        this.title = title;
    }


    public static TrayIconUtil getInstance(String title) {
        return getInstance(Thread.currentThread().getName(), title);
    }

    /**
     * 获取线程对应的托盘图标实例（核心方法：防止重复创建）
     *
     * @param threadKey 线程唯一标识（必须全局唯一）
     * @param title     图标标题
     * @return 托盘图标实例（已存在则返回现有实例，否则创建新实例）
     */
    public static TrayIconUtil getInstance(String threadKey, String title) {
        // 1. 检查注册表，若已存在则直接返回
        if (iconRegistry.containsKey(threadKey)) {
            TrayIconUtil existingIcon = iconRegistry.get(threadKey);
            log.debug("线程[{}]：托盘图标已存在，返回现有实例", threadKey);
            return existingIcon;
        }

        // 2. 若不存在，创建新实例并注册（双重检查锁确保线程安全）
        // 检查是否超过最大限制
        if (iconRegistry.size() >= MAX_ICON_LIMIT) {
            log.error("线程[{}]：托盘图标已达上限({})，无法创建", threadKey, MAX_ICON_LIMIT);
            return null;
        }
        // 创建新实例并加入注册表
        TrayIconUtil newIcon = new TrayIconUtil(threadKey, title);
        iconRegistry.put(threadKey, newIcon);
        // 初始化图标（在EDT线程中执行）
        newIcon.initTrayIconInEDT();
        log.info("线程[{}]：创建新托盘图标，当前总数: {}", threadKey, iconRegistry.size());

        return iconRegistry.get(threadKey);

    }

    private void initTrayIconInEDT() {
        if (systemTray != null) {
            // 检查是否超过最大图标限制
            if (iconRegistry.size() >= MAX_ICON_LIMIT) {
                log.error("线程[{}]：系统托盘图标已达上限({})，无法创建新图标", threadKey, MAX_ICON_LIMIT);
                return;
            }
            // 使用SwingUtilities确保在EDT线程中操作
            SwingUtilities.invokeLater(() -> {
                ClassPathResource classPathResource = new ClassPathResource("/logo.png");
                try (InputStream inputStreamImg = classPathResource.getStream()) {
                    Image iconImg = ImageIO.read(inputStreamImg);
                    this.pop = new PopupMenu();//创建弹出式菜单
                    addActionListener(pop);
                    trayIcon = new TrayIcon(iconImg, title, pop);
                    trayIcon.setImageAutoSize(true);
                    trayIcon.setToolTip(title);
                    trayIcon.addActionListener(e -> {
                        if (clickListener != null) {
                            log.debug("点击事件: 任务栏图标");
                            clickListener.iconClick(e);
                        }
                    });
                    // 4. 添加到系统托盘（同步操作避免并发冲突）
                    synchronized (systemTray) {
                        systemTray.add(trayIcon);
                        log.info("线程[{}]：托盘图标创建成功，当前图标总数: {}", threadKey, iconRegistry.size());
                    }
                } catch (IOException | AWTException e) {
                    log.error("系统状态栏通知创建失败：" + ThrowableUtil.getAllCauseMessage(e), e);
                }
            });
        } else {
            log.warn("操作系统不支持系统托盘功能");
        }
    }

    public void notifyMessage(String msg, TrayIcon.MessageType messageType) {
        if (trayIcon != null)
            trayIcon.displayMessage(title, msg, messageType);
    }

    public void notifyMessage(String msg) {
        notifyMessage(msg, TrayIcon.MessageType.INFO);
    }

    public void setToolTip(String title) {
        if (trayIcon != null)
            trayIcon.setToolTip(title);
    }

    public synchronized void shutdown() {
        // 1. 快速检查：若不在注册表中，直接返回（避免重复移除）
        if (!iconRegistry.containsKey(threadKey)) {
            log.debug("线程[{}]：图标不在注册表中，无需移除", threadKey);
            return;
        }
        // 2. 强制在EDT线程中执行移除操作（关键！）
        SwingUtilities.invokeLater(() -> {
            // 3. 双重校验：确保图标仍存在且系统托盘可用
            if (trayIcon == null) {
                log.debug("线程[{}]：图标已释放或未创建，跳过移除", threadKey);
                iconRegistry.remove(threadKey); // 同步清理注册表
                return;
            }
            // 4. 同步操作systemTray，避免并发冲突
            synchronized (systemTray) {
                try {
                    // 先从系统托盘移除图标（native操作）
                    systemTray.remove(trayIcon);
                    log.debug("线程[{}]：系统托盘已移除图标", threadKey);

                    // 再清理本地资源
                    trayIcon = null;

                    // 最后从注册表移除
                    iconRegistry.remove(threadKey);
                    log.info("线程[{}]：图标完全移除，当前总数: {}", threadKey, iconRegistry.size());

                } catch (Throwable e) {
                    // 捕获所有异常（包括native层可能抛出的Error）
                    log.error("线程[{}]：移除图标时发生异常", threadKey, e);
                    // 即使失败，也强制清理注册表，避免状态不一致
                    iconRegistry.remove(threadKey);
                }
            }

        });
    }

    private void addActionListener(PopupMenu pop) {
        startRecordMenu = new MenuItem("Start Recording");
        stopRecordMenu = new MenuItem("Stop Recording");
        closeMenu = new MenuItem("Exit");
        openWebMenu = new MenuItem("Open Web");
        playVideo = new MenuItem("Play Video");
        monitorMenu = new MenuItem("Open Monitor");
        monitorMenu.addActionListener(e -> {
            if (clickListener != null) {
                String url = clickListener.openMonitor(e);
                if (url != null) {
                    log.debug("点击事件: 打开网页>" + url);
                    openWebpage(url);
                }
            }
        });

        pop.add(closeMenu);
        pop.add(openWebMenu);
        pop.add(playVideo);
        pop.add(monitorMenu);
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

    public void setMenuVisible(MenuItem item, boolean menuVisible) {
        if (item != null) {
            if (menuVisible) {
                pop.add(item);
            } else {
                pop.remove(item);
            }
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

        default String openMonitor(ActionEvent e) {
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
