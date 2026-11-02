package cn.zhangheng.common.cache;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheListener;
import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;
import cn.hutool.cache.impl.TimedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/02 星期一 16:29
 * @version: 1.0
 * @description: 带缓存的单次数据加载工具类
 */
public class CacheWithOnceLoadHelper<K, V> {
    private static final Logger log = LoggerFactory.getLogger(CacheWithOnceLoadHelper.class);

    private final LRUCache<K, V> cache;
    private final ConcurrentHashMap<K, OnceLoadHolder<V>> holderMap = new ConcurrentHashMap<>();

    private final DataLoader<K, V> dataLoader;
    private final int maxRetry;
    private final long retrySleepMs;
    private final long loadTimeoutMs;
    private final boolean enableFallback;
    private final V fallbackValue;
    private final long holderIdleExpireMs;
    private final long cacheExpireMs;
    private final boolean isPrintLog;

    @FunctionalInterface
    public interface DataLoader<K, V> {
        V load(K key) throws Exception;
    }

    private CacheWithOnceLoadHelper(Builder<K, V> builder) {
        this.dataLoader = builder.dataLoader;
        this.maxRetry = builder.maxRetry;
        this.retrySleepMs = builder.retrySleepMs;
        this.loadTimeoutMs = builder.loadTimeoutMs;
        this.enableFallback = builder.enableFallback;
        this.fallbackValue = builder.fallbackValue;
        this.holderIdleExpireMs = builder.holderIdleExpireMs;
        this.cacheExpireMs = builder.cacheExpireMs;
        this.isPrintLog = builder.isPrintLog;

        // 构建Time缓存 + 注册移除监听器
        this.cache = CacheUtil.newLRUCache(builder.cacheMaxSize);
        this.cache.setListener(new CacheListener<K, V>() {
            @Override
            public void onRemove(K key, V value) {
                // 缓存条目被移除：过期访问触发 / Time定时扫描过期 / 主动remove
                if (isPrintLog)
                    log.debug("[CacheListener] 缓存key移除，自动清理holder, key={}", key);
                holderMap.remove(key);
            }
        });
    }

    /**
     * 对外查询入口
     *
     * @param key               查询key
     * @param apiAwaitTimeoutMs 接口等待超时（小于网关超时）
     * @return 数据
     * @throws TimeoutException 等待超时
     * @throws Exception        加载失败且无降级
     */
    public V get(K key, long apiAwaitTimeoutMs) throws Exception {
        V cacheVal = cache.get(key);
        if (cacheVal != null) {
            return cacheVal;
        }

        OnceLoadHolder<V> holder = holderMap.computeIfAbsent(key, k -> OnceLoadHolder.<V>builder(() -> dataLoader.load(k))
                .maxRetry(maxRetry)
                .retrySleepMs(retrySleepMs)
                .loadTimeoutMs(loadTimeoutMs)
                .enableFallback(enableFallback, fallbackValue)
                .build());

        V val = holder.get(apiAwaitTimeoutMs);
        cache.put(key, val, cacheExpireMs);
        scheduleRemoveHolder(key);
        return val;
    }

    /**
     * 主动失效缓存（数据更新后调用）
     */
    public void invalidate(K key) {
        cache.remove(key);
        // listener会回调自动清holder，这里不需要重复remove
        if (isPrintLog)
            log.debug("[CacheHelper] key={} 主动失效缓存", key);
    }

    public void invalidateAll() {
        cache.clear();
        holderMap.clear();
    }

