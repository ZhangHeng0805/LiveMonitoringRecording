package cn.zhangheng.tool.bean;

import lombok.Data;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/13 星期六 07:31
 * @version: 1.0
 * @description:
 */
@Data
public class FaceResult {
    private Rect faceRect;
    private Point leftEye;
    private Point rightEye;
    private Point nose;
    float score;
}
