package cn.zhangheng.common.service;

import cn.zhangheng.common.bean.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:37
 * @version: 1.0
 * @description: 直播间刷新抽象类
 */
public abstract class RoomService<T extends Room> {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final T room;
    protected final AtomicInteger counter = new AtomicInteger(0);
    public int getCount(){
        return counter.get();
    }

    protected RoomService(T room) {
        this.room = room;
        //注意：子类调用super()方法后，需要调用refresh方法初始化room对象
    }

    public void getRoomData(boolean forceRefresh){
        if (refresh(forceRefresh)){
            room.setUpdateTime(new Date());
        }
        counter.incrementAndGet();
    }

    /**
     * 刷新直播间数据
     *
     * @param force 是否强制更新直播流信息
     */
    protected abstract boolean refresh(boolean force);

    /**
     * 开始直播弹幕功能
     */
    public abstract void startSubtitle();

    /**
     * 停止直播弹幕功能
     */
    public abstract void stopSubtitle();


}
