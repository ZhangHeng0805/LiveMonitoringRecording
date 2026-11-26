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
    // 秘钥令牌类型标识（用操作秘钥换取，权限最高）
    public static final String API_TOKEN_TYPE = "api";
    // 普通令牌类型标识（公开接口签发，只够访问只读接口）
    public static final String PUBLIC_TOKEN_TYPE = "public";
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
     * 生成秘钥令牌：必须先用操作秘钥验证才能拿到，可访问私密接口
     *
     * @param expireSec 有效期（秒）
     */
    public static String generateApiToken(long expireSec) {
        return generateToken(MapUtil.of("typ", API_TOKEN_TYPE), expireSec);
    }

    /**
     * 生成普通令牌：由公开接口直接签发，无需秘钥，但只能访问普通（只读）接口
     *
     * @param expireSec 有效期（秒）
     */
    public static String generatePublicToken(long expireSec) {
        return generateToken(MapUtil.of("typ", PUBLIC_TOKEN_TYPE), expireSec);
    }

    /**
     * 校验秘钥令牌：签名有效、未过期、且类型为秘钥（兼容旧版 api 类型）
     */
    public static boolean checkApiToken(String token) {
        return API_TOKEN_TYPE.equals(getTokenType(token));
    }

    /**
     * 校验普通令牌：签名有效、未过期、且类型为 public
     */
    public static boolean checkPublicToken(String token) {
        return PUBLIC_TOKEN_TYPE.equals(getTokenType(token));
    }

    /**
     * 令牌是否达到普通级别（普通令牌或秘钥令牌都算）
     */
    public static boolean checkNormalToken(String token) {
        String type = getTokenType(token);
        return API_TOKEN_TYPE.equals(type) || PUBLIC_TOKEN_TYPE.equals(type);
    }

    /**
     * 取令牌类型；签名/有效期不通过返回 null
     */
    public static String getTokenType(String token) {
        if (!checkToken(token)) return null;
        try {
            Object typ = JWT.of(token).setKey(SECRET_KEY).getPayload("typ");
            return typ == null ? null : typ.toString();
        } catch (Exception e) {
            return null;
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
