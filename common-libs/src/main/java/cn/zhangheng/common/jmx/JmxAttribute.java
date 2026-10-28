package cn.zhangheng.common.jmx;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JmxAttribute {
    String name() default "";
    String desc() default "";
    boolean readOnly() default true;
    /**
     * 当字段是List / Map，转TabularData时，指定索引key列表
     * 示例：tabularIndexKeys = {"id"}
     */
    String[] tabularIndexKeys() default {};
}