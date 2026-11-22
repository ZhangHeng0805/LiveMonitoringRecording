package cn.zhangheng.record.httpServer.router;

import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;

import java.io.IOException;
import java.util.Map;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseRequestBodyStr;

/**
 * 注解式接口方法的入参封装：请求对象 + 统一响应体 + 懒解析的 query/body。
 *
 * @author: ZhangHeng
 */
public class RequestContext {

    private final HttpExchange exchange;
    private final String prefix;
    private final String indexPath;
    private final String routePath;
    private final Message<Object> message = new Message<>();

    private Map<String, String> query;
    private String body;

    public RequestContext(HttpExchange exchange, String prefix, String indexPath, String routePath) {
        this.exchange = exchange;
        this.prefix = prefix;
        this.indexPath = indexPath;
        this.routePath = routePath;
    }

    public HttpExchange exchange() {
        return exchange;
    }

    /**
     * 当前请求去掉 handler 前缀后的路径
     */
    public String indexPath() {
        return indexPath;
    }

    /**
     * 命中的 {@link Endpoint} 路径
     */
    public String routePath() {
        return routePath;
    }

    /**
     * 统一响应体，方法里只管往里写，框架负责序列化输出
     */
    public Message<Object> message() {
        return message;
    }

    /**
     * 解析后的 URL 查询参数（多个同名参数取第一个）
     */
    public Map<String, String> query() {
        if (query == null) {
            query = parseQuery(exchange);
        }
        return query;
    }

    /**
     * 请求体字符串（只读一次，重复调用返回缓存）
     */
    public String body() throws IOException {
        if (body == null) {
            body = parseRequestBodyStr(exchange, java.nio.charset.StandardCharsets.UTF_8);
        }
        return body;
    }
}
