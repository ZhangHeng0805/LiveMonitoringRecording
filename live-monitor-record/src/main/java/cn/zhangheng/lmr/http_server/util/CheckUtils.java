package cn.zhangheng.lmr.http_server.util;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.httpServer.util.JWTUtil;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.bean.RoomFileModel;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;

import java.util.Map;

import static cn.zhangheng.common.httpServer.util.HandlerUtils.getRequestCookie;
import static cn.zhangheng.common.httpServer.util.HandlerUtils.getRequestCookies;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:30
 * @version: 1.0
 * @description:
 */
public class CheckUtils {
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
