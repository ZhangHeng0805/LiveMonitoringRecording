package cn.zhangheng.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.zhangheng.common.bean.Setting;
import com.zhangheng.util.ThrowableUtil;

import java.awt.*;
import java.util.HashMap;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/22 星期四 12:47
 * @version: 1.0
 * @description:
 */

public class NotificationUtil {

    private static final Log log = LogFactory.get();
    private final Setting setting;


    public NotificationUtil(Setting setting) {
        this.setting = setting;
    }

    /**
     * 息知消息API，需要配置url
     *
     * @param title
     * @param content
     */
    public void xiZhiSendMsg(String title, String content) {
        String xiZhiUrl = setting.getXiZhiUrl();
        if (StrUtil.isBlank(xiZhiUrl)) {
            return;
        }
        String[] urls = xiZhiUrl.split(",");
        for (String u : urls) {
            String url = StrUtil.isBlank(u) ? null : u.trim();
            if (StrUtil.isBlank(url)) {
                continue;
            }
            new Thread(() -> {
                HashMap<String, String> param = new HashMap<>();
                param.put("title", title);
                param.put("content", content);
                try {
                    HttpUtils.HttpResponse response = HttpUtils.postForm(url, param);
                    String body = response.getBody();
                    log.debug("息知API通知响应: {} - {}", url, body);
                    if (JSONUtil.isTypeJSON(body)) {
                        JSONObject object = JSONUtil.parseObj(body);
                        if (object.getInt("code", -1) != 200) {
                            log.warn("息知API消息发送异常：{} - {}", url, body);
                        }
                    } else {
                        log.error("息知API通知错误：{} - [{}] {}", url, response.getCode(), body);
                    }
                } catch (Exception e) {
                    log.error("息知API通知失败：{} - {}", url, ThrowableUtil.getAllCauseMessage(e));
                }
            }).start();
        }
    }


    /**
     * 动作模拟操作微信电脑客户端，需要配置通知的对象昵称
     * 需要确保微信操作快捷键
     * 打开微信：Ctrl+Alt+W
     * 搜索: Ctrl+F
     * 发送消息：Ctrl+Enter
     *
     * @param msg
     * @throws AWTException
     */
    public void weChatSendMsg(String msg) {
        String weChatTarget = setting.getWeChatTarget();
        if (StrUtil.isBlank(weChatTarget)) {
            return;
        }
        try {
            ShortcutKeys.wechatSendMsg(weChatTarget, msg);
        } catch (InterruptedException e) {
            log.error("微信客户端发送信息失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 直播开始处理
     */
    public void livingStartHandle() {
        try {
            if (StrUtil.isBlank(setting.getLivingStartShortcut())) return;
            ShortcutKeys.execute(setting.getLivingStartShortcut());
        } catch (Exception e) {
            log.error("直播开始处理失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 直播结束处理
     */
    public void livingEndHandle() {
        try {
            if (StrUtil.isBlank(setting.getLivingEndShortcut())) return;
            ShortcutKeys.execute(setting.getLivingEndShortcut());
        } catch (Exception e) {
            log.error("直播结束处理失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }


}
