package cn.zhangheng.cpolar;

import cn.zhangheng.common.setting.ConfigLoader;
import cn.zhangheng.common.setting.PropertiesConfig;
import cn.zhangheng.common.setting.PropertyValue;
import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/16 星期三 21:40
 * @version: 1.0
 * @description:
 */
@Data
@PropertiesConfig(path = "cpolar.setting")
public class Setting {
    private static volatile Setting setting;

    private Setting() {

    }

    private void loadConfig() {
        //自动加载配置
        ConfigLoader.load(this);
    }

    public static Setting getInstance() {
        return getInstance(false);
    }

    public static synchronized Setting getInstance(boolean isRefresh) {
        // 第一次创建实例
        if (setting == null) {
            setting = new Setting();
            setting.loadConfig();
        }
        // 复用对象重新加载配置（不新建实例，避免多对象）
        if (isRefresh) {
            setting.loadConfig();
        }
        return setting;
    }

    @PropertyValue(value = "execute program.path")
    private String exePath = "./bin/cpolar.exe";


    @PropertyValue(value = "git.repository.dir", required = true)
    private String gitRepoDir;

    @PropertyValue(value = "git.file.relative.path", required = true)
    private String gitFileRelativePath;
}
