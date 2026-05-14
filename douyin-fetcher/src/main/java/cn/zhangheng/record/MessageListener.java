package cn.zhangheng.record;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/10 星期日 05:51
 * @version: 1.0
 * @description:
 */
public interface MessageListener {
    //聊天消息
    default void chat(String info, DouyinMessageOuter.ChatMessage msg) {
    }

    //礼物消息(需要用户登录Cookie，一个cookie账号只能进入一个直播间)
    default void gift(String info, DouyinMessageOuter.GiftMessage msg) {
    }

    //点赞消息
    default void like(String info, DouyinMessageOuter.LikeMessage msg) {
    }

    //进入直播间消息
    default void member(String info, DouyinMessageOuter.MemberMessage msg) {
    }

    //关注消息
    default void social(String info, DouyinMessageOuter.SocialMessage msg) {
    }

    //直播间统计 统计可能有延时
    default void stats(String info, DouyinMessageOuter.RoomUserSeqMessage msg) {
    }

    //直播间在线统计 实时统计人数更准确
    default void online(String info, DouyinMessageOuter.RoomStatsMessage msg) {
    }

    //直播间状态消息 msg.getStatus() == 3 直播间关闭
    default void control(String info, DouyinMessageOuter.ControlMessage msg) {
    }

    //直播间用户排名
    default void roomRank(String info, DouyinMessageOuter.RoomRankMessage msg) {
    }
}
