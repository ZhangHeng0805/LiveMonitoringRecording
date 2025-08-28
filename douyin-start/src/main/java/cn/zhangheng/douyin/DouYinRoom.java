package cn.zhangheng.douyin;


import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import com.zhangheng.util.SettingUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/11 星期日 02:23
 * @version: 1.0
 * @description: 直播间
 */
@Getter
@Setter
@ToString
public class DouYinRoom extends Room {
    //总观看人数
    private String totalUserStr;
    //当前在线人数
    private String userCountStr;
    //喜欢点赞数
    private int likeCount;
    //直播间封面
    private List<String> coverList;


    public DouYinRoom(String id) {
        super(id);
    }

    @Override
    public void initSetting(Setting setting) {
        String cookie = setting.getCookieDouYin();
        if (StrUtil.isNotBlank(cookie))
            setCookie(cookie);
    }


    @Override
    public String getCover() {
        if (coverList != null && !coverList.isEmpty()) {
            return coverList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public Platform getPlatform() {
        return Platform.DouYin;
    }

    @Override
    public String initRoomUrl() {
        return getPlatform().getMainUrl() + getId();
    }

    @Override
    public Map<String, String> getRequestHead() {
        Map<String, String> header = super.getRequestHead();
        header.put("Referer", getPlatform().getMainUrl() + getId());
        header.put("Origin", getPlatform().getMainUrl());
        return header;
    }
}
