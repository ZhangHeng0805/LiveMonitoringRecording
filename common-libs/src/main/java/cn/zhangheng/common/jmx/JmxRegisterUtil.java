package cn.zhangheng.common.jmx;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/26 星期一 18:16
 * @version: 1.0
 * @description:
 */
import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Objects;

public class JmxRegisterUtil {
    private static final MBeanServer MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

    /**
     * 注册对象，默认 domain=monitor
     * @param bean 普通对象（带@JmxAttribute / @JmxOperation + Lombok @Data）
     * @param type MBean的type标识
     * @param keys 附加key，格式 k1=v1,k2=v2
     * @return ObjectName
     * @throws MalformedObjectNameException
     */
    public static ObjectName register(Object bean, String type, String... keys) throws MalformedObjectNameException {
        return register("cn.zhangheng.jmx", bean, type, keys);
    }

    /**
     * 注册对象，指定domain
     * @param domain 域名，如 monitor、app
     * @param bean 业务对象
     * @param type type值
     * @param keys 附加键值对
     * @return ObjectName
     * @throws MalformedObjectNameException
     */
    public static ObjectName register(String domain, Object bean, String type, String... keys) throws MalformedObjectNameException {
        Objects.requireNonNull(bean);
        Objects.requireNonNull(domain);
        Objects.requireNonNull(type);

        StringBuilder sb = new StringBuilder();
        sb.append(domain).append(":type=").append(type);
        for (int i = 0; i < keys.length; i += 2) {
            if (i + 1 >= keys.length) break;
            String k = keys[i];
            String v = keys[i + 1];
            sb.append(",").append(k).append("=").append(v);
        }
        ObjectName objectName = new ObjectName(sb.toString());
        return register(bean, objectName);
    }

    /**
     * 注册，指定完整ObjectName
     * @param bean 业务对象
     * @param objectName 目标ObjectName
     * @return objectName
     */
    public static ObjectName register(Object bean, ObjectName objectName) {
        Objects.requireNonNull(bean);
        Objects.requireNonNull(objectName);
        try {
            if (MBEAN_SERVER.isRegistered(objectName)) {
                // 幂等：已存在则先注销旧的
                MBEAN_SERVER.unregisterMBean(objectName);
            }
            DynamicMBean dynamicMBean = new AnnotationBasedDynamicMBean(bean);
            MBEAN_SERVER.registerMBean(dynamicMBean, objectName);
            return objectName;
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | InstanceNotFoundException e) {
            throw new RuntimeException("JMX register failed, objectName:" + objectName, e);
        }
    }

    /**
     * 注销指定ObjectName
     */
    public static void unregister(ObjectName objectName) {
        if (objectName == null) return;
        try {
            if (MBEAN_SERVER.isRegistered(objectName)) {
                MBEAN_SERVER.unregisterMBean(objectName);
            }
        } catch (InstanceNotFoundException | MBeanRegistrationException e) {
            // 不存在直接忽略
        }
    }

    /**
     * 按domain + type + keys注销
     */
    public static void unregister(String domain, String type, String... keys) throws MalformedObjectNameException {
        StringBuilder sb = new StringBuilder();
        sb.append(domain).append(":type=").append(type);
        for (int i = 0; i < keys.length; i += 2) {
            if (i + 1 >= keys.length) break;
            String k = keys[i];
            String v = keys[i + 1];
            sb.append(",").append(k).append("=").append(v);
        }
        ObjectName on = new ObjectName(sb.toString());
        unregister(on);
    }

    /**
     * 注销指定domain下所有MBean
     */
    public static void unregisterAll(String domain) throws MalformedObjectNameException {
        ObjectName pattern = new ObjectName(domain + ":*");
        for (ObjectName on : MBEAN_SERVER.queryNames(pattern, null)) {
            unregister(on);
        }
    }
}
