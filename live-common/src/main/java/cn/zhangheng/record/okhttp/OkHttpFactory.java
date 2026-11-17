package cn.zhangheng.record.okhttp;

import cn.zhangheng.record.bean.Constant;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

    static {
        CLIENT = new OkHttpClient.Builder()
                // 超时
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
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
