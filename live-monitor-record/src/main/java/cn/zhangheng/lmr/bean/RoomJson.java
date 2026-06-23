package cn.zhangheng.lmr.bean;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Room;
import com.zhangheng.file.FileUtil;
import lombok.Data;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/23 星期二 05:47
 * @version: 1.0
 * @description:
 */
@Data
public class RoomJson {
    private String id;
    private String name;
    private Room.Platform platform;
    private boolean isAutoRecord;
    private boolean isEnable = true;
    private Setting setting;

    @Data
    public static class Setting {
        private int delayIntervalSec;
        private boolean isLoop;
        private boolean openSubtitle;
        private boolean convertFlvToMp4;
        private String cookie;
        private String xiZhiUrl;
    }

    public void desensitize() {
        String xiZhiUrl = setting.getXiZhiUrl();
        if (xiZhiUrl != null && xiZhiUrl.length() > 50) {
            setting.setXiZhiUrl(StrUtil.replace(xiZhiUrl, 25, 50, "***"));
        }
        String cookie = setting.getCookie();
        if (cookie != null && cookie.length() > 50) {
            setting.setCookie(cookie.substring(0, 50) + "......");
        }
    }

    public void check() {
        if ("null".equalsIgnoreCase(setting.getCookie())) {
            setting.setCookie("");
        }
        if ("null".equalsIgnoreCase(setting.getXiZhiUrl())) {
            setting.setXiZhiUrl("");
        }
    }

    public cn.zhangheng.common.bean.Setting convert(cn.zhangheng.common.bean.Setting setting) {
        // 核心配置：忽略源对象中的 null 值
        CopyOptions options = CopyOptions.create()
                .setIgnoreNullValue(true) // 源对象为 null 的属性不进行拷贝
                .setIgnoreError(true);    // 忽略字段注入时的错误
        // 将 oldObj 中不为 null 的属性合并到 newObj 中
        BeanUtil.copyProperties(this.setting, setting, options);
        String xiZhiUrl = this.setting.xiZhiUrl;
        if ("null".equalsIgnoreCase(xiZhiUrl)) {
            xiZhiUrl = null;
            setting.setXiZhiUrl(xiZhiUrl);
        }
        String cookie = this.setting.cookie;
        if (cookie != null) {
            if ("null".equalsIgnoreCase(cookie)) {
                cookie = null;
            }
            BeanUtil.setProperty(setting, "cookie" + this.platform.name(), cookie);
        }
        return setting;
    }
}
