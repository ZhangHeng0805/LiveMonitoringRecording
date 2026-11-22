package cn.zhangheng.record.httpServer.router;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * 固定窗口限流器（按来源标识计数），注解式路由内部使用。
 * <p>
 * 与业务无关，仅做「窗口内次数」判断，超出返回建议等待的秒数。
 *
 * @author: ZhangHeng
 */
public class FixedWindowRateLimiter {

    /**
     * 来源标识 -> [窗口起始毫秒, 窗口内已用次数]
     */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();
    private final long windowMs;
    /** 阈值来源：固定值或实时读配置 */
    private final IntSupplier limitSupplier;
    private final AtomicLong lastEvictTime = new AtomicLong(0);

    public FixedWindowRateLimiter(int limit, long windowMs) {
        this(() -> limit, windowMs);
    }

    /**
     * @param limitSupplier 阈值供应器，每次申请配额时取值（用于读配置，支持运行期改配置）
     * @param windowMs      窗口长度
     */
    public FixedWindowRateLimiter(IntSupplier limitSupplier, long windowMs) {
        this.limitSupplier = limitSupplier;
        this.windowMs = windowMs <= 0 ? 60_000L : windowMs;
    }

    /**
     * 申请一次配额
     *
     * @param id 来源标识（一般是 IP，可加路由前缀区分）
     * @return 0 表示放行；&gt;0 表示被限流，值为建议重试的秒数
     */
    public long tryAcquire(String id) {
        int limit = currentLimit();
        if (limit <= 0) {
            return 0;
        }
        if (id == null || id.isEmpty()) {
            id = "unknown";
        }
        long now = System.currentTimeMillis();
        long[] slot = windows.computeIfAbsent(id, k -> new long[]{now, 0L});
        synchronized (slot) {
            if (now - slot[0] >= windowMs) {
                slot[0] = now;
                slot[1] = 0;
            }
            if (slot[1] >= limit) {
                long waitMs = windowMs - (now - slot[0]);
                return Math.max(1L, (waitMs + 999) / 1000);
            }
            slot[1]++;
            return 0;
        }
    }

    /**
     * 当前生效阈值（读配置的实现每次都会重新取值）
     */
    private int currentLimit() {
        try {
            return limitSupplier.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 定期清理过期窗口，避免 Map 无限增长
     */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        long last = lastEvictTime.get();
        if (windows.size() < 200 || now - last < windowMs) {
            return;
        }
        if (!lastEvictTime.compareAndSet(last, now)) {
            return;
        }
        Iterator<Map.Entry<String, long[]>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            long[] slot = it.next().getValue();
            synchronized (slot) {
                if (now - slot[0] >= windowMs) {
                    it.remove();
                }
            }
        }
    }
}
