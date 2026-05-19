package cn.zhangheng.record;

import okhttp3.Response;
import okhttp3.WebSocket;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/17 星期日 06:48
 * @version: 1.0
 * @description:
 */
public interface SocketListener {
    default void onOpen(WebSocket ws, Response response) {}
    default void onClosed(WebSocket webSocket, int code, String reason) {}
    default void onClosing(WebSocket webSocket, int code, String reason) {}
    default void onFailure(WebSocket webSocket, Throwable t, Response response) {}
    default void onMessage(WebSocket webSocket, okio.ByteString bytes) {}
}
