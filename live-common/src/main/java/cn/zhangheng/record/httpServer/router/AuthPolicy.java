package cn.zhangheng.record.httpServer.router;

/**
 * 接口鉴权级别（三级）。
 * 具体怎么判定由 {@link AuthResolver} 决定（各项目可接自己的令牌/秘钥体系）。
 *
 * @author: ZhangHeng
 */
public enum AuthPolicy {

    /**
     * 公开：不需要任何令牌，任何人可直接访问（如换令牌入口、纯公开解析接口）
     */
    PUBLIC,

    /**
     * 普通：需要普通令牌。普通令牌由公开接口签发（action/token），
     * 只能访问只读类接口，代表「匿名访客也能拿到」的最低权限。
     */
    NORMAL,

    /**
     * 私密：需要秘钥令牌。秘钥令牌必须先用操作秘钥验证（action/auth?actionKey=xxx）换取，
     * 覆盖写操作、敏感数据与文件下载；普通令牌在此级别一律拒绝。
     */
    PRIVATE
}
