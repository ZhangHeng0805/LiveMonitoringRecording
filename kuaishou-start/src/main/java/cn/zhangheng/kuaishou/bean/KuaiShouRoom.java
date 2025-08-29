package cn.zhangheng.kuaishou.bean;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import lombok.Getter;
import lombok.Setter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 18:59
 * @version: 1.0
 * @description:
 */
@Getter
@Setter
public class KuaiShouRoom extends Room {
    //粉丝数
    private String followers;
    //喜欢点赞数
    private String likeCount;

    public KuaiShouRoom(String id) {
        super(id);
    }

    @Override
    public void initSetting(Setting setting) {
        String cookie = setting.getCookieKuaiShou();
        if (StrUtil.isNotBlank(cookie))
            setCookie(cookie);
    }

    @Override
    public Platform getPlatform() {
        return Platform.KuaiShou;
    }

    @Override
    public String initRoomUrl() {
        return getPlatform().getMainUrl() + "u/" + getId();
    }
}
