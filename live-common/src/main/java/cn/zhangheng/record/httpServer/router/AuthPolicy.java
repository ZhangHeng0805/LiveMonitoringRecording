package cn.zhangheng.record.httpServer.router;

/**
 * 接口鉴权级别。
 * 具体怎么判定由 {@link AuthResolver} 决定（各项目可接自己的令牌/秘钥体系）。
 *
 * @author: ZhangHeng
 */
public enum AuthPolicy {

    /**
     * 完全公开：不校验（如换令牌入口、纯公开解析接口）
     */
    PUBLIC,

    /**
     * 只读：带令牌/秘钥放行；不带令牌时是否放行由 server.auth.publicRead 决定（匿名只读）
     */
    READ,

    /**
     * 必须校验：没有有效令牌/会话直接拒绝（403），前端据此弹秘钥框
     */
    REQUIRED
}
