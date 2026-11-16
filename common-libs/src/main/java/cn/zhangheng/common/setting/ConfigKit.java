package cn.zhangheng.common.setting;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/16 星期一 18:41
 * @version: 1.0
 * @description:
 */


public class ConfigKit {
    private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz) {
        return get(clazz, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz, boolean refresh) {
        T obj = (T) CACHE.get(clazz);
        if (obj == null) {
            synchronized (clazz) {
                obj = (T) CACHE.get(clazz);
                if (obj == null) {
                    try {
                        // 拿到私有构造
                        Constructor<T> constructor = clazz.getDeclaredConstructor();
                        // 关键：放开访问权限
                        constructor.setAccessible(true);
                        obj = constructor.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("配置类实例化失败 " + clazz.getName(), e);
                    }
                    CACHE.put(clazz, obj);
                    ConfigLoader.load(obj);
                }
            }
        }
        if (refresh) {
            ConfigLoader.load(obj);
        }
        return obj;
    }

    public static <T> void refresh(Class<T> clazz) {
        get(clazz, true);
    }

    public static <T> void remove(Class<T> clazz) {
        CACHE.remove(clazz);
    }

    public static void clearAll() {
        CACHE.clear();
    }
}