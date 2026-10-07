package cn.zhangheng.record.service;

import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.Task;
import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
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
public abstract class RoomMonitor<R extends Room, S extends RoomService<R>> extends Task implements RoomMonitorMBean {
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
    private ObjectName mbeanObjectName;

    public RoomMonitor(R room) {
        this.room = room;
        roomService = getRoomService(room);
        state = State.NOT_LIVING;
    }

    protected abstract S getRoomService(R room);

    public synchronized void refresh(boolean force) {
        roomService.getRoomData(force);
    }

    @Override
    public void startSubtitle() {
        roomService.startSubtitle();
    }

    @Override
    public void stopSubtitle() {
        roomService.stopSubtitle();
    }

    @Override
    public int getRefreshCount() {
        return roomService.getRefreshCount();
    }

    @Override
    public String getRoomName() {
        return room.getNickname();
    }

    @Override
    public String getRoomUrl() {
        return room.getRoomUrl();
    }

    @Override
    public String getRoomPlatform() {
        return room.getPlatform().getName();
    }

    @Override
    public String getRefreshTime() {
        return TimeUtil.toTime(room.getUpdateTime());
    }


    @Override
    public boolean getIsLiving() {
        return room.isLiving();
    }

    @Override
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

    @Override
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
        String mbeanName = "cn.zhangheng.lmr:type=RoomMonitor,name=" + room.getPlatform() + "-" + room.getId();
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbeanObjectName = new ObjectName(mbeanName);
            if (!mbs.isRegistered(mbeanObjectName)) {
                mbs.registerMBean(this, mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX MBean注册失败[{}]", mbeanName, e);
        }
    }

    // JMX注销（销毁时调用）
    public void unregisterMBean() {
        if (mbeanObjectName == null) {
            return;
        }
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            if (mbs.isRegistered(mbeanObjectName)) {
                mbs.unregisterMBean(mbeanObjectName);
            }
        } catch (Exception e) {
            log.error("JMX注销异常[{}]", mbeanObjectName.getCanonicalName(), e);
        }
        // 置空，防止重复注销
        mbeanObjectName = null;
    }
}
