package cn.zhangheng.record;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/09 星期六 18:45
 * @version: 1.0
 * @description:
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.zhangheng.util.TimeUtil;
import okhttp3.WebSocket;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
//                System.out.println(method);
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
                    default:
                        break;
                }


            }


        } catch (InvalidProtocolBufferException e) {
            System.err.println("不是标准弹幕包（心跳/握手包）");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void control(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.ControlMessage msg = DouyinMessageOuter.ControlMessage.parseFrom(body);
        String x = TimeUtil.getNowTime() + " - 直播间状态： " + msg.getStatus();
//        System.out.println(x);
        if (listener != null) {
            listener.control(x, msg);
        }
    }


    private void stats(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.RoomUserSeqMessage msg = DouyinMessageOuter.RoomUserSeqMessage.parseFrom(body);
        String x = TimeUtil.getNowTime() + " - 在线人数: " + msg.getTotal() + " ,总人数: " + msg.getTotalUser() + " (" + msg.getTotalUserStr() + ")";
//        System.out.println(x);
        if (listener != null) {
            listener.stats(x, msg);
        }
    }

    private void social(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.SocialMessage msg = DouyinMessageOuter.SocialMessage.parseFrom(body);
        String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 关注了主播！主播粉丝数: " + msg.getFollowCount();
//        System.out.println(x);
        if (listener != null) {
            listener.social(x, msg);
        }
    }

    private void member(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.MemberMessage msg = DouyinMessageOuter.MemberMessage.parseFrom(body);
        String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 来了！当前人数: " + msg.getMemberCount();
//        System.out.println(x);
        if (listener != null) {
            listener.member(x, msg);
        }
    }

    public String getUserStr(DouyinMessageOuter.User user) {
        return "(" + user.getPayGrade().getLevel() + ")" + user.getNickName() + "[" + user.getShortId() + "]";
    }

    private void like(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.LikeMessage msg = DouyinMessageOuter.LikeMessage.parseFrom(body);
        String x = TimeUtil.getNowTime() + " - " + getUserStr(msg.getUser()) + " 点赞×" + msg.getCount() + "个 - 总点赞数:" + msg.getTotal();
//        System.out.println(x);
        if (listener != null) {
            listener.like(x, msg);
        }
    }

    public void chat(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.ChatMessage msg = DouyinMessageOuter.ChatMessage.parseFrom(body);
        String x = TimeUtil.toTime(TimeUtil.UnixToDate(msg.getEventTime() + "")) + " - " + getUserStr(msg.getUser()) + " : " + msg.getContent();
//        System.out.println(x);
        if (listener != null) {
            listener.chat(x, msg);
        }
    }

    public void gift(byte[] body) throws InvalidProtocolBufferException {
        DouyinMessageOuter.GiftMessage msg = DouyinMessageOuter.GiftMessage.parseFrom(body);
        String x = TimeUtil.toTime(TimeUtil.UnixToDate(msg.getSendTime() + "")) + " - " + getUserStr(msg.getUser()) + " 给 " + msg.getToUser().getNickName() + " 送出 " + msg.getInteractGiftInfo() + " × " + msg.getTotalCount() + "个";
//        System.out.println(x);
        if (listener != null) {
            listener.gift(x, msg);
        }
    }
}
