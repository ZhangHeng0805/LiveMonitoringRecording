package cn.zhangheng.record.bean.work;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/10 星期六 10:31
 * @version: 1.0
 * @description: 作品
 */
@Data
public abstract class Work {

    protected String title;//标题
    protected Author author;//作者
    protected Type type;
    protected String createTime;

    public enum Type {
        Video,
        Image;
    }
}
