package cn.zhangheng.common.util;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/03/25 星期二 10:27
 * @version: 1.0
 * @description: 快捷键操作
 */
public class ShortcutKeys {
    public static void main(String[] args) throws AWTException {
        Robot robot = new Robot();
//        startObsRecording(robot);
//        stopObsRecording(robot);
        wechatSendMsg(robot,"文件传输助手","123");

    }


    /**
     * 关闭程序
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
     * @param robot
     * @throws AWTException
     */
    public static void stopObsRecording(Robot robot) throws AWTException {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_2);
        robot.delay(200);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_2);
        robot.delay(200);
    }
}
