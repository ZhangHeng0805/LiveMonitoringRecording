package cn.zhangheng.browser;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/28 星期日 00:39
 * @version: 1.0
 * @description:
 */
public class MyThreadLocal<T> {
    private final ConcurrentHashMap<String, T> map = new ConcurrentHashMap<>();
    private Listener listener;

    public MyThreadLocal(Listener listener) {
        this.listener = listener;
    }

    public MyThreadLocal() {
        this(null);
    }

    public T get() {
        return map.get(Thread.currentThread().getName());
    }

    public void set(T value) {
        if (value == null) {
            // 允许通过set(null)移除值，与标准ThreadLocal行为一致
            remove();
            return;
        }
        map.put(Thread.currentThread().getName(), value);
    }

    public void remove() {
        String name = Thread.currentThread().getName();
        T removedValue = map.remove(name);
        // 通知监听器移除了值
        if (listener != null && removedValue != null) {
            listener.removed(name, removedValue);
        }
    }

    /**
     * 获取当前线程是否有值
     */
    public boolean hasValue() {
        return map.containsKey(Thread.currentThread().getName());
    }

    /**
     * 获取当前存储的线程值数量
     */
    public int size() {
        return map.size();
    }

    public void clear() {
        if (listener != null) {
            listener.beforeClear(map);
        }
        map.clear();
        if (listener != null) {
            listener.afterClear();
        }
    }

    interface Listener<T> {
        /**
         * 当某个线程的值被移除时调用
         */
        default void removed(String threadId, T value) {
        }

        /**
         * 在所有值被清除前调用
         */
        default void beforeClear(ConcurrentHashMap<String, T> map) {
        }

        /**
         * 在所有值被清除后调用
         */
        default void afterClear() {
        }
    }
}
