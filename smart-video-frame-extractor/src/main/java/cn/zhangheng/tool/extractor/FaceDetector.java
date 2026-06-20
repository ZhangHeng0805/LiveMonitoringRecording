package cn.zhangheng.tool.extractor;

import cn.zhangheng.tool.bean.AutoMat;
import com.zhangheng.util.ArrayUtil;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.zhangheng.tool.util.OpenCvUtil.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/18 星期四 08:35
 * @version: 1.0
 * @description:
 */
public class FaceDetector {
    // 两个检测器全局初始化，不要每次new
    private static CascadeClassifier frontFaceDetector1;
    private static CascadeClassifier profileFaceDetector1;
//    private static CascadeClassifier frontFaceDetector2;
//    private static CascadeClassifier profileFaceDetector2;

    static {
        frontFaceDetector1 = new CascadeClassifier();
        profileFaceDetector1 = new CascadeClassifier();
//        frontFaceDetector2 = new CascadeClassifier();
//        profileFaceDetector2 = new CascadeClassifier();

        boolean faceLoad1 = loadClassifierFromResource(frontFaceDetector1, "bin/open_cv/lbpcascade_frontalface_improved.xml");
        boolean profileFaceLoad1 = loadClassifierFromResource(profileFaceDetector1, "bin/open_cv/lbpcascade_profileface.xml");
//        boolean faceLoad2 = loadClassifierFromResource(frontFaceDetector2, "bin/open_cv/haarcascade_frontalface_alt2.xml");
//        boolean profileFaceLoad2 = loadClassifierFromResource(profileFaceDetector2, "bin/open_cv/haarcascade_profileface.xml");
//        boolean faceLoad = loadClassifierFromResource(faceDetector, "bin/open_cv/lbpcascade_frontalface.xml");
//        boolean faceLoad = loadClassifierFromResource(faceDetector, "bin/open_cv/haarcascade_frontalface_default.xml");

        boolean b = faceLoad1 &&
//                faceLoad2 &&
                profileFaceLoad1
//                && profileFaceLoad2
                ;
        System.out.println("OpenCV人脸模型加载：" + (b ? "成功" : "失败"));
    }

    public static void main(String[] args) throws IOException {
        String parent = "D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2026-06-17\\video_frames";

        Path parentDir = Paths.get(parent);
        try (Stream<Path> stream = Files.list(parentDir)) {
            List<Path> paths = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            Path tmpPath = Paths.get(parentDir.getRoot().toString(), "temp_frames");
            paths.forEach(path -> {
                List<Rect> face = null;
                try (AutoMat autoMat = pathToMat(path)) {
                    Mat src = autoMat.get();
                    face = detectAllAngleFace(src);
                    Rect[] rects = ArrayUtil.toArray(face, Rect.class);
                    Mat mat = drawPreview(src, rects);
                    String name = path.getFileName().toString();
                    String previewPath = Paths.get(tmpPath.toString(), name).toString();
                    opencv_imgcodecs.imwrite(previewPath, mat);
                    mat.close();
                    System.out.println(name + " 识别数量: " + face.size());
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                } finally {
                }
            });
        }
    }

    /**
     * 正脸 + 左侧脸 + 右侧脸 全套检测
     */
    public static List<Rect> detectAllAngleFace(Mat src) {
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        opencv_imgproc.equalizeHist(gray, gray); // 直方图均衡，提升暗光识别

        List<Rect> totalRects = new ArrayList<>();
        Size minFaceSize = new Size(30, 30);
        Size maxFaceSize = new Size(3000, 3000);
        double scale = 1.1;
        int minNeighbor = 5;

        // 1. 检测正脸
        RectVector frontResult = new RectVector();
        frontFaceDetector1.detectMultiScale(gray, frontResult, scale, minNeighbor, 0, minFaceSize, maxFaceSize);
        totalRects.addAll(Arrays.asList(frontResult.get()));
//        frontResult.clear();
//        frontFaceDetector2.detectMultiScale(gray, frontResult, scale, minNeighbor, 0, minFaceSize, maxFaceSize);
//        totalRects.addAll(Arrays.asList(frontResult.get()));

        // 2. 原图检测侧脸（默认只能检测脸朝左的侧脸）
        RectVector profileLeft = new RectVector();
        profileFaceDetector1.detectMultiScale(gray, profileLeft, scale, minNeighbor, 0, minFaceSize, maxFaceSize);
        totalRects.addAll(Arrays.asList(profileLeft.get()));

        // 3. 水平翻转图像，检测脸朝右的侧脸，再修正坐标
        Mat flipGray = new Mat();
        opencv_core.flip(gray, flipGray, 1); // 1=水平翻转
        RectVector profileRight = new RectVector();
        profileFaceDetector1.detectMultiScale(flipGray, profileRight, scale, minNeighbor, 0, minFaceSize, maxFaceSize);

        int imgWidth = gray.cols();
        for (Rect r : profileRight.get()) {
            // 翻转后坐标还原回原图
            int newX = imgWidth - r.x() - r.width();
            totalRects.add(new Rect(newX, r.y(), r.width(), r.height()));
        }
        flipGray.close();
        profileRight.close();

        // 4. NMS去重，消除同一个人脸多个重叠框
        List<Rect> finalFaces = nms(totalRects, 0.4);

        gray.close();
        frontResult.close();
        profileLeft.close();
        return finalFaces;
    }

    /**
     * 非极大值抑制NMS，去除重叠人脸框
     *
     * @param rectList  所有候选框
     * @param iouThresh 重叠阈值，0.3~0.5最合适
     */
    private static List<Rect> nms(List<Rect> rectList, double iouThresh) {
        List<Rect> result = new ArrayList<>();
        List<Rect> temp = new ArrayList<>(rectList);

        while (!temp.isEmpty()) {
            Rect best = temp.remove(0);
            result.add(best);

            List<Rect> remain = new ArrayList<>();
            for (Rect r : temp) {
                double iou = calcIoU(best, r);
                if (iou < iouThresh) {
                    remain.add(r);
                }
            }
            temp = remain;
        }
        return result;
    }

    // 计算两个框交并比IOU
    private static double calcIoU(Rect a, Rect b) {
        int x1 = Math.max(a.x(), b.x());
        int y1 = Math.max(a.y(), b.y());
        int x2 = Math.min(a.x() + a.width(), b.x() + b.width());
        int y2 = Math.min(a.y() + a.height(), b.y() + b.height());
        if (x2 <= x1 || y2 <= y1) return 0;

        int inter = (x2 - x1) * (y2 - y1);
        int areaA = a.width() * a.height();
        int areaB = b.width() * b.height();
        return (double) inter / (areaA + areaB - inter);
    }
}
