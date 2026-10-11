package cn.zhangheng.douyin.bean.work;

import cn.hutool.json.JSONObject;
import cn.zhangheng.common.util.ObjectUtil;
import cn.zhangheng.record.bean.work.Author;
import cn.zhangheng.record.bean.work.Work;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/10/10 星期六 14:07
 * @version: 1.0
 * @description:
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DouYinWork extends Work {
    public DouYinWork(Type type) {
        setType(type);
    }

    private List<Image> images;
    private List<Video> videos;
    private DouYinWorkCounter counter;
    //音频地址
    private String audioUrl;

    public void parseAuthor(JSONObject authorJsonObject) {
        if (author == null) author = new Author();
        ObjectUtil.setIfNotNull(author::setId, authorJsonObject.getStr("short_id"));
        ObjectUtil.setIfNotNull(author::setNickname, authorJsonObject.getStr("nickname"));
        ObjectUtil.setIfNotNull(author::setIntroduction, authorJsonObject.getStr("signature"));
        ObjectUtil.setIfNotNull(author::setAvatar, authorJsonObject.getJSONObject("avatar_thumb").getJSONArray("url_list").getStr(0));
        ObjectUtil.setIfNotNull(author::setFollower_count, authorJsonObject.getInt("follower_count"));
    }

    public void parseCounter(JSONObject counterJsonObject) {
        if (counter == null) counter = new DouYinWorkCounter();
        ObjectUtil.setIfNotNull(counter::setLike_count, counterJsonObject.getLong("digg_count"));
        ObjectUtil.setIfNotNull(counter::setCollect_count, counterJsonObject.getInt("collect_count"));
        ObjectUtil.setIfNotNull(counter::setComment_count, counterJsonObject.getInt("comment_count"));
        ObjectUtil.setIfNotNull(counter::setRecommend_count, counterJsonObject.getInt("recommend_count"));
        ObjectUtil.setIfNotNull(counter::setShare_count, counterJsonObject.getInt("share_count"));
        ObjectUtil.setIfNotNull(counter::setAdmire_count, counterJsonObject.getInt("admire_count"));
    }

    @Data
    public static class Image {
        private String url;
        private String specifications;//规格

        public void setSpecifications(int width, int height) {
            this.specifications = width + " × " + height;
        }
    }

    @Data
    public static class Video {
        private String url;
        private String cover;//封面
        private String specifications;//规格
        private Long size;//大小

        public void setSpecifications(int width, int height) {
            this.specifications = width + " × " + height;
        }
    }
}
