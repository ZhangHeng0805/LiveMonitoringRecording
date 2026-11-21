package cn.zhangheng.lmr.http_server.util;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.record.bean.Constant;
import cn.zhangheng.record.bean.Setting;
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
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:30
 * @version: 1.0
 * @description:
 */
public class CheckUtils {

    /**
     * 请求鉴权结果标记（挂在 HttpExchange 上，供后续处理判断当前请求是否已通过秘钥校验）
     */
    private static final String AUTH_ATTR = "lmr.auth";

    /**
     * 统一请求鉴权（本地同源 + 远程跨站都能用）：
     * 1. Authorization: Bearer &lt;接口令牌&gt;（跨站推荐，页面托管在 GitHub Pages 时用这个）
     * 2. URL 参数 token=&lt;接口令牌&gt;（img/video/a 之类无法带头部的请求）
     * 3. Cookie 会话（同源页面直接访问后端时沿用，保持兼容）
     */
    public static boolean checkRequestAuth(HttpExchange httpExchange) {
        String token = getBearerToken(httpExchange);
        if (StrUtil.isBlank(token)) {
            String query = httpExchange.getRequestURI().getQuery();
            if (StrUtil.isNotBlank(query)) {
                token = parseQuery(query, java.nio.charset.StandardCharsets.UTF_8).get("token");
            }
        }
        if (StrUtil.isNotBlank(token) && JWTUtil.checkApiToken(token)) {
            httpExchange.setAttribute(AUTH_ATTR, Boolean.TRUE);
            return true;
        }
        // 兜底：同源 Cookie 会话
        boolean ok = checkCookie(httpExchange);
        if (ok) {
            httpExchange.setAttribute(AUTH_ATTR, Boolean.TRUE);
        }
        return ok;
    }

    /**
     * 只读接口鉴权：带令牌直接放行；不带令牌时，若开启 server.auth.publicRead 则匿名可读（返回体需脱敏）
     */
    public static boolean checkReadAuth(HttpExchange httpExchange) {
        if (checkRequestAuth(httpExchange)) {
            return true;
        }
        return ConfigKit.get(Setting.class).isAuthPublicRead();
    }

    /**
     * 当前请求是否已通过秘钥/令牌校验（匿名只读时为 false，用于决定是否脱敏）
     */
    public static boolean isAuthenticated(HttpExchange httpExchange) {
        return Boolean.TRUE.equals(httpExchange.getAttribute(AUTH_ATTR));
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
