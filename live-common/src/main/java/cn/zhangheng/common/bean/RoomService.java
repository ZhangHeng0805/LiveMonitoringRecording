package cn.zhangheng.common.bean;

import cn.hutool.http.Header;
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
        //注意：子类调用super()方法后，需要调用refresh方法初始化room对象
    }

    public void refresh() {
        refresh(false);
    }

    /**
     * 刷新直播间数据
     *
     * @param force 是否强制更新直播流信息
     */
    public abstract void refresh(boolean force);

    public HttpRequest get(String url) {
        return HttpRequest.get(url)
                .timeout(30_000)
                .header(Header.USER_AGENT, Constant.User_Agent)
                ;
    }


}
