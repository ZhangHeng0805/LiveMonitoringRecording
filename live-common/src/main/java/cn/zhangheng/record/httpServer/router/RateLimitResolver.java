package cn.zhangheng.record.httpServer.router;

/**
 * 限流阈值配置解析器：把 {@link RateLimit#configKey()} 翻译成实际阈值。
 * <p>
 * 路由框架本身不认识配置类，具体怎么读配置交给使用方决定
 * （本项目的实现见 lmr 的 {@code RateLimitResolvers.defaults()}）。
 *
 * @author: ZhangHeng
 */
public interface RateLimitResolver {

    /**
     * @param configKey 配置项 key
     * @param fallback  配置缺失/读取失败时的兜底值（即注解上的 {@link RateLimit#limit()}）
     * @return 实际阈值，&lt;=0 表示不限流
     */
    int limit(String configKey, int fallback);
}
