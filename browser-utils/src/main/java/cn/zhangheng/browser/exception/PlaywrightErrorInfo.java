package cn.zhangheng.browser.exception;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/05 星期四 15:44
 * @version: 1.0
 * @description:
 */

import cn.hutool.core.util.StrUtil;
import lombok.Data;

@Data
public class PlaywrightErrorInfo {
    /**
     * 错误分类：TIMEOUT / TARGET_CLOSED / PROTOCOL / ASSERT / NETWORK_DNS / NETWORK_RESET / NETWORK_PROXY / NETWORK_CERT / NETWORK_CONN_TIMEOUT / NAVIGATE / OTHER
     */
    private String errorKind;
    /**
     * 原始异常message
     */
    private String rawMessage;
    /**
     * 提取 Error { message='xxx' } 里面的内层message
     */
    private String innerMessage;
    /**
     * 精简可读提示
     */
    private String briefMsg;
    /**
     * 页面URL
     */
    private String pageUrl;
    /**
     * 失败Host（从url提取）
     */
    private String failedHost;
    /**
     * 帧frame信息
     */
    private String frameInfo;
    /**
     * 选择器selector（定位失败时）
     */
    private String selector;
    /**
     * 原始异常类名
     */
    private String exceptionClass;
    /**
     * 根cause消息
     */
    private String rootCauseMsg;
    /**
     * 是否可重试标记
     */
    private boolean retryable;
    /**
     * 完整堆栈
     */
    private String stackTrace;

    public String toSimpleString() {
        String msg = StrUtil.isNotBlank(innerMessage) ? innerMessage : StrUtil.sub(rawMessage, 0, 128);
        return StrUtil.format("{} [{}: {}]", briefMsg, exceptionClass, msg);
    }
}
