package cn.zhangheng.browser;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/03/30 星期一 19:40
 * @version: 1.0
 * @description:
 */
@Slf4j
public class AutoClearContextThread {
    private final int intervalSec;
    private final MyThreadLocal<BrowserContent> threadLocal;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private Thread thread;
    private final AutoClearContextThreadListener listener;

    public AutoClearContextThread(int intervalSec, MyThreadLocal<BrowserContent> threadLocal, AutoClearContextThreadListener listener) {
        this.intervalSec = intervalSec;
        this.threadLocal = threadLocal;
        this.listener = listener;
        run();
    }

    private void run() {
        thread = new Thread(() -> {
            try {
                log.info("自动清除空闲的BrowserContext线程启动！");
                if (listener != null) listener.threadStarted(intervalSec);
                while (isRunning.get()) {
                    try {
                        TimeUnit.SECONDS.sleep(intervalSec);
                    } catch (InterruptedException ignored) {
                    }
                    ConcurrentHashMap<String, BrowserContent> all = threadLocal.getAll();
                    if (listener != null) listener.triggerCheck(intervalSec, all);
                    if (all != null && !all.isEmpty()) {
                        List<String> keys = all.entrySet().stream().filter(c -> {
                            long lastUsedTimestamp = c.getValue().getLastUsedTimestamp();
                            return System.currentTimeMillis() - lastUsedTimestamp > intervalSec * 1000L;
                        }).map(Map.Entry::getKey).collect(Collectors.toList());

                        for (String key : keys) {
                            BrowserContent remove = threadLocal.remove(key);
                            log.info("自动清除空闲的BrowserContext-[{}]被清除!", key);
                            if (listener != null) listener.triggerClear(intervalSec, key, remove);
                        }
                    }

                }
            } finally {
                isRunning.set(false);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        isRunning.set(false);
        thread.interrupt();
        log.info("自动清除空闲的BrowserContext线程停止！");
        if (listener != null) listener.threadStop();
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
