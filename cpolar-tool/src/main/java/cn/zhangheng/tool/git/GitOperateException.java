package cn.zhangheng.tool.git;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/10 星期三 17:35
 * @version: 1.0
 * @description:
 */
public class GitOperateException extends Exception {

    public GitOperateException() {
        super();
    }

    public GitOperateException(String message) {
        super(message);
    }

    public GitOperateException(String message, Throwable cause) {
        super(message, cause);
    }
}