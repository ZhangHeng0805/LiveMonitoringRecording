package cn.zhangheng.lmr.http_server.util;

import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.common.setting.PropertyValue;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.router.RateLimitResolver;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本项目默认的限流阈值解析器：把 {@code @RateLimit(configKey = "xxx")} 的 key
 * 当作 {@link Setting} 里 {@link PropertyValue#value()} 的配置项名去读值。
 * <p>
 * 好处是注解只写配置项名、不写死类和方法，ConfigLoader 增删字段后注解照旧生效；
 * 配置缺失或读失败时回退到注解上的 {@code limit} 兜底值。
 *
 * @author: ZhangHeng
 */
@Slf4j
public final class RateLimitResolvers {

    /** 配置项名 -> Setting 字段，懒加载缓存 */
    private static final Map<String, Field> PROPERTY_FIELDS = new ConcurrentHashMap<>();
    private static volatile boolean indexed = false;

    private RateLimitResolvers() {
    }

    public static RateLimitResolver defaults() {
        return RateLimitResolvers::readIntConfig;
    }

    private static int readIntConfig(String configKey, int fallback) {
        if (configKey == null || configKey.isEmpty()) {
            return fallback;
        }
        try {
            Field field = findField(configKey);
            if (field == null) {
                log.warn("限流配置项 {} 在 Setting 中不存在，使用兜底阈值 {}", configKey, fallback);
                return fallback;
            }
            Object setting = ConfigKit.get(Setting.class);
            Object value = field.get(setting);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            log.warn("限流配置项 {} 不是数字类型，使用兜底阈值 {}", configKey, fallback);
        } catch (Exception e) {
            log.warn("读取限流配置项 {} 失败，使用兜底阈值 {}：{}", configKey, fallback, e.getMessage());
        }
        return fallback;
    }

    /**
     * 按 {@link PropertyValue#value()} 找字段，首次调用时建立全表索引
     */
    private static Field findField(String configKey) {
        if (!indexed) {
            synchronized (RateLimitResolvers.class) {
                if (!indexed) {
                    for (Field field : Setting.class.getDeclaredFields()) {
                        PropertyValue pv = field.getAnnotation(PropertyValue.class);
                        if (pv == null || pv.value() == null || pv.value().isEmpty()) {
                            continue;
                        }
                        field.setAccessible(true);
                        PROPERTY_FIELDS.put(pv.value(), field);
                    }
                    indexed = true;
                }
            }
        }
        return PROPERTY_FIELDS.get(configKey);
    }
}
