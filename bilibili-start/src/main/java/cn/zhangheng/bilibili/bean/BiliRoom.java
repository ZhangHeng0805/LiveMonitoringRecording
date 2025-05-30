package cn.zhangheng.bilibili.bean;

import cn.zhangheng.common.bean.Room;
import com.zhangheng.util.SettingUtil;
import lombok.Getter;
import lombok.Setter;

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
    public void initSetting(SettingUtil settingUtil) {
        if (settingUtil != null)
            setCookie(settingUtil.getStr("Bilibili.Cookie"));
    }

    public BiliRoom(String id) {
        super(id);
    }

    @Override
    public Platform getPlatform() {
        return Platform.Bili;
    }


}
