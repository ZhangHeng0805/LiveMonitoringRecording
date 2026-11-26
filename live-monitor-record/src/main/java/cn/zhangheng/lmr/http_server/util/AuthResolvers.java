package cn.zhangheng.lmr.http_server.util;

import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.AuthResolver;

/**
 * 本项目默认的注解鉴权实现：把 {@link AuthPolicy} 映射到 CheckUtils 的三级令牌判断。
 * <ul>
 *     <li>PUBLIC（公开）：直接放行</li>
 *     <li>NORMAL（普通）：普通令牌 / 秘钥令牌 / 同源会话</li>
 *     <li>PRIVATE（私密）：秘钥令牌（同源会话只到普通级，不满足私密）</li>
 * </ul>
 *
 * @author: ZhangHeng
 */
public final class AuthResolvers {

    private AuthResolvers() {
    }

    public static AuthResolver defaults() {
        return (exchange, policy) -> {
            switch (policy) {
                case PUBLIC:
                    return true;
                case NORMAL:
                    return CheckUtils.checkNormalAuth(exchange);
                case PRIVATE:
                default:
                    return CheckUtils.checkSecretAuth(exchange);
            }
        };
    }
}
