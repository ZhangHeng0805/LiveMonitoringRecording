package cn.zhangheng.record.httpServer.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口访问规则：方法上声明路径、鉴权级别、限流、允许的 HTTP 方法。
 * <p>
 * 用法示例：
 * <pre>
 * // 必须验证、每分钟最多 10 次
 * &#64;Endpoint("/parseDouYinLink", rate = &#64;RateLimit(limit = 10))
 * public void parseDouYinLink(RequestContext ctx) { ... }
 *
 * // 完全公开
 * &#64;Endpoint("/auth", auth = AuthPolicy.PUBLIC)
 * </pre>
 * 路径为「相对前缀」的路径，例如 handler 前缀是 {@code /action/}，
 * 写 {@code "/refresh"} 即对应 {@code /action/refresh}。
 *
 * @author: ZhangHeng
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Endpoint {

    /**
     * 相对 handler 前缀的路径，默认前缀匹配（"refresh" 也能匹配 "refreshXxx"）
     */
    String value();

    /**
     * 路径匹配方式
     */
    Match match() default Match.PREFIX;

    /**
     * 鉴权级别，默认私密（必须秘钥令牌）；只读接口显式声明 {@link AuthPolicy#NORMAL}，公开接口声明 {@link AuthPolicy#PUBLIC}
     */
    AuthPolicy auth() default AuthPolicy.PRIVATE;

    /**
     * 限流规则，limit &lt;= 0 表示不限流
     */
    RateLimit rate() default @RateLimit;

    /**
     * 允许的 HTTP 方法，空数组表示不限制
     */
    String[] methods() default {};

    /**
     * 路径匹配方式
     */
    enum Match {
        /**
         * 前缀匹配（保持老代码 startsWith 的语义）
         */
        PREFIX,
        /**
         * 完全相等
         */
        EXACT
    }
}
