package cn.zhangheng.tool.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/12 星期五 04:59
 * @version: 1.0
 * @description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaInfo {
    private double fps;
    private long durationMs;
    private int width;
    private int height;
    private String bitrate;


}
