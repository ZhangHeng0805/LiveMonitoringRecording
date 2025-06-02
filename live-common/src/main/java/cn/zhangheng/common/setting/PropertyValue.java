package cn.zhangheng.common.setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 01:09
 * @version: 1.0
 * @description: 配置属性注解
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyValue {
    /**
     * 映射配置文件中的key
     *
     * @return
     */
    String value(); //
}
