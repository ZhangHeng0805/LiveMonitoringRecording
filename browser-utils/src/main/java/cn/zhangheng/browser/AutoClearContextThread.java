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
    private final int interval;
    private final MyThreadLocal<BrowserContent> threadLocal;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private Thread thread;

    public AutoClearContextThread(int interval, MyThreadLocal<BrowserContent> threadLocal) {
        this.interval = interval;
        this.threadLocal = threadLocal;
        run();
    }

    private void run() {
        thread = new Thread(() -> {
            try {
                log.info("自动清除空闲的BrowserContext线程启动！");
                while (isRunning.get()) {
                    try {
                        TimeUnit.SECONDS.sleep(interval);
                    } catch (InterruptedException ignored) {
                    }

                    ConcurrentHashMap<String, BrowserContent> all = threadLocal.getAll();
                    if (all != null && !all.isEmpty()) {
                        List<String> keys = all.entrySet().stream().filter(c -> {
                            long lastUsedTimestamp = c.getValue().getLastUsedTimestamp();
                            return System.currentTimeMillis() - lastUsedTimestamp > interval * 1000L;
                        }).map(Map.Entry::getKey).collect(Collectors.toList());

                        for (String key : keys) {
                            threadLocal.remove(key);
                            log.info("自动清除空闲的BrowserContext-[{}]被清除!", key);
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
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
