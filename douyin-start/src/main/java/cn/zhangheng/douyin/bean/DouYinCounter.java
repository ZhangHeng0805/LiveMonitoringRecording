package cn.zhangheng.douyin.bean;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/17 星期日 05:32
 * @version: 1.0
 * @description:
 */
@Data
public class DouYinCounter {
    //最大在线用户数
    private long maxOnlineUsers = 0;
    //总计礼物数（钻石）
    private long totalGift = 0;


    public void setMaxOnlineUsers(long maxOnlineUsers) {
        if (maxOnlineUsers > this.maxOnlineUsers) {
            this.maxOnlineUsers = maxOnlineUsers;
        }
    }

    public void setTotalGift(long totalGift) {
        this.totalGift += totalGift;
    }

    public void setTotalGift(String giftInfo) {
        if (giftInfo != null && giftInfo.endsWith("钻)")) {
            String sub = giftInfo.substring(giftInfo.lastIndexOf("(") + 1, giftInfo.lastIndexOf("钻"));
            setTotalGift(Long.parseLong(sub));
        }
    }

    @Override
    public String toString() {
        String s = "最高在线人数: " + maxOnlineUsers;
        if (totalGift > 0) s += ", 总计礼物数: " + totalGift + '钻';
        return s;
    }
}
