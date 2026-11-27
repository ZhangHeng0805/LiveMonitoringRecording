package cn.zhangheng.record.httpServer.router;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.record.httpServer.handle.JSONHandler;
import cn.zhangheng.record.httpServer.util.HandlerUtils;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解式接口路由基类：把「路径 / 鉴权 / 限流 / HTTP 方法」从 if-else 里挪到方法注解上。
 * <p>
 * 子类只需要写带 {@link Endpoint} 注解的方法（签名固定为 {@code void xxx(RequestContext ctx)}），
 * 不再需要重写 {@link #request(HttpExchange)} 和 {@code filter()}：
 * <pre>
 * public class ActionHandler extends AnnotationHandler {
 *     public ActionHandler(String prefix) {
 *         super(prefix, (ex, policy) -> policy == AuthPolicy.PUBLIC
 *                 || (policy == AuthPolicy.NORMAL ? checkNormalAuth(ex) : checkSecretAuth(ex)));
 *     }
 *
 *     &#64;Endpoint("/refresh")
 *     public void refresh(RequestContext ctx) {
 *         Map&lt;String, String&gt; query = ctx.query();
 *         Message&lt;Object&gt; msg = ctx.message();
 *         ...
 *     }
 * }
 * </pre>
 *
 * @author: ZhangHeng
 */
public abstract class AnnotationHandler extends JSONHandler {

    private static final Logger log = LoggerFactory.getLogger(AnnotationHandler.class);

    private final AuthResolver authResolver;
    /** 限流阈值配置解析器，可为 null（此时只支持注解上的固定阈值） */
    private final RateLimitResolver rateLimitResolver;
    /** 精确匹配路由 */
    private final Map<String, Route> exactRoutes = new HashMap<>();
    /** 前缀匹配路由，按路径长度倒序保证最长优先 */
    private final List<Route> prefixRoutes = new ArrayList<>();

    protected AnnotationHandler(String prefix, AuthResolver authResolver) {
        this(prefix, authResolver, null);
    }

    protected AnnotationHandler(String prefix, AuthResolver authResolver, RateLimitResolver rateLimitResolver) {
        super(prefix);
        this.authResolver = authResolver;
        this.rateLimitResolver = rateLimitResolver;
        scanRoutes();
    }

    /**
     * 扫描本类所有带 {@link Endpoint} 的 public 方法，构建路由表
     */
    private void scanRoutes() {
        for (Method method : getClass().getMethods()) {
            Endpoint endpoint = method.getAnnotation(Endpoint.class);
            if (endpoint == null) {
                continue;
            }
            if (method.getParameterCount() != 1
                    || !RequestContext.class.equals(method.getParameterTypes()[0])) {
                throw new IllegalStateException("@Endpoint 方法签名必须是 void xxx(RequestContext ctx)："
                        + getClass().getName() + "#" + method.getName());
            }
            if (StrUtil.isBlank(endpoint.value())) {
                throw new IllegalStateException("@Endpoint 路径不能为空：" + method.getName());
            }
            method.setAccessible(true);
            // 注解路径统一补前导斜杠，避免 "/refresh" 与 "refresh" 两种写法混用
            String path = endpoint.value().startsWith("/") ? endpoint.value() : "/" + endpoint.value();
            Route route = new Route(endpoint, path, method, rateLimitResolver);
            if (endpoint.match() == Endpoint.Match.EXACT) {
                exactRoutes.put(path, route);
            } else {
                prefixRoutes.add(route);
            }
        }
        prefixRoutes.sort(Comparator.comparingInt((Route r) -> r.path.length()).reversed());
        log.debug("{} 注解路由注册完成：精确 {} 个，前缀 {} 个", getClass().getSimpleName(),
                exactRoutes.size(), prefixRoutes.size());
    }

    @Override
    protected void request(HttpExchange httpExchange) throws Exception {
        // getIndexPath 剥掉前缀后不带前导斜杠，这里统一补成 "/xxx"，与注解路径对齐
        String rawPath = StrUtil.nullToEmpty(getIndexPath(httpExchange, prefix));
        String indexPath = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        Route route = resolve(indexPath);
        if (route == null) {
            Message<Object> msg = new Message<>();
            msg.setCode(1);
            msg.setMessage("访问的接口路径不存在！" + prefix + indexPath.substring(1));
            responseJson(httpExchange, msg);
            return;
        }

        // HTTP 方法限制
        if (!route.methodAllowed(httpExchange.getRequestMethod())) {
            httpExchange.getResponseHeaders().set("Allow", String.join(", ", route.endpoint.methods()));
            sendJson(httpExchange, 405, buildMessage(405, "该接口不支持 " + httpExchange.getRequestMethod() + " 请求"));
            return;
        }

        // 鉴权：规则来自注解（公开 / 普通令牌 / 秘钥令牌），前端只在真正 403 时才升级鉴权
        if (!authResolver.check(httpExchange, route.endpoint.auth())) {
            // 403 只回通用提示：不带接口级别、令牌名称与获取方式，避免向未授权请求泄露接口细节
            sendJson(httpExchange, 403, buildMessage(403, "无权访问"));
            return;
        }

        // 限流：按「接口 + 来源 IP」计数
        FixedWindowRateLimiter limiter = route.limiter;
        if (limiter != null) {
            String id = HandlerUtils.getClientIP(httpExchange) + "|" + route.path;
            long retryAfter = limiter.tryAcquire(id);
            if (retryAfter > 0) {
                limiter.evictExpired();
                httpExchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfter));
                sendJson(httpExchange, 429,
                        buildMessage(429, "请求过于频繁，请 " + retryAfter + " 秒后再试"));
                return;
            }
        }

        RequestContext ctx = new RequestContext(httpExchange, prefix, indexPath, route.endpoint.value());
        try {
            route.method.invoke(this, ctx);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Message<Object> msg = ctx.message();
            msg.setCode(1);
            msg.setTitle("接口异常！");
            msg.setMessage(ThrowableUtil.getAllCauseMessage(cause));
            log.error("{}{}接口异常:{}", prefix, indexPath, ThrowableUtil.getAllCauseMessage(cause));
        } catch (Throwable e) {
            Message<Object> msg = ctx.message();
            msg.setCode(1);
            msg.setTitle("接口异常！");
            msg.setMessage(ThrowableUtil.getAllCauseMessage(e));
            log.error("{}{}接口异常:{}", prefix, indexPath, ThrowableUtil.getAllCauseMessage(e));
        }

        if (httpExchange.getResponseCode() == -1) {
            responseJson(httpExchange, ctx.message());
        }
    }

    /**
     * 路径解析：先精确匹配，再按最长前缀匹配
     */
    private Route resolve(String indexPath) {
        Route exact = exactRoutes.get(indexPath);
        if (exact != null) {
            return exact;
        }
        for (Route route : prefixRoutes) {
            if (indexPath.startsWith(route.path)) {
                return route;
            }
        }
        return null;
    }

    private static Message<Object> buildMessage(int code, String message) {
        Message<Object> msg = new Message<>();
        msg.setCode(code);
        msg.setMessage(message);
        return msg;
    }

    private void sendJson(HttpExchange exchange, int status, Message<?> msg) throws IOException {
        responseJson(exchange, JSONUtil.toJsonStr(msg), status);
    }

    /**
     * 单条路由：注解元数据 + 反射方法 + 该路由独立的限流器
     */
    private static final class Route {
        private final Endpoint endpoint;
        private final String path;
        private final Method method;
        private final FixedWindowRateLimiter limiter;

        private Route(Endpoint endpoint, String path, Method method, RateLimitResolver resolver) {
            this.endpoint = endpoint;
            this.path = path;
            this.method = method;
            RateLimit rate = endpoint.rate();
            if (rate == null) {
                this.limiter = null;
                return;
            }
            String configKey = rate.configKey();
            if (!StrUtil.isBlank(configKey) && resolver != null) {
                // 每次申请配额实时读配置：改 server.api.* 后无需重启
                this.limiter = new FixedWindowRateLimiter(
                        () -> resolver.limit(configKey, rate.limit()), rate.windowMs());
            } else if (rate.limit() > 0) {
                this.limiter = new FixedWindowRateLimiter(rate.limit(), rate.windowMs());
            } else {
                this.limiter = null;
            }
        }

        private boolean methodAllowed(String requestMethod) {
            String[] allowed = endpoint.methods();
            if (allowed == null || allowed.length == 0) {
                return true;
            }
            for (String m : allowed) {
                if (m.equalsIgnoreCase(requestMethod)) {
                    return true;
                }
            }
            return false;
        }
    }
}
