package cn.zhangheng.record.bean.work;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/10 星期六 12:33
 * @version: 1.0
 * @description:
 */
@Data
public abstract class WorkCounter {
    protected Long like_count;//点赞数

    /**
     * 视频统计数据
     *
     * @return
     */
    public abstract String statistics();
}
