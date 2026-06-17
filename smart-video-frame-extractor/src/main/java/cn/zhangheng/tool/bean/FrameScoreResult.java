package cn.zhangheng.tool.bean;

import cn.zhangheng.tool.util.OpenCvUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bytedeco.opencv.opencv_core.Mat;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/12 星期五 00:55
 * @version: 1.0
 * @description:
 */
@Data
@AllArgsConstructor
public class FrameScoreResult {
    private final Path frame;
    private final boolean valid;
    private final double totalScore;
    //    private final Boolean hasFace;
    private final String hash;

    public static FrameScoreResult scoreFrame(Path frame) {
        try (AutoMat mat = OpenCvUtil.pathToMat(frame)) {
            if (mat.get() == null || mat.get().empty()) {
                return new FrameScoreResult(frame, false, 0, "");
            }
//            double fastBlurScore = OpenCvUtil.fastBlurScore(mat);
//            double calcLaplaceScore = OpenCvUtil.calcLaplaceScore(mat);
            double blackPixelRatio = OpenCvUtil.calcBlackPixelRatio(mat.get());
            String hash = OpenCvUtil.calcAHash(mat.get());

            double totalScore = (1 - blackPixelRatio) * 100;
            return new FrameScoreResult(frame, true, totalScore, hash);
        } catch (IOException e) {
            return new FrameScoreResult(frame, false, 0, "");
        }
    }
}
