package cn.zhangheng.douyin;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Video;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/10/23 星期四 14:03
 * @version: 1.0
 * @description:
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DouYinVideo extends Video {
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
    //音频地址
    private String audioUrl;
    //主页签名
    private String signature;
    //视频规格
    private String videoSpecifications;

    @Override
    public String getStatistics() {
        if (statistics == null) {
            this.statistics = StrUtil.format("点赞数: {}; 收藏数: {}; 分享数: {}; 评论数: {}; 推荐数: {}; 赞赏数: {}"
                    , like_count, collect_count, share_count, comment_count, recommend_count, admire_count);
        }
        return statistics;
    }
}
