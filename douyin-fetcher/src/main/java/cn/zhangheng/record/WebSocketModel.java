package cn.zhangheng.record;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/05/13 星期三 14:44
 * @version: 1.0
 * @description:
 */
@Data
public class WebSocketModel {
    private String url;
    private String cookie;//抖音限制，同一个cookie账号只能进入一个直播间
    private String userAgent;

    public boolean isUse() {
        return url != null &&
                cookie != null &&
                userAgent != null;
    }
}
