package cn.zhangheng.record.httpServer.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.jwt.JWT;
import cn.zhangheng.record.bean.Constant;

import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/10 星期三 07:08
 * @version: 1.0
 * @description:
 */
public class JWTUtil {
    // 接口令牌类型标识，用于区分「Cookie 会话 token」与「无状态接口 token」
    public static final String API_TOKEN_TYPE = "api";
    // 密钥，生产环境放在配置文件，长度尽量长
    private static final byte[] SECRET_KEY = Constant.deviceUniqueId.getBytes();


    // 生成token
    public static String generateToken(Map<String, Object> payload, long expireSec) {

        long now = System.currentTimeMillis() / 1000;
        JWT jwt = JWT.create();
        payload.forEach(jwt::setPayload);
        return jwt
                .setPayload("exp", now + expireSec)
                .setPayload("iat", now)
                .setPayload("nbf", now)
                .setPayload("iss ", "xxxr")
                .setKey(SECRET_KEY)
                .sign(); // 自动用 HS256
    }

    /**
     * 生成无状态接口令牌（用于前端托管在 GitHub Pages 等跨站场景：
     * 浏览器会拦截第三方 Cookie，只能靠 Authorization: Bearer 传递）
     *
     * @param expireSec 有效期（秒）
     */
    public static String generateApiToken(long expireSec) {
        return generateToken(MapUtil.of("typ", API_TOKEN_TYPE), expireSec);
    }

    /**
     * 校验接口令牌：签名有效、未过期、且类型为 api
     */
    public static boolean checkApiToken(String token) {
        if (!checkToken(token)) return false;
        try {
            JWT jwt = JWT.of(token).setKey(SECRET_KEY);
            Object typ = jwt.getPayload("typ");
            return typ != null && API_TOKEN_TYPE.equals(typ.toString());
        } catch (Exception e) {
            return false;
        }
    }

    //校验token
    public static boolean checkToken(String token) {
        if (token == null || token.isEmpty()) return false;
        try {
            JWT jwt = JWT.of(token).setKey(SECRET_KEY);
            // 先校验签名，再校验时间
            boolean signOk = jwt.verify();
            if (!signOk) {
                System.err.println("jwt签名校验失败");
                return false;
            }
            // 校验exp/iat时间
            return jwt.validate(0);
        } catch (Exception e) {
            // 打印完整异常，看清到底是签名/过期/格式问题
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取载荷值
     */
    public static Object getClaim(String token, String key) {
        if (!checkToken(token)) return null;
        JWT jwt = cn.hutool.jwt.JWTUtil.parseToken(token);
        return jwt.getPayload(key);
    }

    public static String getValeStr(String token, String key) {
        Object value = getClaim(token, key);
        return value == null ? null : (String) value;
    }
}
