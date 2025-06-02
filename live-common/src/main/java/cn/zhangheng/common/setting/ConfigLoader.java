package cn.zhangheng.common.setting;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/03 星期二 01:10
 * @version: 1.0
 * @description: 加载配置
 */

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Properties;

public class ConfigLoader {

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
                if (propertyValue != null) {
                    String propertyKey = propertyValue.value();
                    String propertyValueStr = properties.getProperty(propertyKey);

                    if (propertyValueStr != null) {
                        field.setAccessible(true);
                        // 根据字段类型进行转换
                        Class<?> fieldType = field.getType();
                        if (fieldType == String.class) {
                            field.set(obj, propertyValueStr);
                        } else if (fieldType == int.class || fieldType == Integer.class) {
                            field.set(obj, Integer.valueOf(propertyValueStr));
                        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                            field.set(obj, Boolean.valueOf(propertyValueStr));
                        } else if (fieldType == long.class || fieldType == Long.class) {
                            field.set(obj, Long.valueOf(propertyValueStr));
                        } else if (fieldType == double.class || fieldType == Double.class) {
                            field.set(obj, Double.valueOf(propertyValueStr));
                        } else if (fieldType == float.class || fieldType == Float.class) {
                            field.set(obj, Float.valueOf(propertyValueStr));
                        } else if (fieldType == char.class || fieldType == Character.class) {
                            field.set(obj, propertyValueStr.charAt(0));
                        }
                        // 可以扩展更多类型...
                    }

                }
            }
        } catch (IOException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
