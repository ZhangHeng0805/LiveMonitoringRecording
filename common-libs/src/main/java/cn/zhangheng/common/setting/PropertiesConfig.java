package cn.zhangheng.common.setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 01:09
 * @version: 1.0
 * @description: 配置类注解
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertiesConfig {
    /**
     * 配置文件路径
     * 优先从文件系统加载（外部配置）,否则从classpath加载（内部配置）
     *
     * @return
     */
    String path();

    /**
     * 字符集设置，默认UTF-8
     *
     * @return
     */
    String charset() default "UTF-8";

    /**
     * 通过-D参数名指定配置路径
     * -D{paramPath}=
     * 例：java -Dconfig.path=/path/config.properties -jar demo.jar
     *
     * @return
     */
    String paramPath() default "config.path";

    /**
     * 通过-D参数名指定文件字符集
     * -D{paramCharset}=
     * 例：java -Dconfig.charset=GBK -jar demo.jar
     *
     * @return
     */
    String paramCharset() default "config.charset";
}