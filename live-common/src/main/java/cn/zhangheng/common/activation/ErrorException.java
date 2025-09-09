package cn.zhangheng.common.activation;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/01 星期一 05:44
 * @version: 1.0
 * @description:
 */
public class ErrorException extends Exception{
    public ErrorException(String msg, Exception e) {
        super(msg, e);
    }

    public ErrorException(String msg) {
        super(msg);
    }
}
