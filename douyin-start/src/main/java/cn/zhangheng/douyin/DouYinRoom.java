package cn.zhangheng.douyin;


import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import com.zhangheng.util.SettingUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

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

    @Override
    public void initSetting(Setting setting) {
        String cookieDouYin = setting.getCookieDouYin();
        if (StrUtil.isNotBlank(cookieDouYin))
            setCookie(cookieDouYin);
    }

    public DouYinRoom(String id) {
        super(id);
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

}
