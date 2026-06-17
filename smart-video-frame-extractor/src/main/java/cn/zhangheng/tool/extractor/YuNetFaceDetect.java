package cn.zhangheng.tool.extractor;

import cn.zhangheng.tool.bean.FaceResult;
import cn.zhangheng.tool.util.OpenCvUtil;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/13 星期六 07:32
 * @version: 1.0
 * @description:
 */
public class YuNetFaceDetect implements AutoCloseable {
    private Net yunetNet;
    // 推理配置
    private final float scoreThreshold = 1f;   // 置信度过滤假人脸
    private final float nmsThreshold = 0.3f;

    // 初始化模型，传入onnx文件路径
    public YuNetFaceDetect(String modelPath) {
        yunetNet = opencv_dnn.readNet(modelPath);
        // CPU推理
//        yunetNet.setPreferableBackend(opencv_dnn.DNN_BACKEND_OPENCV);
//        yunetNet.setPreferableTarget(opencv_dnn.DNN_TARGET_CPU);
    }

    // 执行检测，返回所有人脸+五官
    public List<FaceResult> detect(Mat srcImg) {
        List<FaceResult> faceList = new ArrayList<>();
        int imgW = srcImg.cols();
        int imgH = srcImg.rows();

        int inputWidth = 320;
        int inputHeight = 320;
        Mat blob = opencv_dnn.blobFromImage(
                srcImg,
                1 / 255.0,
                new Size(inputWidth, inputHeight),
                new Scalar(127.5, 127.5, 127.5, 0),
                true,
                false,
                opencv_core.CV_32F
        );
        yunetNet.setInput(blob);
        Mat output = yunetNet.forward();

        int numRaw = output.size(1);
        float[] buf = new float[15];
        // 大幅拉高置信度阈值，只保留最高置信的1个真人脸
        float hardScore = scoreThreshold;

        for (int i = 0; i < numRaw; i++) {
            for (int j = 0; j < 15; j++) {
                buf[j] = output.ptr(0, i, j).getFloat();
            }
            float score = buf[4];
            // 低分全部丢弃
            if (score < hardScore) {
                continue;
            }

            float scaleX = (float) imgW / inputWidth;
            float scaleY = (float) imgH / inputHeight;
            Rect rect = new Rect(
                    Math.round(buf[0] * scaleX),
                    Math.round(buf[1] * scaleY),
                    Math.round(buf[2] * scaleX),
                    Math.round(buf[3] * scaleY)
            );
            // 边界安全裁剪，防止坐标出画面
            rect.x(Math.max(0, rect.x()));
            rect.y(Math.max(0, rect.y()));
            rect.width(Math.min(imgW - rect.x(), rect.width()));
            rect.height(Math.min(imgH - rect.y(), rect.height()));

            FaceResult fr = getFaceResult(rect, buf, scaleX, scaleY, score);
            faceList.add(fr);
        }
        System.out.println("人脸数：" + faceList.size());

        // 只保留分数最高的唯一人脸（兜底去重）
        if (!faceList.isEmpty()) {
            FaceResult best = faceList.get(0);
            for (FaceResult f : faceList) {
                if (f.getScore() > best.getScore()) best = f;
            }
            faceList.clear();
            faceList.add(best);
        }

        blob.close();
        output.close();
        return faceList;
    }

    private static FaceResult getFaceResult(Rect rect, float[] buf, float scaleX, float scaleY, float score) {
        FaceResult fr = new FaceResult();
        fr.setFaceRect(rect);
        fr.setLeftEye(new Point(Math.round(buf[5] * scaleX), Math.round(buf[6] * scaleY)));
        fr.setRightEye(new Point(Math.round(buf[7] * scaleX), Math.round(buf[8] * scaleY)));
        fr.setNose(new Point(Math.round(buf[9] * scaleX), Math.round(buf[10] * scaleY)));
        fr.setScore(score);
        return fr;
    }

    // 绘制预览：人脸绿框、眼睛红点标注
    public Mat drawPreview(Mat src, List<FaceResult> faceList) {
        Mat drawImg = src.clone();
        Scalar faceColor = new Scalar(0.0, 255.0, 0.0, 0.0);  // 绿色人脸框
        Scalar eyeColor = new Scalar(0.0, 0.0, 255.0, 0.0);    // 红色眼睛点

        for (FaceResult face : faceList) {
            // 绘制人脸矩形
            Rect faceRect = face.getFaceRect();
            opencv_imgproc.rectangle(drawImg, faceRect.tl(), faceRect.br(), faceColor, 2, opencv_imgproc.LINE_AA, 0);
//            // 标注置信度
//            String text = String.format("score:%.2f", face.getScore());
//            opencv_imgproc.putText(
//                    drawImg, text,
//                    new Point(faceRect.x(), faceRect.y() - 8),
//                    opencv_imgproc.FONT_HERSHEY_SIMPLEX,
//                    1, faceColor, 1, opencv_imgproc.LINE_AA, false
//            );
//            // 绘制左右眼圆点
//            opencv_imgproc.circle(drawImg, face.getLeftEye(), 3, eyeColor, -1, opencv_imgproc.LINE_AA, 0);
//            opencv_imgproc.circle(drawImg, face.getRightEye(), 3, eyeColor, -1, opencv_imgproc.LINE_AA, 0);
        }
        return drawImg;
    }

    // 释放网络资源
    @Override
    public void close() {
        if (yunetNet != null) yunetNet.close();
    }

    // 测试入口
    public static void main(String[] args) throws IOException {
        // 1、替换为你本地onnx模型路径
        String modelPath = "bin/open_cv/face_detection_yunet_2023mar.onnx";
        YuNetFaceDetect detector = new YuNetFaceDetect(modelPath);
        String parent = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2025-06-07\\video_frames";
        Path p = Paths.get(parent, "raw_001048_score_82.20.jpg");
//        Path p = Paths.get(parent, "raw_001046_score_84.72.jpg");
//        Path p = Paths.get(parent, "raw_000521_score_84.94.jpg");
        // 读取图片
        Mat src = OpenCvUtil.pathToMat(p).get();
        if (src.empty()) {
            System.err.println("图片读取失败");
            return;
        }

        // 检测
        List<FaceResult> faces = detector.detect(src);
        System.out.println("检测到人脸数量：" + faces.size());

        // 绘制预览并保存
        Mat preview = OpenCvUtil.drawPreview(src, faces.get(0).getFaceRect());
        boolean saveOk = opencv_imgcodecs.imwrite("img/" + p.getFileName(), preview);
        System.out.println(saveOk ? "预览图保存成功" : "保存失败");

        // 资源释放
        src.close();
        preview.close();
        detector.close();


    }
}
