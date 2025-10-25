package cn.zhangheng.common.bean;

import lombok.Data;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 13:51
 * @version: 1.0
 * @description: 视频作品抽象类
 */
@Data
public abstract class Video {
    //视频标题
    protected String title;
    //作者
    protected String author;
    //头像
    protected String avatar;
    //封面
    protected String cover;
    //视频地址
    protected String videoUrl;
    //视频大小
    protected Long size;
    //点赞数
    protected Long like_count;

    protected String statistics;

    protected String createTime;

    /**
     * 视频统计数据
     * @return
     */
    public abstract String getStatistics();
}
