package cn.zhangheng.record.okhttp;

import cn.zhangheng.record.bean.Constant;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/17 星期二 19:44
 * @version: 1.0
 * @description:
 */
public class OkHttpFactory {
    // 全局单例，整个项目共用这一个client
    private static final OkHttpClient CLIENT;
    // 常驻核心线程数：轮询间隔30s，核心线程常驻可避免每次请求都新建线程
    private static final int HTTP_CORE_THREADS = 2;
    private static final AtomicInteger OKHTTP_THREAD_SEQ = new AtomicInteger(0);

    static {
        // 默认 Dispatcher 的线程空闲60s即退出，与轮询叠加会几乎每次请求新建线程，
        // 这里改为常驻核心线程（超出部分空闲300s回收），并限制并发上限
        ThreadPoolExecutor dispatcherExecutor = new ThreadPoolExecutor(
                HTTP_CORE_THREADS,
                Math.max(HTTP_CORE_THREADS, Constant.maxMonitorThreads),
                300L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "OkHttp-pool-" + OKHTTP_THREAD_SEQ.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                // 并发打满时退化为调用方线程执行，避免直接抛 RejectedExecutionException
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CLIENT = new OkHttpClient.Builder()
                // 超时
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)//流式读取不要callTimeout
                .dispatcher(new Dispatcher(dispatcherExecutor))
                // 连接池：最大空闲5个，空闲连接保留300s
                .connectionPool(new ConnectionPool(Constant.maxMonitorThreads, 300, TimeUnit.SECONDS))
                // 自动跟随重定向
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[OkHttpShutdown] 开始优雅关闭 OkHttp...");
            try {
                // 1. 停止接收新任务
                ExecutorService executor = CLIENT.dispatcher().executorService();
                executor.shutdown();

                // 等待正在执行的任务完成，最多等待5秒
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // 超时后强制中断
                    executor.shutdownNow();
                }

                // 2. 清空连接池里所有空闲连接
                CLIENT.connectionPool().evictAll();

                // 3. 如果启用了磁盘缓存，关闭缓存
                if (CLIENT.cache() != null) {
                    CLIENT.cache().close();
                }
                System.out.println("[OkHttpShutdown] OkHttp 优雅关闭完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[OkHttpShutdown] 关闭过程被中断");
            } catch (Exception e) {
                System.err.println("[OkHttpShutdown] OkHttp关闭异常：" + e.getMessage());
            }
        }, "okhttp-shutdown-hook"));
    }

    public static OkHttpClient getClient() {
        return CLIENT;
    }
}
