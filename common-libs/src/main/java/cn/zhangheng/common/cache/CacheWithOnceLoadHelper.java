package cn.zhangheng.common.cache;

import cn.hutool.cache.CacheListener;
import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/02 星期一 16:29
 * @version: 1.2
 * @description: 带缓存的单次数据加载工具类 + 失败冷却熔断，修复失败永久异常、锁阻塞、清理时序问题
 */
public class CacheWithOnceLoadHelper<K, V> {
    private static final Logger log = LoggerFactory.getLogger(CacheWithOnceLoadHelper.class);

    // 自定义业务线程池：用于执行loader加载任务
    private final ExecutorService loadExecutor;
    // 定时清理器，替代每次新建Thread
    private final ScheduledExecutorService scheduler;

    private final LRUCache<K, V> cache;
    private final ConcurrentHashMap<K, OnceLoadHolder<V>> holderMap = new ConcurrentHashMap<>();
    private final int holderMapMaxSize;

    private final DataLoader<K, V> dataLoader;
    private final int maxRetry;
    private final long retrySleepMs;
    private final long loadTimeoutMs;
    private final boolean enableFallback;
    private final V fallbackValue;
    private final long holderIdleExpireMs;
    private final long cacheExpireMs;
    private final boolean isPrintLog;
    private final long failCoolDownMs;

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
        this.holderMapMaxSize = builder.holderMapMaxSize;
        this.failCoolDownMs = builder.failCoolDownMs;

