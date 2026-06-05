package cn.zhangheng.browser;

import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/03 星期三 10:14
 * @version: 1.0
 * @description:
 */
public interface AutoClearContextThreadListener {
    //自动清除线程开始
    default void threadStarted(int intervalSec) {}
    //触发检查
    default void triggerCheck(int intervalSec, Map<String, BrowserContent> allContextThread) {}
    //触发清除
    default void triggerClear(int intervalSec, String clearThreadName,BrowserContent clearContext) {}
    //自动清除线程结束
    default void threadStop() {}

}
