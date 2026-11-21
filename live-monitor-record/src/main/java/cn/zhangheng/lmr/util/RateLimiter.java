package cn.zhangheng.lmr.util;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易固定窗口限流器（无第三方依赖，JDK1.8 可用）
 * 以「来源标识（IP）」为单位统计每分钟请求次数，超出返回建议等待秒数。
 * 只用于保护重资源接口（如抖音作品解析），不参与鉴权。
 *
 * @author: ZhangHeng
 */
public class RateLimiter {

    /**
     * 来源标识 -> [窗口起始毫秒, 窗口内已用次数]
     */
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();
    private final long windowMs;

    public RateLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    /**
     * 申请一次调用配额
     *
     * @param id            来源标识（IP）
     * @param limitPerWindow 当前窗口允许次数，&lt;=0 表示不限
     * @return 0 表示放行；大于 0 表示被限流，值为建议重试的秒数
     */
    public long tryAcquire(String id, int limitPerWindow) {
        if (limitPerWindow <= 0) {
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
            if (slot[1] >= limitPerWindow) {
                long waitMs = windowMs - (now - slot[0]);
                return Math.max(1L, (waitMs + 999) / 1000);
            }
            slot[1]++;
            return 0;
        }
    }

    /**
     * 清理过期窗口，避免长期运行后 Map 只增不减
     */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        if (windows.size() < 200) {
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
