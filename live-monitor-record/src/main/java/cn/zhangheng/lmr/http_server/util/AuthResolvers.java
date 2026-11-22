package cn.zhangheng.lmr.http_server.util;

import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.AuthResolver;

/**
 * 本项目默认的注解鉴权实现：把 {@link AuthPolicy} 映射到 CheckUtils 的令牌/秘钥/匿名只读判断。
 *
 * @author: ZhangHeng
 */
public final class AuthResolvers {

    private AuthResolvers() {
    }

    public static AuthResolver defaults() {
        return (exchange, policy) -> {
            if (policy == AuthPolicy.PUBLIC) {
                return true;
            }
            if (policy == AuthPolicy.READ) {
                return CheckUtils.checkReadAuth(exchange);
            }
            return CheckUtils.checkRequestAuth(exchange);
        };
    }
}
