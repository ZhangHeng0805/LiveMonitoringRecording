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
public class API {
    public API(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }
    private final String urlPrefix;
    private String dataUrl;
    private Map<String,String> headers;

    public void setHeaders(Map<String, String> headers) {
        headers.entrySet().removeIf(next -> next.getKey().startsWith(":"));
        this.headers = headers;
    }

    private String responseBody;
}
