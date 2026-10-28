package cn.zhangheng.common.jmx;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JmxOperation {
    /** 操作名称，默认方法名 */
    String name() default "";
    /** 操作描述 */
    String desc() default "";
    /** 参数描述，顺序和方法参数一一对应 */
    String[] paramDesc() default {};
}