    /**
     * 延迟兜底清理holder，作为listener的补充防护
     */
    private void scheduleRemoveHolder(K key) {
        Thread thread = new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(holderIdleExpireMs);
                if (cache.get(key) != null) {
                    holderMap.remove(key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cache-holder-clean");
        thread.setDaemon(true);
        thread.start();
    }

    public static <K, V> Builder<K, V> builder(DataLoader<K, V> loader) {
        return new Builder<>(loader);
    }

    public static class Builder<K, V> {
        private final DataLoader<K, V> dataLoader;
        //缓存过期时间
        private long cacheExpireMs = 30_000;
        //计划定时扫描过期延时
        private int cacheMaxSize = 10000;
        //重试次数
        private int maxRetry = 1;
        //重试间隔时间
        private long retrySleepMs = 200;
        //加载数据超时时间
        private long loadTimeoutMs = 1500;
        //是否开启降级兜底,加载数据失败时要不要返回兜底数据
        private boolean enableFallback = false;
        //兜底返回值
        private V fallbackValue = null;
        //排队器闲置清理时间
        private long holderIdleExpireMs = 5000;
        private boolean isPrintLog = false;

        public Builder(DataLoader<K, V> loader) {
            this.dataLoader = loader;
        }

        public Builder<K, V> cacheExpireMs(long ms) {
            this.cacheExpireMs = ms;
            return this;
        }

        public Builder<K, V> cacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder<K, V> maxRetry(int maxRetry) {
            this.maxRetry = Math.max(0, maxRetry);
            return this;
        }

        public Builder<K, V> retrySleepMs(long retrySleepMs) {
            this.retrySleepMs = retrySleepMs;
            return this;
        }

        public Builder<K, V> loadTimeoutMs(long loadTimeoutMs) {
            this.loadTimeoutMs = loadTimeoutMs;
            return this;
        }

        public Builder<K, V> enableFallback(V fallbackValue) {
            this.enableFallback = true;
            this.fallbackValue = fallbackValue;
            return this;
        }

        public Builder<K, V> holderIdleExpireMs(long ms) {
            this.holderIdleExpireMs = ms;
            return this;
        }

        public Builder<K, V> isPrintLog(boolean isPrintLog) {
            this.isPrintLog = isPrintLog;
            return this;
        }

        public CacheWithOnceLoadHelper<K, V> build() {
            return new CacheWithOnceLoadHelper<>(this);
        }
    }

    public static class OnceLoadHolder<T> {
        private static final Logger log = LoggerFactory.getLogger(OnceLoadHolder.class);
        private volatile CompletableFuture<T> future;
        private final DataLoader0<T> loader;
        private final int maxRetry;
        private final long retrySleepMs;
        private final long loadTimeoutMs;
        private final boolean enableFallback;
        private final T fallbackValue;

        @FunctionalInterface
        public interface DataLoader0<T> {
            T load() throws Exception;
        }

        private OnceLoadHolder(Builder0<T> builder) {
            this.loader = builder.loader;
            this.maxRetry = builder.maxRetry;
            this.retrySleepMs = builder.retrySleepMs;
            this.loadTimeoutMs = builder.loadTimeoutMs;
            this.enableFallback = builder.enableFallback;
            this.fallbackValue = builder.fallbackValue;
        }

        public T get(long apiAwaitTimeoutMs) throws Exception {
            CompletableFuture<T> f = future;
            if (f == null) {
                synchronized (this) {
                    f = future;
                    if (f == null) {
                        f = new CompletableFuture<>();
                        future = f;
                        T result = null;
                        boolean success = false;
                        Throwable lastEx = null;
                        try {
                            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                                try {
//                                    log.debug("[OnceLoad] 开始加载，第{}次尝试", attempt + 1);
                                    CompletableFuture<T> task = CompletableFuture.supplyAsync(() -> {
                                        try {
                                            return loader.load();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                    result = task.get(loadTimeoutMs, TimeUnit.MILLISECONDS);
                                    success = true;
//                                    log.debug("[OnceLoad] 加载成功，尝试次数:{}", attempt + 1);
                                    break;
                                } catch (TimeoutException e) {
                                    lastEx = new Exception("单次加载超时", e);
                                    log.warn("[OnceLoad] 第{}次加载超时", attempt + 1, e);
                                } catch (Exception e) {
                                    lastEx = e;
                                    log.warn("[OnceLoad] 第{}次加载异常", attempt + 1, e);
                                }
                                if (attempt < maxRetry) {
                                    TimeUnit.MILLISECONDS.sleep(retrySleepMs);
                                }
                            }
                            if (success) {
                                f.complete(result);
                            } else {
                                if (enableFallback) {
                                    log.error("[OnceLoad] 全部重试失败，返回降级数据", lastEx);
                                    f.complete(fallbackValue);
                                } else {
                                    log.error("[OnceLoad] 全部重试失败，抛出异常", lastEx);
                                    f.completeExceptionally(lastEx);
                                }
                            }
                        } catch (InterruptedException e) {
                            log.error("[OnceLoad] 加载中断", e);
                            f.completeExceptionally(e);
                            Thread.currentThread().interrupt();
                        } catch (Throwable e) {
                            log.error("[OnceLoad] 加载未知异常", e);
                            f.completeExceptionally(e);
                        }
                    }
                }
            }
            return f.get(apiAwaitTimeoutMs, TimeUnit.MILLISECONDS);
        }

        public synchronized void reset() {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            future = null;
            log.info("[OnceLoad] holder重置");
        }

        public static <T> Builder0<T> builder(DataLoader0<T> loader) {
            return new Builder0<>(loader);
        }

        public static class Builder0<T> {
            private final DataLoader0<T> loader;
            private int maxRetry = 0;
            private long retrySleepMs = 200;
            private long loadTimeoutMs = 1500;
            private boolean enableFallback = false;
            private T fallbackValue = null;

            public Builder0(DataLoader0<T> loader) {
                this.loader = loader;
            }

            public Builder0<T> maxRetry(int maxRetry) {
                this.maxRetry = Math.max(0, maxRetry);
                return this;
            }

            public Builder0<T> retrySleepMs(long retrySleepMs) {
                this.retrySleepMs = retrySleepMs;
                return this;
            }

            public Builder0<T> loadTimeoutMs(long loadTimeoutMs) {
                this.loadTimeoutMs = loadTimeoutMs;
                return this;
            }

            public Builder0<T> enableFallback(boolean enable, T val) {
                this.enableFallback = enable;
                this.fallbackValue = val;
                return this;
            }

            public OnceLoadHolder<T> build() {
                return new OnceLoadHolder<>(this);
            }
        }
    }
}
