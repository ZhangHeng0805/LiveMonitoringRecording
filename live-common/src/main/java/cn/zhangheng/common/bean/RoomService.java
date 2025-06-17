package cn.zhangheng.common.bean;

import cn.hutool.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:37
 * @version: 1.0
 * @description:
 */
public abstract class RoomService<T extends Room> {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final T room;

    protected RoomService(T room) {
        this.room = room;
    }

    public void refresh() {
        refresh(false);
    }

    public abstract void refresh(boolean force);

    public HttpRequest get(String url) {
        return HttpRequest.get(url)
                .timeout(30_000)
                .header("User-Agent", Constant.User_Agent)
                .header("Referer", room.getPlatform().getMainUrl())
                ;
    }


}
