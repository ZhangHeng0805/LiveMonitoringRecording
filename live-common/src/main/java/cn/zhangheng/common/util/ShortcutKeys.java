package cn.zhangheng.common.util;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/03/25 星期二 10:27
 * @version: 1.0
 * @description: 快捷键操作
 */
public class ShortcutKeys {

    private static final Logger log = LoggerFactory.getLogger(ShortcutKeys.class);

    public static void main(String[] args) throws AWTException {
        Robot robot = new Robot();
//        startObsRecording(robot);
//        stopObsRecording(robot);
//        wechatSendMsg(robot,"文件传输助手","123");
        execute("ctrl+2");
    }


    /**
     * 关闭程序
     *
     * @param robot
     * @throws AWTException
     */
    public static void shutdownProgram(Robot robot) throws AWTException {
        //关闭程序 快捷键alt+F4
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F4);
        robot.delay(200);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_F4);
        robot.delay(200);
    }

    /**
     * 微信发消息
     *
     * @param robot
     * @param user
     * @param msg
     * @throws AWTException
     */
    public static void wechatSendMsg(Robot robot, String user, String msg) throws AWTException {
        //打开微信CTRL+ALT+W
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_W);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_W);
        robot.delay(200);
        //打开搜索CTRL+F
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_F);
        robot.delay(200);
        // 获取系统剪切板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 复制用户名到剪切板
        StringSelection selection1 = new StringSelection(user);
        clipboard.setContents(selection1, null);
        //粘贴
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_V);
        robot.delay(1000);
        //确认
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.delay(200);
        // 复制消息到剪切板
        StringSelection selection2 = new StringSelection(msg);
        clipboard.setContents(selection2, null);
        //粘贴
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_V);
        robot.delay(500);
        //发送信息
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_ENTER);
        robot.delay(200);
        //关闭微信
        shutdownProgram(robot);
    }

    /**
     * 打开obs软件
     *
     * @param robot
     * @throws AWTException
     */
    public static void openObs(Robot robot) throws AWTException {
        //快捷键ctrl+alt+O
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_O);
        robot.delay(200);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_O);
        robot.delay(200);
    }

    /**
     * 开始obs录屏
     *
     * @param robot
     * @throws AWTException
     */
    public static void startObsRecording(Robot robot) throws AWTException {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_1);
        robot.delay(200);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_1);
        robot.delay(200);
    }

    /**
     * 停止obs录屏
     *
     * @param robot
     */
    public static void stopObsRecording(Robot robot) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_2);
        robot.delay(200);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_2);
        robot.delay(200);
    }

    /**
     * 快捷键组合执行，多个组合之间逗号分割
     * 例：ctrl+c,ctrl+v
     *
     * @param shortcutKeys
     * @throws AWTException
     */
    public static void execute(String shortcutKeys) throws AWTException {
        if (StrUtil.isBlank(shortcutKeys)) return;
        log.info("执行快捷键：{}", shortcutKeys);
        ShortcutExecutor executor = new ShortcutExecutor();
        String[] keys = shortcutKeys.split(",");
        for (int i = 0; i < keys.length; i++) {
            executor.executeShortcut(keys[i]);
            if (i < keys.length - 1) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }

    }
}
