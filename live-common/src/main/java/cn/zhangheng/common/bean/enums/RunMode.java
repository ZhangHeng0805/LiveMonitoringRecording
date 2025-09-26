package cn.zhangheng.common.bean.enums;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/24 星期三 20:13
 * @version: 1.0
 * @description: 程序运行模式
 */
public enum RunMode {
    COMMAND,//通过命令交互的方式,只能监听单个直播间
    FILE,//通过读取文件的形式获取监听信息，可以同事监听多个直播间
    ;
}
