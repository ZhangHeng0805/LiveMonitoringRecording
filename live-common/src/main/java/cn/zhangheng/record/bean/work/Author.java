package cn.zhangheng.record.bean.work;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/10 星期六 10:31
 * @version: 1.0
 * @description: 作者
 */
@Data
public class Author {
    protected String id;//ID
    protected String nickname;//昵称
    protected String avatar;//头像
    protected String introduction;//简介
    protected Integer follower_count;//粉丝量
}
