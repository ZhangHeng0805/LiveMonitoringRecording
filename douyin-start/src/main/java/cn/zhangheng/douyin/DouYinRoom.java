package cn.zhangheng.douyin;


import cn.zhangheng.common.bean.Room;
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
    public void initSetting(SettingUtil settingUtil) {
        if (settingUtil != null)
            setCookie(settingUtil.getStr("DouYin.Cookie"));
    }

    public DouYinRoom(String id) {
        super(id);
    }


    @Override
    public String getCover() {
        return coverList.get(0);
    }

    @Override
    public Platform getPlatform() {
        return Platform.DouYin;
    }

}
