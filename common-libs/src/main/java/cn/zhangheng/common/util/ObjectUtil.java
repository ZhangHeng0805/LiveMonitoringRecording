package cn.zhangheng.common.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/11 星期日 15:11
 * @version: 1.0
 * @description:
 */
public class ObjectUtil {
    /**
     * newValue不为null才赋值给target
     * @param target 目标字段引用
     * @param newValue 新值
     */
    public static <T> void setIfNotNull(Consumer<T> target, T newValue) {
        if (newValue != null) {
            target.accept(newValue);
        }
    }
    /**
     * 仅当目标对象原有值为null时，才设置新值
     * @param getter 获取原有值
     * @param setter 设置方法
     * @param newValue 待设置的新值
     * @param <T> 类型
     */
    public static <T> void setOnlyIfOldNull(Supplier<T> getter, Consumer<T> setter, T newValue) {
        T oldVal = getter.get();
        if (oldVal == null) {
            setter.accept(newValue);
        }
    }
}
