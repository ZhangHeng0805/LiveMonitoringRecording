package cn.zhangheng.browser;

import lombok.Data;

import java.util.Map;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 14:13
 * @version: 1.0
 * @description:
 */
@Data
public class BrowserAPI {
    public BrowserAPI(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    //接口前缀，根据前缀匹配url
    private final String urlPrefix;
    //接口数据url
    private String dataUrl;

    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
        this.updateTimes = System.currentTimeMillis();
    }

    //接口请求头
    private Map<String, String> headers;

    public void setHeaders(Map<String, String> headers) {
        headers.entrySet().removeIf(next -> next.getKey().startsWith(":"));
        this.headers = headers;
    }

    //接口响应体
    private String responseBody;

    private long updateTimes = 0;
}
