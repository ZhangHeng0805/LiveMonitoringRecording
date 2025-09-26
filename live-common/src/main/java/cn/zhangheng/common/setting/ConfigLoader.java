package cn.zhangheng.common.setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 01:10
 * @version: 1.0
 * @description: 加载配置
 */

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

@Slf4j
public class ConfigLoader {

    // 类型转换器映射：键为目标类型，值为字符串到该类型的转换器
    private static final Map<Class<?>, Function<String, Object>> TYPE_CONVERTERS;

    static {
        // 初始化转换器映射（策略注册）
        TYPE_CONVERTERS = new HashMap<>();

        // 字符串类型
        TYPE_CONVERTERS.put(String.class, str -> str);

        // 整数类型（包括基本类型和包装类）
        TYPE_CONVERTERS.put(int.class, Integer::valueOf);
        TYPE_CONVERTERS.put(Integer.class, Integer::valueOf);

        // 布尔类型
        TYPE_CONVERTERS.put(boolean.class, Boolean::valueOf);
        TYPE_CONVERTERS.put(Boolean.class, Boolean::valueOf);

        // 长整数类型
        TYPE_CONVERTERS.put(long.class, Long::valueOf);
        TYPE_CONVERTERS.put(Long.class, Long::valueOf);

        // 双精度类型
        TYPE_CONVERTERS.put(double.class, Double::valueOf);
        TYPE_CONVERTERS.put(Double.class, Double::valueOf);

        // 单精度类型
        TYPE_CONVERTERS.put(float.class, Float::valueOf);
        TYPE_CONVERTERS.put(Float.class, Float::valueOf);

        // 字符类型
        TYPE_CONVERTERS.put(char.class, str -> str.charAt(0));
        TYPE_CONVERTERS.put(Character.class, str -> str.charAt(0));
    }

    public static void load(Object obj) {
        Class<?> clazz = obj.getClass();
        PropertiesConfig configAnnotation = clazz.getAnnotation(PropertiesConfig.class);
        if (configAnnotation == null) {
            return;
        }
        //配置文件路径
        String configPath = System.getProperty(configAnnotation.paramPath(), configAnnotation.path());
        //配置文件字符集
        Charset charset = Charset.forName(
                System.getProperty(configAnnotation.paramCharset(), configAnnotation.charset())
        );
        Properties properties = new Properties();
        try {
            // 优先从文件系统加载（外部配置）
            File externalFile = new File(configPath);
            if (externalFile.exists() && externalFile.isFile()) {
                try (Reader reader = new InputStreamReader(
                        Files.newInputStream(externalFile.toPath()),
                        charset
                )) {
                    properties.load(reader);
                }
            } else {
                // 否则从classpath加载（内部配置）
                try (InputStream is = clazz.getClassLoader().getResourceAsStream(configPath)) {
                    if (is == null) {
                        throw new FileNotFoundException("配置文件不存在: " + configPath);
                    }
                    try (Reader reader = new InputStreamReader(
                            is,
                            charset
                    )) {
                        properties.load(reader);
                    }
                }
            }
            // 遍历类的所有字段
            for (Field field : clazz.getDeclaredFields()) {
                PropertyValue propertyValue = field.getAnnotation(PropertyValue.class);
                if (propertyValue == null) {
                    continue; // 无注解则跳过
                }
                String propertyKey = propertyValue.value();
                String propertyValueStr = properties.getProperty(propertyKey);
                if (propertyValueStr == null || propertyValueStr.trim().isEmpty()) {
                    continue; // 配置值为空则跳过
                }

                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();
                    Object convertedValue = convertValue(field, fieldType, propertyValueStr);
                    field.set(obj, convertedValue);
                } catch (Exception e) {
                    // 包装异常信息，方便定位问题
                    log.error(
                            String.format("配置注入属性失败：字段[%s]，配置键[%s]，值[%s]",
                                    field.getName(), propertyKey, propertyValueStr),
                            e
                    );
                }
            }
        } catch (IOException e) {
            log.error("配置文件" + configPath + "解析失败:" + e.getMessage(), e);
        }
    }

    /**
     * 转换配置值为字段所需类型
     */
    private static Object convertValue(Field field, Class<?> fieldType, String valueStr) {
        // 处理枚举类型（单独处理，因为枚举是动态类型）
        if (fieldType.isEnum()) {
            return convertEnum(fieldType, valueStr);
        }

        // 从转换器映射中获取对应转换器
        Function<String, Object> converter = TYPE_CONVERTERS.get(fieldType);
        if (converter != null) {
            return converter.apply(valueStr);
        }

        // 未支持的类型
        throw new UnsupportedOperationException(
                String.format("不支持的字段类型：%s（字段名：%s）",
                        fieldType.getName(), field.getName())
        );
    }

    /**
     * 转换字符串为枚举值
     */
    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E convertEnum(Class<?> enumType, String valueStr) {
        try {
            return Enum.valueOf((Class<E>) enumType, valueStr);
        } catch (IllegalArgumentException e) {
            // 收集枚举所有有效值，增强错误信息
            StringBuilder validValues = new StringBuilder();
            for (Enum<?> enumConstant : (Enum<?>[]) enumType.getEnumConstants()) {
                validValues.append(enumConstant.name()).append(", ");
            }
            throw new IllegalArgumentException(
                    String.format("枚举值无效：%s（有效值：%s）",
                            valueStr, validValues.substring(0, validValues.length() - 2)), // 移除最后一个逗号
                    e
            );
        }
    }
}