        // 加载线程池
        this.loadExecutor = new ThreadPoolExecutor(
                builder.corePoolSize,
                builder.maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(builder.queueCapacity),
                new LoadThreadFactory("cache-once-load"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 定时清理线程
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new LoadThreadFactory("cache-holder-schedule"));

        this.cache = CacheUtil.newLRUCache(builder.cacheMaxSize);
        this.cache.setListener(new CacheListener<K, V>() {
            @Override
            public void onRemove(K key, V value) {
                if (isPrintLog) {
                    log.debug("[CacheListener] 缓存key移除，尝试清理holder, key={}", key);
                }
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
     * @throws Exception        加载失败且无降级 / 冷却期内禁止加载
     */
    public V get(K key, long apiAwaitTimeoutMs) throws Exception {
        V cacheVal = cache.get(key);
        if (cacheVal != null) {
            return cacheVal;
        }

        if (holderMap.size() >= holderMapMaxSize) {
            throw new RejectedExecutionException("holderMap达到上限:" + holderMapMaxSize + ", 拒绝新增key:" + key);
        }

        OnceLoadHolder<V> holder = holderMap.computeIfAbsent(key, k -> OnceLoadHolder.<V>builder(() -> dataLoader.load(k))
                .maxRetry(maxRetry)
                .retrySleepMs(retrySleepMs)
                .loadTimeoutMs(loadTimeoutMs)
                .enableFallback(enableFallback, fallbackValue)
                .failCoolDownMs(failCoolDownMs)
                .executor(loadExecutor)
                .build());

        V val = holder.get(apiAwaitTimeoutMs);
        cache.put(key, val, cacheExpireMs);
        scheduleRemoveHolder(key);
        return val;
    }

    /**
     * 主动失效缓存（数据更新后调用）
     * 会重置holder冷却时间，下一次get允许直接发起加载
     */
    public void invalidate(K key) {
        cache.remove(key);
        OnceLoadHolder<V> holder = holderMap.remove(key);
        if (holder != null) {
            holder.reset();
            if (isPrintLog) {
                log.debug("[CacheHelper] key={} 主动失效，重置holder与冷却时间", key);
            }
        }
        if (isPrintLog) {
            log.debug("[CacheHelper] key={} 主动失效缓存", key);
        }
    }

    public void invalidateAll() {
        cache.clear();
        holderMap.forEach((k, h) -> h.reset());
        holderMap.clear();
    }

    /**
     * 延迟兜底清理holder：缓存已经不存在才清holder
     */
    private void scheduleRemoveHolder(K key) {
        scheduler.schedule(() -> {
            if (cache.get(key) == null) {
                holderMap.remove(key);
                if (isPrintLog) {
                    log.debug("[CacheHelper] 空闲到期清理holder, key={}", key);
                }
            }
        }, holderIdleExpireMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 销毁线程池，服务关闭时调用
     */
    public void shutdown() {
        loadExecutor.shutdown();
        scheduler.shutdown();
    }

    public static <K, V> Builder<K, V> builder(DataLoader<K, V> loader) {
        return new Builder<>(loader);
    }

    public static class Builder<K, V> {
        private final DataLoader<K, V> dataLoader;
        private long cacheExpireMs = 30_000;
        private int cacheMaxSize = 10000;
        private int maxRetry = 1;
        private long retrySleepMs = 200;//重试间隔时间
        private long loadTimeoutMs = 1500;
        private boolean enableFallback = false;
        private V fallbackValue = null;
        private long holderIdleExpireMs = 5000;
        private boolean isPrintLog = false;

        // 线程池参数
        private int corePoolSize = 8;
        private int maxPoolSize = 16;
        private int queueCapacity = 200;
        private int holderMapMaxSize = 10000;

        // 失败冷却，0=关闭冷却
        private long failCoolDownMs = 0;

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

        public Builder<K, V> pool(int core, int max, int queueCap) {
            this.corePoolSize = core;
            this.maxPoolSize = max;
            this.queueCapacity = queueCap;
            return this;
        }

        public Builder<K, V> holderMapMaxSize(int size) {
            this.holderMapMaxSize = size;
            return this;
        }

        /**
         * 失败冷却时间，失败后多久内不再发起真实加载
         * @param ms 毫秒，0 关闭冷却
         */
        public Builder<K, V> failCoolDownMs(long ms) {
            this.failCoolDownMs = Math.max(0, ms);
            return this;
        }

        public CacheWithOnceLoadHelper<K, V> build() {
            return new CacheWithOnceLoadHelper<>(this);
        }
    }

    public static class OnceLoadHolder<T> {
        private static final Logger log = LoggerFactory.getLogger(OnceLoadHolder.class);
        private CompletableFuture<T> future;
        private volatile long lastFailTs;
        private final DataLoader0<T> loader;
        private final int maxRetry;
        private final long retrySleepMs;
        private final long loadTimeoutMs;
        private final boolean enableFallback;
        private final T fallbackValue;
        private final long failCoolDownMs;
        private final ExecutorService executor;

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
            this.failCoolDownMs = builder.failCoolDownMs;
            this.executor = builder.executor;
            this.lastFailTs = 0;
        }

        public T get(long apiAwaitTimeoutMs) throws Exception {
            CompletableFuture<T> f;
            synchronized (this) {
                long now = System.currentTimeMillis();
                if (failCoolDownMs > 0 && lastFailTs > 0 && (now - lastFailTs) < failCoolDownMs) {
                    String msg = String.format("处于失败冷却窗口，剩余冷却 %d ms", failCoolDownMs - (now - lastFailTs));
                    if (enableFallback) {
                        log.warn("[OnceLoad] {}，直接返回降级值", msg);
                        return fallbackValue;
                    } else {
                        throw new Exception(msg);
                    }
                }

                if (future == null || (future.isDone() && future.isCompletedExceptionally())) {
                    future = new CompletableFuture<>();
                    f = future;
                    // 提交一轮single-flight加载任务，只占用一条executor线程
                    executor.submit(() -> {
                        T result = null;
                        boolean success = false;
                        Throwable lastEx = null;
                        try {
                            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                                try {
                                    // Runnable::run：在当前这条线程直接执行，不向executor提交新任务
                                    CompletableFuture<T> task = CompletableFuture.supplyAsync(() -> {
                                        try {
                                            return loader.load();
                                        } catch (Exception e) {
                                            throw new CompletionException(e);
                                        }
                                    }, Runnable::run);
                                    result = task.get(loadTimeoutMs, TimeUnit.MILLISECONDS);
                                    success = true;
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
                                lastFailTs = 0;
                            } else {
                                lastFailTs = System.currentTimeMillis();
                                if (enableFallback) {
                                    log.error("[OnceLoad] 全部重试失败，记录冷却时间，返回降级数据", lastEx);
                                    f.complete(fallbackValue);
                                } else {
                                    log.error("[OnceLoad] 全部重试失败，记录冷却时间，抛出异常", lastEx);
                                    f.completeExceptionally(lastEx);
                                }
                            }
                        } catch (InterruptedException e) {
                            lastFailTs = System.currentTimeMillis();
                            log.error("[OnceLoad] 加载中断", e);
                            f.completeExceptionally(e);
                            Thread.currentThread().interrupt();
                        } catch (Throwable e) {
                            lastFailTs = System.currentTimeMillis();
                            log.error("[OnceLoad] 加载未知异常", e);
                            f.completeExceptionally(e);
                        }
                    });
                } else {
                    f = future;
                }
            }
            // 所有并发请求在这里等待同一个future，实现合并查询（single-flight核心）
            return f.get(apiAwaitTimeoutMs, TimeUnit.MILLISECONDS);
        }

        public synchronized void reset() {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            future = null;
            lastFailTs = 0;
            log.info("[OnceLoad] holder重置，清空冷却时间");
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
            private long failCoolDownMs = 0;
            private ExecutorService executor;

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

            public Builder0<T> failCoolDownMs(long ms) {
                this.failCoolDownMs = Math.max(0, ms);
                return this;
            }

            public Builder0<T> executor(ExecutorService executor) {
                this.executor = executor;
                return this;
            }

            public OnceLoadHolder<T> build() {
                return new OnceLoadHolder<>(this);
            }
        }
    }

    /**
     * 命名线程工厂
     */
    static class LoadThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(0);

        public LoadThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r,  prefix + "-" + seq.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}