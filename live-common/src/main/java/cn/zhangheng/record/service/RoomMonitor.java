package cn.zhangheng.record.service;

import cn.zhangheng.common.jmx.JmxAttribute;
import cn.zhangheng.common.jmx.JmxOperation;
import cn.zhangheng.common.jmx.JmxRegisterUtil;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.Task;
import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;

import javax.management.ObjectName;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:33
 * @version: 1.0
 * @description: 直播监听
 */
public abstract class RoomMonitor<R extends Room, S extends RoomService<R>> extends Task {
    @Getter
    private State state;
    @Getter
    private final R room;
    private final S roomService;
    @Setter
    private RoomListener<R> listener = null;
    // 用于保存任务所在的工作线程
    private volatile Thread workerThread = null;
    // ========== MBean 注册 ==========
    private ObjectName mbeanObjectName, mbeanRoomName;

    public RoomMonitor(R room) {
        this.room = room;
        roomService = getRoomService(room);
        state = State.NOT_LIVING;
    }

    protected abstract S getRoomService(R room);

    public synchronized void refresh(boolean force) {
        roomService.getRoomData(force);
    }

    @JmxOperation(desc = "开始弹幕记录")
    public void startSubtitle() {
        roomService.startSubtitle();
    }

    @JmxOperation(desc = "停止弹幕记录")
    public void stopSubtitle() {
        roomService.stopSubtitle();
    }

    @JmxAttribute(name = "RefreshCount", desc = "刷新次数")
    public int getRefreshCount() {
        return roomService.getRefreshCount();
    }

    @JmxAttribute(name = "RoomName", desc = "直播间名称")
    public String getRoomName() {
        return room.getNickname();
    }

    @JmxAttribute(name = "RoomUrl", desc = "直播间地址")
    public String getRoomUrl() {
        return room.getRoomUrl();
    }

    @JmxAttribute(name = "RoomPlatform", desc = "直播间平台")
    public String getRoomPlatform() {
        return room.getPlatform().getName();
    }

    @JmxAttribute(name = "RefreshTime", desc = "直播间刷新时间")
    public String getRefreshTime() {
        return TimeUtil.toTime(room.getUpdateTime());
    }

    @JmxAttribute(name = "IsLiving", desc = "直播间是否开启直播")
    public boolean getIsLiving() {
        return room.isLiving();
    }

    @JmxAttribute(name = "IsOpenSubtitle", desc = "直播间是否开启弹幕记录")
    public boolean getIsOpenSubtitle() {
        return roomService.isOpenSubtitle();
    }

    @Override
    public void run(boolean isAsync) throws ExecutionException {
        if (mainExecutors.isShutdown() || mainExecutors.isTerminated()) {
            mainExecutors = Executors.newFixedThreadPool(1);
        }
        Future<?> future = mainExecutors.submit(() -> {
            Thread.currentThread().setName(room.getNickname() + "-monitor-" + room.getPlatform().name());
            workerThread = Thread.currentThread();
            isRunning.set(true);
            if (!room.isLiving()) {
                state = State.NOT_LIVING;
                //未开播
                if (listener != null) {
                    listener.onChange(state, room);
                }
                do {
                    try {
                        TimeUnit.SECONDS.sleep(room.getSetting().getDelayIntervalSec());
                    } catch (InterruptedException ignored) {
                        continue;
                    }
                    try {
                        refresh(false);
                    } catch (Exception e) {
                        log.error("直播监听刷新异常：{}", ThrowableUtil.getAllCauseMessage(e));
                    }
                } while (isRunning() && !room.isLiving());
            }
            if (room.isLiving()) {
                state = State.LIVING;
                //已开播
                if (listener != null) {
                    listener.onChange(state, room);
                }
                do {
                    try {
                        TimeUnit.SECONDS.sleep(room.getSetting().getDelayIntervalSec());
                    } catch (InterruptedException ignored) {
                        continue;
                    }
                    try {
                        refresh(RandomUtil.randomBoolean());
                    } catch (Exception e) {
                        log.error("直播监听刷新异常：{}", ThrowableUtil.getAllCauseMessage(e));
                    }
//                    if (RandomUtil.randomBoolean()) {
//                        //模拟下播
//                        room.setLiving(false);
//                    }
                    if (room.isLiving() && listener != null) {
                        listener.onProgress(room);
                    }
                } while (isRunning() && room.isLiving());
            }
            //直播结束，结束监听
            state = State.END;
            isRunning.set(false);
            if (listener != null) {
                listener.onStop();
            }
        });
        //开始监听
        if (listener != null) {
            listener.onStart();
        }
        if (!isAsync) {
            try {
                future.get();
            } catch (InterruptedException e) {
                log.error("直播间监听主任务中断：" + ThrowableUtil.getAllCauseMessage(e));
            } finally {
                isRunning.set(false);
            }
        }
        mainExecutors.shutdown();
    }

    @JmxOperation(desc = "直播间立即刷新")
    public void nowRefresh() {
        refresh(true);
        workerThread.interrupt();
    }

    @Override
    public void stop(boolean force) {
        state = State.END;
        super.stop(force);
    }


    public enum State {
        NOT_LIVING,
        LIVING,
        END,
        ;
    }

    public interface RoomListener<R extends Room> {
        //开始监听
        void onStart();

        //监听结束
        void onStop();

        //直播间状态改变（未开播/已开播）
        void onChange(State state, R room);

        //开播时的直播间信息（根据delayIntervalSec设置的频率回调）
        void onProgress(R room);
    }

    // JMX MBean 注册
    public void registerMBean() {
        String name = room.getPlatform().getName() + "-" + room.getNickname();
        try {
            mbeanRoomName = JmxRegisterUtil.register(room, "Room", "name", name);
            mbeanObjectName =JmxRegisterUtil.register(this, "RoomMonitor","name", name);
        } catch (Exception e) {
            log.error("JMX MBean注册失败[{}]", name, e);
        }
    }

    // JMX注销（销毁时调用）
    public void unregisterMBean() {
        try {
            JmxRegisterUtil.unregister(mbeanObjectName);
            JmxRegisterUtil.unregister(mbeanRoomName);
        } catch (Exception e) {
            log.error("JMX注销异常[{}]", mbeanObjectName.getCanonicalName(), e);
        }
        // 置空，防止重复注销
        mbeanObjectName = null;
        mbeanRoomName = null;
    }
}
