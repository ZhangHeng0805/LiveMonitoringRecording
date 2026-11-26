package cn.zhangheng.record.httpServer.router;

import com.sun.net.httpserver.HttpExchange;

/**
 * 鉴权判定器：把 {@link AuthPolicy} 翻译成项目自己的校验逻辑。
 * 放在接口里，是为了让路由框架（live-common）不依赖具体项目的令牌/秘钥实现。
 *
 * @author: ZhangHeng
 */
public interface AuthResolver {

    /**
     * @param exchange 当前请求
     * @param policy   该接口声明的鉴权级别（PUBLIC 公开 / NORMAL 普通令牌 / PRIVATE 秘钥令牌）
     * @return true 放行；false 由框架返回 403
     */
    boolean check(HttpExchange exchange, AuthPolicy policy);
}
