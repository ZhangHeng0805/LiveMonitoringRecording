package cn.zhangheng.common.httpServer.handle;

import cn.hutool.jwt.JWT;
import cn.zhangheng.common.bean.Constant;
import com.zhangheng.util.RandomUtil;
import com.zhangheng.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.security.KeyRep.Type.SECRET;

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

    public static void main(String[] args) throws InterruptedException {
        String token = generateToken("123", 8);
        System.out.println("token = " + token);

        // 第一次校验
        System.out.println("第一次校验：" + checkToken(token));
        TimeUnit.SECONDS.sleep(5);

        System.out.println("token2 立即校验：" + checkToken(token));

        TimeUnit.SECONDS.sleep(5);
        System.out.println("token2 过期后校验：" + checkToken(token));
    }

    // 生成token
    public static String generateToken(String sessionID, long expireSec) {
        // 快速创建token

        long now = System.currentTimeMillis() / 1000;
        return JWT.create()
                .setPayload("session_id", sessionID)
                .setPayload("exp", now + expireSec)
                .setPayload("iat", now)
                .setPayload("nbf", now)
                .setKey(SECRET_KEY)
                .sign(); // 自动用 HS256
    }

    public static String generateToken(String sessionID) {
        return generateToken(sessionID, EXPIRE);
    }

    //校验token
    public static boolean checkToken(String token) {
        if (token == null || token.isEmpty()) return false;
        try {
            JWT jwt = JWT.of(token).setKey(SECRET_KEY);
            // 先校验签名，再校验时间
            boolean signOk = jwt.verify();
            if (!signOk) {
                System.err.println("签名校验失败");
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

    public static String getSessionID(String token) {
        Object sessionId = getClaim(token, "session_id");
        return sessionId == null ? null : (String) sessionId;
    }
}
