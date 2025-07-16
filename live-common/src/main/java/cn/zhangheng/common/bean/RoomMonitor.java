package cn.zhangheng.common.bean;

import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.ThrowableUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:33
 * @version: 1.0
 * @description:
 */
public abstract class RoomMonitor<R extends Room, S extends RoomService<R>> extends Task {
    private final int delayIntervalSec;
    @Getter
    private State state;
    @Getter
    private final R room;
    private final S roomService;
    private int runCount = 0;
    @Setter
    private RoomListener<R> listener = null;

    public RoomMonitor(int delayIntervalSec, R room) {
        this.delayIntervalSec = delayIntervalSec;
        this.room = room;
        roomService = getRoomService(room);
        state = State.NOT_LIVING;
    }

    public RoomMonitor(R room) {
        this(10, room);
    }

    protected abstract S getRoomService(R room);

    public synchronized void refresh(boolean force) {
        roomService.refresh(force);
    }

    @Override
    public void run(boolean isAsync) throws ExecutionException {
        runCount++;
        if (runCount > 1) {
           refresh(false);
        }
        if (mainExecutors.isShutdown() || mainExecutors.isTerminated()) {
            mainExecutors = Executors.newFixedThreadPool(1);
        }
        Future<?> future = mainExecutors.submit(() -> {
            Thread.currentThread().setName(room.getNickname() + "-monitor-" + Thread.currentThread().getId());
            isRunning.set(true);
            if (!room.isLiving()) {
                state = State.NOT_LIVING;
                //未开播
                if (listener != null) {
                    listener.onChange(state, room);
                }
                do {
                    try {
                        TimeUnit.SECONDS.sleep(delayIntervalSec);
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
                        TimeUnit.SECONDS.sleep(delayIntervalSec);
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
    public void stop(boolean force) {
        isRunning.set(false);
        state = State.END;
        if (force) {
            mainExecutors.shutdownNow();
        } else {
            mainExecutors.shutdown();
        }
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
}
