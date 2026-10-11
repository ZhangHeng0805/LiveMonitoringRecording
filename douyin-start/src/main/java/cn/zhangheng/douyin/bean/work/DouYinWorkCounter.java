package cn.zhangheng.douyin.bean.work;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.record.bean.work.WorkCounter;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/10 星期六 12:36
 * @version: 1.0
 * @description:
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DouYinWorkCounter extends WorkCounter {
    //收藏数
    private Integer collect_count;
    //分享数
    private Integer share_count;
    //评论数
    private Integer comment_count;
    //赞赏数
    private Integer admire_count;
    //推荐数
    private Integer recommend_count;

    @Override
    public String statistics() {
        return StrUtil.format("点赞数: {}; 收藏数: {}; 分享数: {}; 评论数: {}; 推荐数: {}; 赞赏数: {}"
                , like_count, collect_count, share_count, comment_count, recommend_count, admire_count);
    }


}
