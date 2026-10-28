package cn.zhangheng.common.jmx;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/26 星期一 18:13
 * @version: 1.0
 * @description:
 */

import javax.management.*;
import javax.management.openmbean.OpenDataException;
import java.lang.reflect.Method;
import java.util.*;

import static cn.zhangheng.common.jmx.JmxOpenTypeUtil.isSimpleType;

public class AnnotationBasedDynamicMBean implements DynamicMBean {
    private final Object target;
    private final MBeanInfo mBeanInfo;
    private final Map<String, Method> getterMap = new HashMap<>();
    private final Map<String, Method> setterMap = new HashMap<>();
    private final Map<String, JmxAttribute> metaMap = new HashMap<>();
    private final Map<String, Method> opMethodMap = new HashMap<>();
    private final Map<String, JmxOperation> opAnnoMap = new HashMap<>();

    public AnnotationBasedDynamicMBean(Object target) {
        this.target = target;
        Class<?> clazz = target.getClass();
        JmxOpenTypeUtil.ClassMeta meta = JmxOpenTypeUtil.getClassMeta(clazz);

        // 加载属性
        for (JmxOpenTypeUtil.FieldInfo fi : meta.fieldMap.values()) {
            String attrName = fi.attrName;
            Method getter = fi.getter;
            Method setter = fi.anno.readOnly() ? null : findSetter(clazz, fi.fieldType, fi.attrName);
            getterMap.put(attrName, getter);
            if (setter != null) setterMap.put(attrName, setter);
            metaMap.put(attrName, fi.anno);
        }

        // 加载操作
        List<MBeanOperationInfo> opInfos = new ArrayList<>();
        for (JmxOpenTypeUtil.OperationInfo oi : meta.opList) {
            String opName = oi.opName;
            Method m = oi.method;
            opMethodMap.put(opName, m);
            opAnnoMap.put(opName, oi.anno);
            MBeanParameterInfo[] params = new MBeanParameterInfo[m.getParameterCount()];
            String[] paramDescArr = oi.anno.paramDesc();
            for (int i = 0; i < params.length; i++) {
                String pDesc = i < paramDescArr.length ? paramDescArr[i] : "";
                params[i] = new MBeanParameterInfo("p" + i, m.getParameterTypes()[i].getName(), pDesc);
            }
            opInfos.add(new MBeanOperationInfo(
                    opName,
                    oi.anno.desc(),
                    params,
                    m.getReturnType().getName(),
                    MBeanOperationInfo.UNKNOWN
            ));
        }

        // 属性元信息
        List<MBeanAttributeInfo> attrInfos = new ArrayList<>();
        for (Map.Entry<String, JmxAttribute> entry : metaMap.entrySet()) {
            String name = entry.getKey();
            JmxAttribute jmxAttr = entry.getValue();
            Method getter = getterMap.get(name);
            boolean writable = setterMap.containsKey(name);
            // 构造MBeanAttributeInfo的时候，判断一下，给更好看的类型名
            String typeName;
            if (List.class.isAssignableFrom(getter.getReturnType())) {
                typeName = "TabularData";
            } else if (Map.class.isAssignableFrom(getter.getReturnType())) {
                typeName = "TabularData";
            } else if (!isSimpleType(getter.getReturnType())) {
                typeName = "CompositeData";
            } else {
                typeName = getter.getReturnType().getName();
            }
            attrInfos.add(new MBeanAttributeInfo(
                    name,
                    typeName,
                    jmxAttr.desc(),
                    true,
                    writable,
                    false
            ));
        }

        this.mBeanInfo = new MBeanInfo(
                clazz.getName(),
                "Annotation JMX MBean (parent + operation)",
                attrInfos.toArray(new MBeanAttributeInfo[0]),
                null,
                opInfos.toArray(new MBeanOperationInfo[0]),
                null
        );
    }

    private static Method findSetter(Class<?> clazz, Class<?> fieldType, String attrName) {
        String setterName = "set" + capitalize(attrName);
        try {
            return clazz.getMethod(setterName, fieldType);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        Method getter = getterMap.get(attribute);
        if (getter == null) throw new AttributeNotFoundException(attribute);
        try {
            Object raw = getter.invoke(target);
            JmxAttribute anno = metaMap.get(attribute);
            return JmxOpenTypeUtil.toOpenData(raw, new HashSet<>(), anno.tabularIndexKeys());
        } catch (OpenDataException e) {
            throw new ReflectionException(e);
        } catch (Exception e) {
            throw new ReflectionException(e);
        }
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        String name = attribute.getName();
        Method setter = setterMap.get(name);
        if (setter == null) {
            throw new InvalidAttributeValueException("read-only or no setter: " + name);
        }
        try {
            setter.invoke(target, attribute.getValue());
        } catch (Exception e) {
            throw new ReflectionException(e);
        }
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        for (String n : attributes) {
            try {
                list.add(new Attribute(n, getAttribute(n)));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList res = new AttributeList();
        for (Attribute attr : attributes.asList()) {
            try {
                setAttribute(attr);
                res.add(attr);
            } catch (Exception ignored) {
            }
        }
        return res;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        Method opMethod = opMethodMap.get(actionName);
        if (opMethod == null) {
            throw new ReflectionException(new NoSuchMethodException("operation not found: " + actionName));
        }
        try {
            return opMethod.invoke(target, params);
        } catch (Exception e) {
            throw new ReflectionException(e);
        }
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return mBeanInfo;
    }
}
