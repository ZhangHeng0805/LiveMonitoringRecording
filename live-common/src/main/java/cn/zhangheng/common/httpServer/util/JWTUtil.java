package cn.zhangheng.common.httpServer.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.jwt.JWT;
import cn.zhangheng.common.bean.Constant;

import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/10 星期三 07:08
 * @version: 1.0
 * @description:
 */
public class JWTUtil {
    // 密钥，生产环境放在配置文件，长度尽量长
    private static final byte[] SECRET_KEY = Constant.deviceUniqueId.getBytes();
    // 过期时间：2小时 毫秒
    public static final long EXPIRE = 2 * 60 * 60;


    // 生成token
    public static String generateToken(Map<String, Object> payload, long expireSec) {
        // 快速创建token

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
