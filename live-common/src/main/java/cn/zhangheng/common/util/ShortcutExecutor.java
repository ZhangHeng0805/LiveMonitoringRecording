package cn.zhangheng.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/23 星期三 18:13
 * @version: 1.0
 * @description: 快捷键执行器，能够解析快捷键字符串并执行相应操作
 */
public class ShortcutExecutor {
    // 键名到KeyEvent常量的映射
    private static final Map<String, Integer> KEY_MAP;
    private static final Logger log = LoggerFactory.getLogger(ShortcutExecutor.class);

    static {
        KEY_MAP = new HashMap<>();
        // 修饰键
        KEY_MAP.put("ctrl", KeyEvent.VK_CONTROL);
        KEY_MAP.put("alt", KeyEvent.VK_ALT);
        KEY_MAP.put("shift", KeyEvent.VK_SHIFT);
        KEY_MAP.put("meta", KeyEvent.VK_META); // Command键
        KEY_MAP.put("win", KeyEvent.VK_WINDOWS); // Windows键

        // 字母键
        for (char c = 'a'; c <= 'z'; c++) {
            KEY_MAP.put(String.valueOf(c), KeyEvent.getExtendedKeyCodeForChar(c));
        }

        // 数字键
        for (char c = '0'; c <= '9'; c++) {
            KEY_MAP.put(String.valueOf(c), KeyEvent.getExtendedKeyCodeForChar(c));
        }

        // 功能键
        KEY_MAP.put("f1", KeyEvent.VK_F1);
        KEY_MAP.put("f2", KeyEvent.VK_F2);
        KEY_MAP.put("f3", KeyEvent.VK_F3);
        KEY_MAP.put("f4", KeyEvent.VK_F4);
        KEY_MAP.put("f5", KeyEvent.VK_F5);
        KEY_MAP.put("f6", KeyEvent.VK_F6);
        KEY_MAP.put("f7", KeyEvent.VK_F7);
        KEY_MAP.put("f8", KeyEvent.VK_F8);
        KEY_MAP.put("f9", KeyEvent.VK_F9);
        KEY_MAP.put("f10", KeyEvent.VK_F10);
        KEY_MAP.put("f11", KeyEvent.VK_F11);
        KEY_MAP.put("f12", KeyEvent.VK_F12);

        // 其他常用键
        KEY_MAP.put("esc", KeyEvent.VK_ESCAPE);
        KEY_MAP.put("enter", KeyEvent.VK_ENTER);
        KEY_MAP.put("space", KeyEvent.VK_SPACE);
        KEY_MAP.put("backspace", KeyEvent.VK_BACK_SPACE);
        KEY_MAP.put("delete", KeyEvent.VK_DELETE);
        KEY_MAP.put("tab", KeyEvent.VK_TAB);
        KEY_MAP.put("up", KeyEvent.VK_UP);
        KEY_MAP.put("down", KeyEvent.VK_DOWN);
        KEY_MAP.put("left", KeyEvent.VK_LEFT);
        KEY_MAP.put("right", KeyEvent.VK_RIGHT);
    }

    private Robot robot;

    public ShortcutExecutor() {
        initRobot();
    }

    // 初始化 Robot 前检查环境
    public boolean initRobot() {
        // 检查是否为图形化环境
        if (GraphicsEnvironment.isHeadless()) {
            log.error("错误：当前为无图形界面环境，无法使用 Robot");
            return false;
        }

        // 尝试创建 Robot 实例
        try {
            robot = new Robot();
            // 设置全局延迟（避免操作过快）
            robot.setAutoDelay(50); // 每次操作后自动延迟 50ms
            return true;
        } catch (AWTException e) {
            log.error("创建 Robot 失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 执行指定的快捷键组合
     *
     * @param shortcut 快捷键字符串，例如 "ctrl+c"、"alt+shift+s"、"f5"
     * @throws IllegalArgumentException 如果快捷键格式不正确或包含未知键
     */
    public void executeShortcut(String shortcut) {
        if (robot == null) {
            return;
        }
        if (shortcut == null || shortcut.trim().isEmpty()) {
            throw new IllegalArgumentException("快捷键不能为空");
        }

        // 解析快捷键组合
        String[] keyParts = shortcut.toLowerCase().split("\\+");
        ArrayList<Integer> modifierKeys = new ArrayList<>();
        Integer mainKey = null;

        // 识别修饰键和主键
        for (String keyPart : keyParts) {
            String key = keyPart.trim();
            Integer keyCode = KEY_MAP.get(key);

            if (keyCode == null) {
                throw new IllegalArgumentException("未知的键: " + key);
            }

            // 判断是否为修饰键
            if (key.equals("ctrl")
                    || key.equals("alt")
                    || key.equals("shift")
                    || key.equals("meta")
                    || key.equals("win")
            ) {
                modifierKeys.add(keyCode);
            } else {
                // 主键只能有一个
                if (mainKey != null) {
                    throw new IllegalArgumentException("快捷键格式错误，只能有一个主键: " + shortcut);
                }
                mainKey = keyCode;
            }
        }

        // 必须有一个主键
        if (mainKey == null) {
            throw new IllegalArgumentException("快捷键必须包含一个主键: " + shortcut);
        }

        try {
            // 按下所有修饰键
            for (Integer modifierKey : modifierKeys) {
                robot.keyPress(modifierKey);
            }

            // 按下并释放主键
            robot.keyPress(mainKey);
            robot.keyRelease(mainKey);

        } finally {
            // 释放所有修饰键
            for (Integer modifierKey : modifierKeys) {
                robot.keyRelease(modifierKey);
            }
        }
    }
}
