package cn.zhangheng.bilibili.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/25 星期日 22:26
 * @version: 1.0
 * @description:
 */
@Getter
@Setter
public class BiliRoom extends Room {
    private String room_id;
    private String uid;
    //观看人数
    private int viewers;
    //关注数
    private int followers;

    @Override
    public void initSetting(Setting setting) {
        String cookieBili = setting.getCookieBili();
        if (StrUtil.isNotBlank(cookieBili))
            setCookie(cookieBili);
    }

    public BiliRoom(String id) {
        super(id);
    }

    @Override
    public Platform getPlatform() {
        return Platform.Bili;
    }

    @Override
    public Map<String, String> getRequestHead() {
        Map<String, String> header = super.getRequestHead();
        header.put("Referer", getPlatform().getMainUrl() + getId());
        header.put("Origin", getPlatform().getMainUrl());
        return header;
    }
}
