package cn.zhangheng.common.util;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/24 星期三 12:42
 * @version: 1.0
 * @description:
 */
import java.lang.reflect.Field;

public class ObjectPropertyUpdater {

    /**
     * 比较源对象和目标对象的非空属性，将源对象中与目标对象不同的非空属性值替换到目标对象
     * @param source 源对象，提供要替换的属性值
     * @param target 目标对象，其属性值将被源对象的非空属性值替换（如果不同）
     * @param <T> 对象类型
     * @throws IllegalAccessException 如果无法访问对象的属性
     */
    public static <T> void updateDifferentProperties(T source, T target) throws IllegalAccessException {
        // 检查两个对象是否为同一类型
        if (source == null || target == null) {
            throw new IllegalArgumentException("源对象和目标对象不能为null");
        }

        if (source.getClass() != target.getClass()) {
            throw new IllegalArgumentException("两个对象必须是同一类型");
        }

        Class<?> clazz = source.getClass();
        // 获取所有属性（包括私有属性）
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            // 设置可以访问私有属性
            field.setAccessible(true);

            // 获取源对象的属性值
            Object sourceValue = field.get(source);

            // 只处理源对象中非空的属性
            if (sourceValue != null) {
                // 获取目标对象的属性值
                Object targetValue = field.get(target);

                // 比较两个属性值是否不同
                if (!equals(sourceValue, targetValue)) {
                    // 如果不同，将源对象的属性值设置到目标对象
                    field.set(target, sourceValue);
                }
            }
        }
    }

    /**
     * 安全地比较两个对象是否相等，处理null的情况
     */
    private static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
}

