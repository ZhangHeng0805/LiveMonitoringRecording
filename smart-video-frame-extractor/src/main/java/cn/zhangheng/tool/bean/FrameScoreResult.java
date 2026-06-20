package cn.zhangheng.tool.bean;

import cn.zhangheng.tool.extractor.FaceDetector;
import cn.zhangheng.tool.util.OpenCvUtil;
import cn.zhangheng.tool.util.SimilarFrameUtil;
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
    private static final SimilarFrameUtil similarFrameUtil=new SimilarFrameUtil();
    private final Path frame;
    private final boolean valid;
    private final double totalScore;
    //    private final Boolean hasFace;
    private final String hash;

    public static FrameScoreResult scoreFrame(Path frame) {
        try (AutoMat mat = OpenCvUtil.pathToMat(frame)) {
            Mat src = mat.get();
            if (src == null || src.empty()) {
                return new FrameScoreResult(frame, false, 0, "");
            }
//            double fastBlurScore = OpenCvUtil.fastBlurScore(src);
//            double calcLaplaceScore = OpenCvUtil.calcLaplaceScore(src);
            double blackPixelRatio = (1 - OpenCvUtil.calcBlackPixelRatio(src)) * 100;
            String hash = OpenCvUtil.calcAHash(src);
            boolean similarFrame = similarFrameUtil.isSimilarFrame(hash, blackPixelRatio);
            if (similarFrame) return new FrameScoreResult(frame, false, blackPixelRatio, hash);
            int faceScore = FaceDetector.detectAllAngleFace(src).size() * 100;
            double totalScore = blackPixelRatio + faceScore;
            return new FrameScoreResult(frame, faceScore > 0, totalScore, hash);
        } catch (IOException e) {
            return new FrameScoreResult(frame, false, 0, "");
        }
    }
}
