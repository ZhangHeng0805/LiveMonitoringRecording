package cn.zhangheng.record;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 18:45
 * @version: 1.0
 * @description:
 */

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import com.zhangheng.util.TimeUtil;
import okhttp3.WebSocket;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class DouyinLiveDecoder {

    private final MessageListener listener;

    public DouyinLiveDecoder(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * 解压抖音 gzip 数据包
     */
    private static byte[] decompressGzip(byte[] compressed) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            IOUtils.copy(gzipIn, baos);
            return baos.toByteArray();
        }
    }

    /**
     * 解析抖音直播 WebSocket 二进制消息
     *
     * @param websocketBinary 你从 wss 收到的原始 byte[]
     * @return 解析后的弹幕/礼物/消息对象
     */
    public void parse(WebSocket webSocket, byte[] websocketBinary) {
        try {
            DouyinMessageOuter.PushFrame pushFrame = DouyinMessageOuter.PushFrame.parseFrom(websocketBinary);
            byte[] bytes1 = decompressGzip(pushFrame.getPayload().toByteArray());
            DouyinMessageOuter.Response response = DouyinMessageOuter.Response.parseFrom(bytes1);
            if (response.getNeedAck()) {
                DouyinMessageOuter.PushFrame.Builder builder = DouyinMessageOuter.PushFrame.newBuilder();
                DouyinMessageOuter.PushFrame ack = builder.setLogId(pushFrame.getLogId())
                        .setPayloadType("ack")
                        .setPayload(response.getInternalExtBytes())
                        .build();
                webSocket.send(okio.ByteString.of(ack.toByteArray()));
            }
            for (DouyinMessageOuter.Message message : response.getMessagesListList()) {
                String method = message.getMethod();
                byte[] body = message.getPayload().toByteArray();
                switch (method) {
                    case "WebcastChatMessage":
//                        System.out.println("##### 聊天消息 #####");
                        chat(body);
                        break;
                    case "WebcastGiftMessage":
//                        System.out.println("##### 礼物消息 #####");
                        gift(body);
                        break;
                    case "WebcastLikeMessage":
//                        System.out.println("##### 点赞消息 #####");
                        like(body);
                        break;
                    case "WebcastMemberMessage":
//                        System.out.println("##### 进入直播间消息 #####");
                        member(body);
                        break;
                    case "WebcastSocialMessage":
//                        System.out.println("##### 关注消息 #####");
                        social(body);
                        break;
                    case "WebcastRoomUserSeqMessage":
//                        System.out.println("##### 直播间统计 #####");
                        stats(body);
                        break;
                    case "WebcastControlMessage":
//                        System.out.println("##### 直播间状态消息 #####");
                        control(body);
                        break;
                    case "WebcastRoomStatsMessage":
                        if (listener != null) {
//                            System.out.println("##### 直播间在线统计消息 #####");
                            DouyinMessageOuter.RoomStatsMessage msg = DouyinMessageOuter.RoomStatsMessage.parseFrom(body);
                            String x = TimeUtil.toTime(msg.getCommon().getCreateTime()) + " - " + msg.getDisplayLong() + " (" + msg.getTotal() + ")";
                            listener.online(x, msg);
                        }
                        break;
                    case "WebcastInRoomBannerMessage":
//                        DouyinMessageOuter.InRoomBannerMessage inRoomBannerMessage = DouyinMessageOuter.InRoomBannerMessage.parseFrom(body);
//                        System.out.println("WebcastInRoomBannerMessage=== " + JSONUtil.parseObj(inRoomBannerMessage.getJson()).toStringPretty());
                        break;
                    case "WebcastGiftSortMessage":
//                        DouyinMessageOuter.GiftSortMessage giftSortMessage = DouyinMessageOuter.GiftSortMessage.parseFrom(body);
//                        System.out.println("WebcastGiftSortMessage=== "+giftSortMessage);
                        break;
                    case "WebcastRanklistHourEntranceMessage":
//                        DouyinMessageOuter.RanklistHourEntranceMessage ranklistHourEntranceMessage = DouyinMessageOuter.RanklistHourEntranceMessage.parseFrom(body);
//                        System.out.println("WebcastRanklistHourEntranceMessage=== "+ranklistHourEntranceMessage);
                        break;
                    case "WebcastAudioChatMessage":
                        break;
                    case "WebcastRoomRankMessage":
//                        System.out.println("##### 直播间用户排名消息 #####");
                        roomRank(body);
                        break;
                    case "WebcastRoomStreamAdaptationMessage":
//                        DouyinMessageOuter.WebcastRoomStreamAdaptationMessage webcastRoomStreamAdaptationMessage = DouyinMessageOuter.WebcastRoomStreamAdaptationMessage.parseFrom(body);
//                        System.out.println("WebcastRoomStreamAdaptationMessage=== " + webcastRoomStreamAdaptationMessage.toString());
                        break;
                    default:
//                        System.out.println("===== " + method);
//                        System.out.println(new String(body, StandardCharsets.UTF_8));
                        break;
                }


            }

        } catch (InvalidProtocolBufferException e) {
            System.err.println("不是标准弹幕包（心跳/握手包）");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void roomRank(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.RoomRankMessage msg = DouyinMessageOuter.RoomRankMessage.parseFrom(body);
            List<String> usres = msg.getRanksListList().stream().map(roomRank -> getUserStr(roomRank.getUser())).collect(Collectors.toList());
            String x = TimeUtil.toTime(msg.getCommon().getCreateTime()) + " - 用户排名： " + String.join(" > ", usres);
//        System.out.println(x);
            listener.roomRank(x, msg);
        }
    }

    private void control(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.ControlMessage msg = DouyinMessageOuter.ControlMessage.parseFrom(body);
            String x = TimeUtil.getNowTime() + " - 直播间状态码: " + msg.getStatus();
            if (msg.getStatus() == 3) {
                x += ", 直播间已关闭!";
            }
//        System.out.println(x);
            listener.control(x, msg);
        }
    }


    private void stats(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.RoomUserSeqMessage msg = DouyinMessageOuter.RoomUserSeqMessage.parseFrom(body);
            String x = TimeUtil.getNowTime() + " - 在线人数: " + msg.getTotal() + " ,总人数: " + msg.getTotalUser() + " (" + msg.getTotalUserStr() + ")";
//        System.out.println(x);
            listener.stats(x, msg);
        }
    }

    private void social(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.SocialMessage msg = DouyinMessageOuter.SocialMessage.parseFrom(body);
            String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 关注了主播！";
            if (msg.getFollowCount() > 0) {
                x += "主播粉丝数: " + msg.getFollowCount();
            }
//        System.out.println(x);
            listener.social(x, msg);
        }
    }

    private void member(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.MemberMessage msg = DouyinMessageOuter.MemberMessage.parseFrom(body);
            String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 来了！当前人数: " + msg.getMemberCount();
//        System.out.println(x);
            listener.member(x, msg);
        }
    }

    public String getUserStr(DouyinMessageOuter.User user) {
        if (user.getShortId() > 0) {
            return "(" + user.getPayGrade().getLevel() + ")" + user.getNickName() + "[" + user.getShortId() + "]";
        } else {
            return user.getNickName();
        }
    }

    private void like(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.LikeMessage msg = DouyinMessageOuter.LikeMessage.parseFrom(body);
            String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 点赞×" + msg.getCount() + "个 - 总点赞数:" + msg.getTotal();
//        System.out.println(x);
            listener.like(x, msg);
        }
    }

    public void chat(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.ChatMessage msg = DouyinMessageOuter.ChatMessage.parseFrom(body);
            String x = TimeUtil.toTime(TimeUtil.unixToDate(msg.getEventTime() + "")) + " - " + getUserStr(msg.getUser()) + " : " + msg.getContent();
//        System.out.println(x);
            listener.chat(x, msg);
        }
    }

    public void gift(byte[] body) throws InvalidProtocolBufferException {
        if (listener != null) {
            DouyinMessageOuter.GiftMessage msg = DouyinMessageOuter.GiftMessage.parseFrom(body);
            String describe = msg.getCommon().getDescribe();
            String x = TimeUtil.toTime(msg.getSendTime()) + " - " + getUserStr(msg.getUser()) + " : " + describe.substring(describe.indexOf(":") + 1);
//        System.out.println(x);
            listener.gift(x, msg);
        }
    }
}
