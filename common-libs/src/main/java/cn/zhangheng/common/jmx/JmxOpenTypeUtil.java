package cn.zhangheng.common.jmx;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/26 星期一 18:08
 * @version: 1.0
 * @description:
 */

import com.zhangheng.util.TimeUtil;

import javax.management.openmbean.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class JmxOpenTypeUtil {

    private static final ConcurrentHashMap<Class<?>, ClassMeta> META_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Function<Object, String>> TYPE_CONVERTERS;

    static {
        // 初始化转换器映射（策略注册）
        TYPE_CONVERTERS = new HashMap<>();
        //字段类型转换，指定字段类型展示
        TYPE_CONVERTERS.put(Date.class, obj -> TimeUtil.toTime((Date) obj));
    }

    static class ClassMeta {
        final Map<String, FieldInfo> fieldMap = new LinkedHashMap<>();
        final List<OperationInfo> opList = new ArrayList<>();
    }

    static class FieldInfo {
        String attrName;
        JmxAttribute anno;
        Method getter;
        Class<?> fieldType;
        Type genericType;
    }

    static class OperationInfo {
        String opName;
        JmxOperation anno;
        Method method;
    }

    /**
     * 转为OpenData：基础类型 / POJO CompositeData / List TabularData / Map TabularData
     */
    public static Object toOpenData(Object obj, Set<Object> visiting, String[] tabularIndexKeys) throws OpenDataException {
        if (obj == null) return null;
        if (visiting.contains(obj)) {
            return "[CircularRef:" + obj.getClass().getSimpleName() + "]";
        }
        visiting.add(obj);
        try {
            Class<?> clazz = obj.getClass();
            if (isSimpleType(clazz)) {
                return obj;
            }
            if (clazz.isEnum()) {
                return obj.toString();
            }
            Function<Object, String> converter = TYPE_CONVERTERS.get(clazz);
            if (converter != null) {
                return converter.apply(obj);
            }
            if (List.class.isAssignableFrom(clazz)) {
                return listToTabularData((List<?>) obj, visiting, tabularIndexKeys);
            }
            if (Map.class.isAssignableFrom(clazz)) {
                return mapToTabularData((Map<?, ?>) obj, visiting, tabularIndexKeys);
            }
            return pojoToCompositeData(obj, visiting);
        } finally {
            visiting.remove(obj);
        }
    }

    public static boolean isSimpleType(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return true;
        }
        return type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type);
    }

    private static CompositeData pojoToCompositeData(Object bean, Set<Object> visiting) throws OpenDataException {
        Class<?> clazz = bean.getClass();
        ClassMeta meta = getClassMeta(clazz);
        if (meta.fieldMap.isEmpty()) return null;

        List<String> names = new ArrayList<>();
        List<String> descs = new ArrayList<>();
        List<OpenType<?>> types = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (FieldInfo fi : meta.fieldMap.values()) {
            Object rawVal;
            try {
                rawVal = fi.getter.invoke(bean);
            } catch (Exception e) {
                rawVal = null;
            }
            Object openVal = toOpenData(rawVal, visiting, fi.anno.tabularIndexKeys());
            OpenType<?> openType = inferOpenType(fi, rawVal);

            String attrName = fi.attrName;
            String desc = fi.anno.desc();
            String showName = desc.isEmpty() ? attrName : attrName + "(" + desc + ")";
            names.add(showName);
            descs.add(desc);
            types.add(openType);
            values.add(openVal);
        }

        CompositeType ct = new CompositeType(
                clazz.getName(),
                clazz.getSimpleName(),
                names.toArray(new String[0]),
                descs.toArray(new String[0]),
                types.toArray(new OpenType[0])
        );
        return new CompositeDataSupport(ct, names.toArray(new String[0]), values.toArray());
    }

    private static TabularData listToTabularData(List<?> list, Set<Object> visiting, String[] indexKeys) throws OpenDataException {
        if (list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        // 首元素为null，降级按字符串简单类型处理
        Class<?> itemCls;
        if (first == null) {
            itemCls = String.class;
        } else {
            itemCls = first.getClass();
        }

        // ========== 简单类型分支：String / 数字 / boolean 等，两列表格 index, itemValue ==========
        if (isSimpleType(itemCls)) {
            SimpleType<?> valueType = simpleToOpenType(itemCls);
            CompositeType rowType = new CompositeType(
                    "SimpleListItem",
                    "Simple list item",
                    new String[]{"index", "itemValue"},
                    new String[]{"Index", "Value"},
                    new OpenType[]{SimpleType.INTEGER, valueType}
            );
            // 列表默认用index作为主键
            String[] idx = indexKeys != null && indexKeys.length > 0 ? indexKeys : new String[]{"index"};
            TabularType tabType = new TabularType(
                    "SimpleList",
                    "Simple list",
                    rowType,
                    idx
            );
            TabularDataSupport tabData = new TabularDataSupport(tabType);
            for (int i = 0; i < list.size(); i++) {
                Object val = list.get(i);
                tabData.put(new CompositeDataSupport(rowType,
                        new String[]{"index", "itemValue"},
                        new Object[]{i, val}
                ));
            }
            return tabData;
        }

        // ========== POJO对象分支：元素是带@JmxAttribute的实体类（原有逻辑） ==========
        ClassMeta itemMeta = getClassMeta(itemCls);
        if (itemMeta.fieldMap.isEmpty()) {
            return null;
        }

        List<String> itemNames = new ArrayList<>();
        List<String> itemDescs = new ArrayList<>();
        List<OpenType<?>> itemTypes = new ArrayList<>();
        for (FieldInfo fi : itemMeta.fieldMap.values()) {
            itemNames.add(fi.attrName);
            itemDescs.add(fi.anno.desc());
            itemTypes.add(inferOpenType(fi, null));
        }
        CompositeType rowType = new CompositeType(
                itemCls.getName(),
                itemCls.getSimpleName(),
                itemNames.toArray(new String[0]),
                itemDescs.toArray(new String[0]),
                itemTypes.toArray(new OpenType[0])
        );
        // 没有指定索引key，默认取第一个字段
        String[] idx = indexKeys != null && indexKeys.length > 0 ? indexKeys : new String[]{itemNames.get(0)};
        TabularType tabType = new TabularType(
                "List<" + itemCls.getSimpleName() + ">",
                "List of " + itemCls.getSimpleName(),
                rowType,
                idx
        );
        TabularDataSupport tabData = new TabularDataSupport(tabType);
        for (Object rowObj : list) {
            CompositeData rowCd = (CompositeData) toOpenData(rowObj, visiting, indexKeys);
            tabData.put(rowCd);
        }
        return tabData;
    }

    private static TabularData mapToTabularData(Map<?, ?> map, Set<Object> visiting, String[] indexKeys) throws OpenDataException {
        if (map.isEmpty()) {
            return null;
        }
        Map.Entry<?, ?> firstEntry = map.entrySet().iterator().next();
        Object firstVal = firstEntry.getValue();
        // 如果第一个value是null，我们降级统一按字符串处理，不再直接return null
        Class<?> itemCls;
        if (firstVal == null) {
            itemCls = String.class;
        } else {
            itemCls = firstVal.getClass();
        }

        // ========== 简单类型分支：String / 数字 / boolean 等，两列表格 mapKey, mapValue ==========
        if (isSimpleType(itemCls)) {
            SimpleType<?> valueType = simpleToOpenType(itemCls);
            CompositeType rowType = new CompositeType(
                    "SimpleMapEntry",
                    "Simple map key-value entry",
                    new String[]{"mapKey", "mapValue"},
                    new String[]{"Key", "Value"},
                    new OpenType[]{SimpleType.STRING, valueType}
            );
            String[] idx = indexKeys != null && indexKeys.length > 0 ? indexKeys : new String[]{"mapKey"};
            TabularType tabType = new TabularType(
                    "SimpleMap",
                    "Simple key-value map",
                    rowType,
                    idx
            );
            TabularDataSupport tabData = new TabularDataSupport(tabType);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String k = String.valueOf(entry.getKey());
                Object v = entry.getValue();
                tabData.put(new CompositeDataSupport(rowType,
                        new String[]{"mapKey", "mapValue"},
                        new Object[]{k, v}
                ));
            }
            return tabData;
        }

        // ========== POJO对象分支：value是带@JmxAttribute的实体类 ==========
        ClassMeta itemMeta = getClassMeta(itemCls);
        if (itemMeta.fieldMap.isEmpty()) {
            return null;
        }

        List<String> itemNames = new ArrayList<>();
        List<String> itemDescs = new ArrayList<>();
        List<OpenType<?>> itemTypes = new ArrayList<>();
        // 附加mapKey列
        itemNames.add("mapKey");
        itemDescs.add("Map key");
        itemTypes.add(SimpleType.STRING);

        for (FieldInfo fi : itemMeta.fieldMap.values()) {
            itemNames.add(fi.attrName);
            itemDescs.add(fi.anno.desc());
            itemTypes.add(inferOpenType(fi, null));
        }
        CompositeType rowType = new CompositeType(
                "MapEntry<" + itemCls.getSimpleName() + ">",
                "Map entry row",
                itemNames.toArray(new String[0]),
                itemDescs.toArray(new String[0]),
                itemTypes.toArray(new OpenType[0])
        );
        String[] idx = indexKeys != null && indexKeys.length > 0 ? indexKeys : new String[]{"mapKey"};
        TabularType tabType = new TabularType(
                "Map<" + itemCls.getSimpleName() + ">",
                "Map values of " + itemCls.getSimpleName(),
                rowType,
                idx
        );
        TabularDataSupport tabData = new TabularDataSupport(tabType);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object val = entry.getValue();
            CompositeData innerCd = (CompositeData) toOpenData(val, visiting, indexKeys);
            Map<String, Object> rowMap = new HashMap<>();
            rowMap.put("mapKey", String.valueOf(entry.getKey()));
            for (String n : innerCd.getCompositeType().keySet()) {
                rowMap.put(n, innerCd.get(n));
            }
            tabData.put(new CompositeDataSupport(rowType, rowMap));
        }
        return tabData;
    }

    private static OpenType<?> inferOpenType(FieldInfo fi, Object val) throws OpenDataException {
        Class<?> type = fi.fieldType;
        if (isSimpleType(type)) {
            return simpleToOpenType(type);
        }
        // List / Map / POJO 全部用 STRING 占位，真实OpenType在序列化toOpenData时动态生成
        return SimpleType.STRING;
    }

    private static SimpleType<?> simpleToOpenType(Class<?> type) {
        if (type == null) {
            return SimpleType.STRING;
        }
        if (type == int.class || Integer.class == type) return SimpleType.INTEGER;
        if (type == long.class || Long.class == type) return SimpleType.LONG;
        if (type == boolean.class || Boolean.class == type) return SimpleType.BOOLEAN;
        if (type == String.class) return SimpleType.STRING;
        if (type == double.class || Double.class == type) return SimpleType.DOUBLE;
        if (type == float.class || Float.class == type) return SimpleType.FLOAT;
        if (type == byte.class || Byte.class == type) return SimpleType.BYTE;
        if (type == short.class || Short.class == type) return SimpleType.SHORT;
        if (type == char.class || Character.class == type) return SimpleType.CHARACTER;
        // 其他比如 BigDecimal、BigInteger 兜底为字符串
        return SimpleType.STRING;
    }

    /**
     * 扫描类 + 所有父类字段、@JmxOperation方法
     */
    private static ClassMeta scanClass(Class<?> clazz) {
        ClassMeta meta = new ClassMeta();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            // ========== 1. 扫描字段上的 @JmxAttribute ==========
            for (Field f : current.getDeclaredFields()) {
                JmxAttribute anno = f.getAnnotation(JmxAttribute.class);
                if (anno == null) {
                    continue;
                }
                FieldInfo fi = new FieldInfo();
                fi.anno = anno;
                fi.attrName = anno.name().isEmpty() ? f.getName() : anno.name();
                fi.fieldType = f.getType();
                fi.genericType = f.getGenericType();
                // 字段注解模式：还是去找对应的 getter
                fi.getter = findGetter(current, f);
                // 这里不再直接 continue;
                // 👉想要【没有getter也可以直接反射读field】，就注释掉下面这行
                 if (fi.getter == null) continue;
                meta.fieldMap.put(fi.attrName, fi);
            }

            // ========== 2. 扫描方法：区分 @JmxAttribute / @JmxOperation ==========
            for (Method m : current.getDeclaredMethods()) {
                JmxAttribute attrAnno = m.getAnnotation(JmxAttribute.class);
                JmxOperation opAnno = m.getAnnotation(JmxOperation.class);
                // 禁止同时标记两个注解
                if (attrAnno != null && opAnno != null) {
                    continue;
                }

                String mName = m.getName();
                if (attrAnno != null) {
                    // 👉 方法上的 @JmxAttribute：这个方法本身就是getter
                    FieldInfo fi = new FieldInfo();
                    fi.anno = attrAnno;
                    fi.attrName = attrAnno.name().isEmpty() ? mName : attrAnno.name();
                    fi.fieldType = m.getReturnType();
                    fi.genericType = m.getGenericReturnType();
                    fi.getter = m; // 直接把当前方法作为getter
                    // JMX属性getter要求无参
                    if (m.getParameterCount() != 0) {
                        continue;
                    }
                    meta.fieldMap.put(fi.attrName, fi);
                } else if (opAnno != null) {
                    // 👉 @JmxOperation 操作方法
                    OperationInfo opInfo = new OperationInfo();
                    opInfo.opName = opAnno.name().isEmpty() ? mName : opAnno.name();
                    opInfo.anno = opAnno;
                    opInfo.method = m;
                    meta.opList.add(opInfo);
                }
            }
            current = current.getSuperclass();
        }
        return meta;
    }

    public static ClassMeta getClassMeta(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, JmxOpenTypeUtil::scanClass);
    }

    private static Method findGetter(Class<?> clazz, Field field) {
        String fieldName = field.getName();
        Class<?> fieldType = field.getType();
        String candidate1, candidate2;
        if (fieldType == boolean.class) {
            candidate1 = "is" + capitalize(fieldName);
            candidate2 = "get" + capitalize(fieldName);
        } else {
            candidate1 = "get" + capitalize(fieldName);
            candidate2 = null;
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (String name : Arrays.asList(candidate1, candidate2)) {
                if (name == null) continue;
                try {
                    Method m = current.getDeclaredMethod(name);
                    if (m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        return m;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
