package cn.zhangheng.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
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

    public static void main(String[] args) throws AWTException, InterruptedException {
//        Robot robot = new Robot();
//        startObsRecording(robot);
//        stopObsRecording(robot);
        wechatSendMsg( "文件传输助手", "123");
//        execute("ctrl+2");
//        setClipboardString("你好");
//        execute("ctrl+v,ctrl+enter");
    }


    /**
     * 关闭程序
     *
     */
    public static void shutdownProgram() {
        //关闭程序 快捷键alt+F4
        execute("alt+f4");
    }

    /**
     * 微信发消息
     *
     * @param user
     * @param msg
     * @throws AWTException
     */
    public static void wechatSendMsg(String user, String msg) throws InterruptedException {
        //打开微信CTRL+ALT+W
        //打开搜索CTRL+F
        execute("ctrl+alt+w,ctrl+f");
        TimeUnit.MILLISECONDS.sleep(500);
        //复制到剪切板
        setClipboardString(user);
        //粘贴
        execute("ctrl+v");
        TimeUnit.MILLISECONDS.sleep(1000);
        //确认
        execute("enter");
        TimeUnit.MILLISECONDS.sleep(500);

        // 复制消息到剪切板
        setClipboardString(msg);
        //粘贴
        execute("ctrl+v");
        TimeUnit.MILLISECONDS.sleep(500);
        //发送信息ctrl+enter
        execute("ctrl+enter");
        TimeUnit.MILLISECONDS.sleep(500);
        //关闭微信
        shutdownProgram();
    }

    /**
     * 打开obs软件
     *
     * @param robot
     */
    public static void openObs(Robot robot) {
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
     */
    public static void startObsRecording(Robot robot) {
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

    private static final ShortcutExecutor executor = new ShortcutExecutor();

    /**
     * 快捷键组合执行，多个组合之间逗号分割
     * 例：ctrl+c,ctrl+v
     *
     * @param shortcutKeys
     */
    public static void execute(String shortcutKeys) {
        if (StrUtil.isBlank(shortcutKeys)) return;
        log.info("执行快捷键：{}", shortcutKeys);
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

    public static void setClipboardString(String data) {
        // 获取系统剪切板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 复制用户名到剪切板
        StringSelection selection1 = new StringSelection(data);
        clipboard.setContents(selection1, null);
    }
}
