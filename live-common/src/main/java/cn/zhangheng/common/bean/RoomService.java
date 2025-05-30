package cn.zhangheng.common.bean;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:37
 * @version: 1.0
 * @description:
 */
public abstract class RoomService<T extends Room> {
    protected final T room;

    protected RoomService(T room) {
        this.room = room;
    }

    public void refresh() {
        refresh(false);
    }
    public abstract void refresh(boolean force);
}
