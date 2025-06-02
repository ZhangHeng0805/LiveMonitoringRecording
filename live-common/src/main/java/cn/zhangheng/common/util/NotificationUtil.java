package cn.zhangheng.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.zhangheng.common.bean.Setting;
import com.zhangheng.util.SettingUtil;
import com.zhangheng.util.ThrowableUtil;

import java.awt.*;

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
        HttpResponse execute = HttpRequest.post(xiZhiUrl)
                .form("title", title)
                .form("content", content)
                .execute();
        String body = execute.body();
        execute.close();
        if (JSONUtil.isTypeJSON(body)) {
            JSONObject object = JSONUtil.parseObj(body);
            if (object.getInt("code", -1) != 200) {
                log.error("息知API消息发送异常：" + body);
            }
        } else {
            log.error("息知API消息发送失败：" + body);
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
            ShortcutKeys.wechatSendMsg(new Robot(), weChatTarget, msg);
        } catch (AWTException e) {
            log.error("微信客户端发送信息失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }

}
