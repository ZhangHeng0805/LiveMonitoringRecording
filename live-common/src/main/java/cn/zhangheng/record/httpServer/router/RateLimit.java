package cn.zhangheng.record.httpServer.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级限流规则（固定窗口，按来源 IP 计数）。
 * <p>
 * 静态写法：{@code @RateLimit(limit = 3)} 表示同一 IP 每分钟最多 3 次；limit &lt;= 0 表示不限流。
 * <p>
 * 读配置写法：{@code @RateLimit(configKey = "server.api.parsePerMinute", limit = 3)}
 * 阈值每次请求都从配置读，{@code limit} 作为配置缺失/读取失败时的兜底值；
 * 配置值为 0 时表示不限流。窗口长度仍由 {@link #windowMs()} 控制。
 *
 * @author: ZhangHeng
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface RateLimit {

    /**
     * 每个窗口允许的请求次数，&lt;=0 表示不限流。
     * 配置了 {@link #configKey()} 时作为兜底值
     */
    int limit() default 0;

    /**
     * 窗口长度（毫秒），默认 1 分钟
     */
    long windowMs() default 60_000L;

    /**
     * 阈值所在的配置项 key（如 {@code server.api.parsePerMinute}），
     * 为空表示用 {@link #limit()} 的固定值；非空时每次请求都实时读配置
     */
    String configKey() default "";
}
