package cn.zhangheng.cpolar;

import cn.zhangheng.common.jmx.JmxAttribute;
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
public class CpolarSetting {

    private CpolarSetting() {
    }

    @JmxAttribute(desc = "程序路径")
    @PropertyValue(value = "execute program.path")
    private String exePath = "./bin/cpolar.exe";


    @JmxAttribute(desc = "Git项目根路径")
    @PropertyValue(value = "git.repository.dir", required = true)
    private String gitRepoDir;

    @JmxAttribute(desc = "Git文件相对路径")
    @PropertyValue(value = "git.file.relative.path", required = true)
    private String gitFileRelativePath;
}
