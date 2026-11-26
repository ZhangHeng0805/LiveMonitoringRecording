package cn.zhangheng.lmr.http_server.util;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.record.bean.Constant;
import cn.zhangheng.record.httpServer.util.JWTUtil;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomFileModel;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;

import java.util.Map;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.getRequestCookie;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.getRequestCookies;
import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseQuery;

/**
 * 接口鉴权工具：三级令牌体系
 * <p>
 * 1. 公开（PUBLIC）：完全不校验；
 * 2. 普通（NORMAL）：需要普通令牌，普通令牌由公开接口 action/token 直接签发，无需秘钥；
 * 3. 私密（PRIVATE）：需要秘钥令牌，只能由 action/auth?actionKey=&lt;操作秘钥&gt; 换取。
 * <p>
 * 令牌来源（按顺序尝试）：Authorization: Bearer &lt;token&gt; → URL 参数 token=&lt;token&gt; → 同源 Cookie 会话。
 * Cookie 会话只算普通级，私密接口必须显式携带秘钥令牌，避免被跨站请求（CSRF）利用。
 *
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:30
 * @version: 1.0
 * @description:
 */
public class CheckUtils {

    /**
     * 请求已达到的鉴权级别（挂在 HttpExchange 上，供后续处理判断脱敏/权限）
     */
    private static final String AUTH_LEVEL_ATTR = "lmr.auth.level";
    /** 未通过任何鉴权 */
    public static final int LEVEL_NONE = 0;
    /** 通过普通令牌（只读权限） */
    public static final int LEVEL_NORMAL = 1;
    /** 通过秘钥令牌或同源会话（完整权限） */
    public static final int LEVEL_SECRET = 2;

    /**
     * 普通接口鉴权：普通令牌、秘钥令牌、同源会话都放行
     */
    public static boolean checkNormalAuth(HttpExchange httpExchange) {
        return authenticate(httpExchange, false);
    }

    /**
     * 私密接口鉴权：只认秘钥令牌与同源会话，普通令牌一律拒绝
     */
    public static boolean checkSecretAuth(HttpExchange httpExchange) {
        return authenticate(httpExchange, true);
    }

    private static boolean authenticate(HttpExchange httpExchange, boolean secretOnly) {
        String token = resolveToken(httpExchange);
        if (StrUtil.isNotBlank(token)) {
            if (JWTUtil.checkApiToken(token)) {
                mark(httpExchange, LEVEL_SECRET);
                return true;
            }
            if (!secretOnly && JWTUtil.checkPublicToken(token)) {
                mark(httpExchange, LEVEL_NORMAL);
                return true;
            }
        }
        // 同源 Cookie 会话只代表「普通级」：浏览器会自动携带，若把它当私密凭证会留下 CSRF 面。
        // 私密接口必须显式带秘钥令牌（Authorization 头跨站不会自动附加，天然防 CSRF）。
        if (checkCookie(httpExchange)) {
            mark(httpExchange, LEVEL_NORMAL);
            return !secretOnly;
        }
        return false;
    }

    /**
     * 取请求携带的令牌：Authorization: Bearer → ?token=
     */
    private static String resolveToken(HttpExchange httpExchange) {
        String token = getBearerToken(httpExchange);
        if (StrUtil.isNotBlank(token)) {
            return token;
        }
        String query = httpExchange.getRequestURI().getQuery();
        if (StrUtil.isNotBlank(query)) {
            return parseQuery(query, java.nio.charset.StandardCharsets.UTF_8).get("token");
        }
        return null;
    }

    private static void mark(HttpExchange httpExchange, int level) {
        if (level > authLevel(httpExchange)) {
            httpExchange.setAttribute(AUTH_LEVEL_ATTR, level);
        }
    }

    /**
     * 当前请求已达到的鉴权级别：0 无 / 1 普通 / 2 秘钥
     */
    public static int authLevel(HttpExchange httpExchange) {
        Object level = httpExchange.getAttribute(AUTH_LEVEL_ATTR);
        return level instanceof Integer ? (Integer) level : LEVEL_NONE;
    }

    /**
     * 是否已达到秘钥级（普通令牌不算，用于决定敏感字段是否脱敏）
     */
    public static boolean isSecretAuthenticated(HttpExchange httpExchange) {
        return authLevel(httpExchange) >= LEVEL_SECRET;
    }

    /**
     * 是否已通过任意令牌校验（普通级即可）
     */
    public static boolean isAuthenticated(HttpExchange httpExchange) {
        return authLevel(httpExchange) >= LEVEL_NORMAL;
    }

    private static String getBearerToken(HttpExchange httpExchange) {
        String header = httpExchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) return null;
        header = header.trim();
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    public static boolean checkActionKey(Map<String, String> query, Message msg) {
        String actionKey = query.get("actionKey");
        if (StrUtil.isBlank(actionKey)) {
            msg.setMessage("操作秘钥不能为空！");
            return false;
        }
        if (!actionKey.equals(Constant.deviceUniqueId)) {
            msg.setMessage("操作秘钥错误！");
            return false;
        }
        return true;
    }

    public static boolean checkRoomKey(Map<String, String> query, Message msg) {
        String key = query.get("key");
        if (StrUtil.isBlank(key)) {
            msg.setMessage("直播间标识不能为空！");
            return false;
        }
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("直播间标识[" + key + "]不存在！");
            return false;
        }
        return true;
    }

    public static boolean checkCookie(HttpExchange httpExchange) {
        Map<String, String> cookies = getRequestCookies(httpExchange);
        String token = cookies.get("_token");
        if (token == null) return false;
        if (!JWTUtil.checkToken(token)) return false;
        String sid = getRequestCookie(httpExchange, "_sid", "");
        String cid = getRequestCookie(httpExchange, "_cid", "cid");
        return sid.equals(JWTUtil.getValeStr(token, cid));
    }
}
