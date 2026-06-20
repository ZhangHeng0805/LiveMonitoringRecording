package cn.zhangheng.tool.util;

import cn.hutool.core.io.IoUtil;
import cn.zhangheng.tool.bean.AutoMat;
import com.zhangheng.file.FileUtil;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;

import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static cn.hutool.core.util.ClassLoaderUtil.getClassLoader;
import static org.opencv.core.CvType.CV_64F;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/11 星期四 09:03
 * @version: 1.0
 * @description:
 */
public class OpenCvUtil {
    public static final String IMG_SUFFIX = ".jpg";
    private static CascadeClassifier faceDetector;
    private static CascadeClassifier eyeDetector;

    static {
//        faceDetector = new CascadeClassifier();
//        eyeDetector = new CascadeClassifier();
//        boolean faceLoad = loadClassifierFromResource(faceDetector, "bin/open_cv/lbpcascade_frontalface_improved.xml");
//        boolean faceLoad = loadClassifierFromResource(faceDetector, "bin/open_cv/lbpcascade_frontalface.xml");
//        boolean faceLoad = loadClassifierFromResource(faceDetector, "bin/open_cv/haarcascade_frontalface_default.xml");
//        if (!faceLoad) faceDetector = null;
//        boolean eyeLoad = loadClassifierFromResource(eyeDetector, "bin/open_cv/haarcascade_eye_tree_eyeglasses.xml");
//        if (!eyeLoad) eyeDetector = null;
//        System.out.println("OpenCV人脸模型加载：" + (faceLoad ? "成功" : "失败"));
//        System.out.println("OpenCV眼睛模型加载：" + (eyeLoad ? "成功" : "失败"));
    }


    /**
     * 通用工具：读取resources下xml，写入临时文件，再加载分类器
     *
     * @param classifier 检测器实例
     * @param resName    resources内文件名
     * @return 是否加载成功
     */
    public static boolean loadClassifierFromResource(CascadeClassifier classifier, String resName) {
        Path path = Paths.get(resName);
        if (Files.exists(path)) {
            return classifier.load(path.toAbsolutePath().toString());
        }
        try (InputStream input = getClassLoader().getResourceAsStream("data/" + path.getFileName())) {
            if (input == null) {
                return false;
            }
            // 创建jvm退出自动删除的临时文件
            File tempXml = File.createTempFile(FileUtil.getMainName(resName), ".xml");
            tempXml.deleteOnExit();
            try (OutputStream outputStream = Files.newOutputStream(tempXml.toPath())) {
                IoUtil.copy(input, outputStream);
            }
            // 用物理路径加载
            return classifier.load(tempXml.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 拉普拉斯清晰度评分
    public static double calcLaplaceScore(Mat src) {
        if (src.empty()) return 0.0;
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        Mat lap = new Mat();
        opencv_imgproc.Laplacian(gray, lap, CV_64F);

        int rows = lap.rows();
        int cols = lap.cols();
        int totalPix = rows * cols;
        if (totalPix <= 0) {
            gray.release();
            lap.release();
            return 0.0;
        }

        double total = 0.0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                total += lap.ptr(y, x).getDouble();
            }
        }
        double avg = total / totalPix;

        double variance = 0.0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double val = lap.ptr(y, x).getDouble();
                variance += (val - avg) * (val - avg);
            }
        }

        gray.close();
        lap.close();
        return variance / 10_000_000;
    }

    /**
     * 高速模糊清晰度打分
     * 返回值越大画面越清晰，越小越模糊
     */
    public static double fastBlurScore(Mat src) {
        if (src == null || src.empty()) {
            return 0.0;
        }
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);

        int rows = gray.rows();
        int cols = gray.cols();
        double total = 0.0;
        // 只横向对比x与x+1，计算量最小；想要精度微提升可再加纵向y对比
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols - 1; x++) {
                int p1 = gray.ptr(y, x).get();
                int p2 = gray.ptr(y, x + 1).get();
                int diff = p1 - p2;
                total += (double) diff * diff;
            }
        }

        gray.close();
        // 可选归一：除以总像素，适配不同分辨率图片对比
        // return total / (rows * cols);
        return total / 10_000_000;
    }

    // 黑屏占比计算
    public static double calcBlackPixelRatio(Mat src) {
        if (src.empty()) return 0.0;
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        Mat mask = new Mat();
        opencv_imgproc.threshold(gray, mask, 30, 255, opencv_imgproc.THRESH_BINARY_INV);

        int blackCnt = 0;
        int rows = mask.rows();
        int cols = mask.cols();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (mask.ptr(y, x).get() != 0) blackCnt++;
            }
        }

        long total = (long) rows * cols;
        gray.close();
        mask.close();
        return (double) blackCnt / total;
    }

    // 色彩丰富度
    public static double calcColorVariance(Mat src) {
        if (src.empty()) return 0.0;
        int rows = src.rows();
        int cols = src.cols();
        double totalColor = 0;
        // 遍历三通道像素差值，判定色彩丰富度
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int b = src.ptr(y, x).get(0);
                int g = src.ptr(y, x).get(1);
                int r = src.ptr(y, x).get(2);
                totalColor += (Math.abs(b - g) + Math.abs(g - r) + Math.abs(r - b));
            }
        }
        return totalColor;
    }

    public static AutoMat pathToMat(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new RuntimeException(file + " 文件不存在");
        }
        byte[] bytes = Files.readAllBytes(file);
        return new AutoMat(opencv_imgcodecs.imdecode(new Mat(bytes), opencv_imgcodecs.IMREAD_COLOR));
    }

    public static String calcAHash(Mat src) {
        // 空图保护
        if (src.empty()) {
            return "";
        }
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        Mat resizeMat = new Mat();
        // 标准8*8尺寸，64位短哈希，对比速度快
        opencv_imgproc.resize(gray, resizeMat, new Size(10, 10));

        int rows = resizeMat.rows();
        int cols = resizeMat.cols();
        int totalPixel = rows * cols;
        if (totalPixel == 0) {
            gray.release();
            resizeMat.release();
            return "";
        }

        // 一次遍历：求和 + 缓存像素值，不用两次循环读像素
        long sum = 0;
        int[] pixCache = new int[totalPixel];
        int idx = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int val = resizeMat.ptr(y, x).get();
                pixCache[idx++] = val;
                sum += val;
            }
        }
        double meanVal = (double) sum / totalPixel;

        // 生成01哈希字符串
        StringBuilder hashSb = new StringBuilder(totalPixel);
        for (int pix : pixCache) {
            hashSb.append(pix > meanVal ? "1" : "0");
        }

        gray.close();
        resizeMat.close();
        return hashSb.toString();
    }

    /**
     * 计算两个01哈希串的汉明距离
     * 数值越小越相似：0=完全一样，<10高度相似，>20差异很大
     */
    public static int calcHamming(String hash1, String hash2) {
        if (hash1.length() != hash2.length()) {
            return Integer.MAX_VALUE;
        }
        int diff = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                diff++;
            }
        }
        return diff;
    }

    public static RectVector getFace(Mat src) {
        if (faceDetector == null) {
            throw new IllegalArgumentException("人脸模型未加载,faceDetector为null");
        }
        Mat gray = new Mat();
        Mat procImg = new Mat();
        try {
            opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
            // CLAHE均衡
            CLAHE clahe = opencv_imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(gray, procImg);
            // 轻微高斯降噪
            opencv_imgproc.GaussianBlur(procImg, gray, new Size(3, 3), 0);
            gray.convertTo(gray, -1, 1.5, 0);// alpha对比度，beta亮度偏移
            RectVector faces = new RectVector();
            faceDetector.detectMultiScale(
                    gray,
                    faces,
                    1.1,
                    5,
                    0,
                    new Size(50, 50),
                    new Size(3000, 3000)
            );
            // 后过滤人脸框
//            RectVector validFaces = filterFalseFace(faces);
//            faces.close();
            return faces;
        } finally {
            gray.close();
            procImg.close();
        }
    }

    // 单独抽离过滤方法
    private static RectVector filterFalseFace(RectVector rawFaces) {
        RectVector res = new RectVector();
        for (int i = 0; i < rawFaces.size(); i++) {
            Rect rect = rawFaces.get(i);
            float whRatio = (float) rect.width() / rect.height();
            int area = rect.width() * rect.height();
            if (whRatio >= 0.7 && whRatio <= 1.4 && area > 400) {
                res.push_back(rect);
            }
        }
        return res;
    }

    public static RectVector getEyes(Mat src) {
        if (eyeDetector == null) throw new IllegalArgumentException("眼睛模型未加载,eyeDetector为null");
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        RectVector allEyes = new RectVector();

        RectVector faces = getFace(src);
        int faceCount = (int) faces.size();
        for (int i = 0; i < faceCount; i++) {
            Rect face = faces.get(i);
            RectVector eyes = detectEyesInFace(gray, face);
            // 把当前人脸的所有眼睛合并到总集合
            int eCnt = (int) eyes.size();
            for (int j = 0; j < eCnt; j++) {
                allEyes.push_back(eyes.get(j));
            }
            eyes.close();
        }

        gray.close();
        faces.close();
        return allEyes;
    }

    private static RectVector detectEyesInFace(Mat grayImg, Rect faceRect) {
        int imgCols = grayImg.cols();
        int imgRows = grayImg.rows();

        int x = Math.max(0, faceRect.x());
        int y = Math.max(0, faceRect.y());
        int w = Math.min(faceRect.width(), imgCols - x);
        int h = Math.min(faceRect.height(), imgRows - y);

        RectVector emptyVec = new RectVector();
        if (w < 20 || h < 20) {
            return emptyVec;
        }

        Rect safeFace = new Rect(x, y, w, h);
        Mat faceRoi = grayImg.apply(safeFace);

        RectVector localEyes = new RectVector();
        eyeDetector.detectMultiScale(faceRoi, localEyes,
                1.1, 5, 0,
                new Size(12, 12),
                new Size(800, 800));

        // 新建容器存储全局坐标眼睛
        RectVector globalEyes = new RectVector();
        for (int i = 0; i < localEyes.size(); i++) {
            Rect localEye = localEyes.get(i);
            // 眼睛必须在人脸高度的 10% ~ 55% 区间，顶部眉毛、下方嘴巴直接丢弃
            double eyeYPercent = (double) localEye.y() / safeFace.height();
            if (eyeYPercent < 0.10 || eyeYPercent > 0.55) {
//                System.out.println("眼睛必须在人脸高度比例:" + eyeYPercent);
                continue;
            }
            // 眼睛合理比例1.1 ~ 2.6，正圆/竖圆直接丢弃
//            double wh = (double) localEye.width() / localEye.height();
//            if (wh < 1.1 || wh > 2.6) {
//                continue;
//            }
            // 检测器minSize(12,12)，额外过滤极小框
            if (localEye.width() < 18 || localEye.height() < 18) {
                continue;
            }
            Rect globalEye = new Rect(
                    safeFace.x() + localEye.x(),
                    safeFace.y() + localEye.y(),
                    localEye.width(),
                    localEye.height()
            );
            globalEyes.push_back(globalEye);
        }

        faceRoi.close();
        safeFace.close();
        localEyes.close();
        return globalEyes;
    }

    /**
     * 在原图绘制眼睛框和开闭状态
     *
     * @param src     原始彩色图（不会修改原图，内部拷贝绘制）
     * @param eyeList 带坐标+开闭状态的眼睛集合
     * @return 绘制完成的预览图
     */
    public static Mat drawPreview(Mat src, Rect... eyeList) {
        Mat drawImg = src.clone();
        String text = "" + eyeList.length;
        Scalar greenColor = new Scalar(0, 255, 0, 0); // BGR 绿色
        Scalar redColor = new Scalar(0, 0, 255, 0); // BGR 红色
        for (Rect rect : eyeList) {
            // 1、画方框：直接传入Rect，匹配截图里 rectangle(Mat, Rect, Scalar, int)
            opencv_imgproc.rectangle(drawImg, rect, greenColor, 3, opencv_imgproc.LINE_AA, 0);

        }
        // 2、绘制文字：匹配 putText(Mat, String, Point, int, double, Scalar, int)
        Point textPos = new Point(50, 150);
        opencv_imgproc.putText(
                drawImg,
                text,
                textPos,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                5,
                redColor,
                5,
                opencv_imgproc.LINE_AA,
                false
        );
        return drawImg;
    }

    /**
     * 判断单只眼睛是否睁开
     *
     * @param grayFull      整张人脸灰度图
     * @param eyeRect       检测出来的眼睛矩形（相对于整张图）
     * @param darkThreshold 暗像素阈值（灰度低于该值算瞳孔深色）
     * @param openRatio     占比阈值，超过则判定睁眼
     * @return true=睁眼 false=闭眼
     */
    public static boolean isEyeOpen(Mat grayFull, Rect eyeRect, int darkThreshold, double openRatio) {
        int x = Math.max(0, eyeRect.x());
        int y = Math.max(0, eyeRect.y());
        int w = Math.min(eyeRect.width(), grayFull.cols() - x);
        int h = Math.min(eyeRect.height(), grayFull.rows() - y);
        if (w < 5 || h < 5) return false;

        Mat eyeRoi = grayFull.apply(eyeRect);
        Mat grayEye = new Mat();
        Mat blur = new Mat();
        Mat bin = new Mat();

        try {
            // 二次保险：哪怕roi异常，强制转灰度
            if (eyeRoi.channels() == 3) {
                opencv_imgproc.cvtColor(eyeRoi, grayEye, opencv_imgproc.COLOR_BGR2GRAY);
            } else if (eyeRoi.channels() == 4) {
                opencv_imgproc.cvtColor(eyeRoi, grayEye, opencv_imgproc.COLOR_BGRA2GRAY);
            } else {
                eyeRoi.copyTo(grayEye);
            }
            // 高斯降噪
            opencv_imgproc.GaussianBlur(eyeRoi, blur, new Size(3, 3), 1);
//            System.out.println("eyeRoi通道：" + eyeRoi.channels());
            // 反向二值化，输出依然单通道
            opencv_imgproc.threshold(blur, bin, darkThreshold, 255, opencv_imgproc.THRESH_BINARY_INV);
//            System.out.println("bin通道：" + bin.channels());
            // 强制校验通道，防止异常
            if (bin.channels() != 1) {
                return false;
            }

            long total = (long) bin.cols() * bin.rows();
            long darkPixelCount = opencv_core.countNonZero(bin);
            double ratio = (double) darkPixelCount / total;
            System.out.printf("暗像素数:%d 总像素:%d 占比:%.3f\n", darkPixelCount, total, ratio);
            // 眉毛/头发区域会冲到0.5~0.9+，直接过滤
            if (ratio > 0.80) {
                System.out.println("疑似眉毛误检，丢弃");
                return false;
            }
            return ratio >= openRatio;
        } finally {
            eyeRoi.close();
            grayEye.close();
            blur.close();
            bin.close();
        }
    }

    public static boolean isEyeOpen(Mat grayFull, Rect eyeRect, double areaThresh) {
        if (grayFull.channels() != 1) {
            System.err.println("输入非单通道灰度图");
            return false;
        }

        int x = Math.max(0, eyeRect.x());
        int y = Math.max(0, eyeRect.y());
        int w = Math.min(eyeRect.width(), grayFull.cols() - x);
        int h = Math.min(eyeRect.height(), grayFull.rows() - y);
        if (w < 6 || h < 6) return false;

        // 宽松宽高比
        double whRatio = (double) w / h;
//        System.out.printf("眼框 w:%d h:%d 比例:%.2f%n", w, h, whRatio);
        if (whRatio < 0.8 || whRatio > 3.2) {
            return false;
        }

        Mat eyeRoi = grayFull.apply(new Rect(x, y, w, h));
        Mat blur = new Mat();
        Mat bin = new Mat();
        Mat hierarchy = new Mat();
        MatVector contours = new MatVector();
        Mat equal = new Mat();

        try {
            opencv_imgproc.GaussianBlur(eyeRoi, blur, new Size(3, 3), 1);
            // 均衡化是提亮瞳孔对比度的关键
            opencv_imgproc.equalizeHist(blur, equal);
            // 固定阈值50，亮画面也能捕捉深色瞳孔
            opencv_imgproc.threshold(equal, bin, 50, 255, opencv_imgproc.THRESH_BINARY_INV);

            opencv_imgproc.findContours(
                    bin,
                    contours,
                    hierarchy,
                    opencv_imgproc.RETR_EXTERNAL,
                    opencv_imgproc.CHAIN_APPROX_SIMPLE
            );

            double maxArea = 0;
            for (int i = 0; i < contours.size(); i++) {
                Mat singleContourMat = contours.get(i);
                double area = opencv_imgproc.contourArea(singleContourMat);
                if (area > maxArea) {
                    maxArea = area;
                }
            }

            double totalArea = (double) w * h;
            double ratio = maxArea / totalArea;
            System.out.printf("最大黑斑面积占比:%.3f%n", ratio);

            if (ratio > 0.40) { // 过滤眉毛大块黑斑
                return false;
            }
            return ratio >= areaThresh;

        } finally {
            eyeRoi.close();
            blur.close();
            bin.close();
            hierarchy.close();
            contours.close();
            equal.close();
        }
    }

    // 重载一套默认参数（调试好的通用值）
    public static boolean isEyeOpen(Mat grayFull, Rect eyeRect) {
        if (grayFull.channels() != 1) {
            System.out.println("gray通道：" + grayFull.channels());
            System.err.println("传入图像不是单通道灰度图");
            return false;
        }
        return isEyeOpen(grayFull, eyeRect, 0.1);
    }

    public static int eyeOpenNum(Mat src, RectVector eyes) {
        int num = 0;
        if (eyeDetector == null) throw new IllegalArgumentException("眼睛模型未加载,eyeDetector为null");
        try (AutoMat gray = new AutoMat(new Mat())) {
            opencv_imgproc.cvtColor(src, gray.get(), Imgproc.COLOR_BGR2GRAY);
            for (Rect rect : eyes.get()) {
                if (isEyeOpen(gray.get(), rect)) num++;
            }
        }
        return num;
    }

    public static void main(String[] args) throws IOException {


//        Path p1 = Paths.get(parent, "sushe2.jpg");
//        Path p2 = Paths.get(parent, "img-3565420dbc824e7aeb33dff66312b5e8.jpg");
//        Path p3 = Paths.get(parent, "微信图片_20250821224802_11912.jpg");
//        long sta = System.currentTimeMillis();
//        try (
//                AutoMat mat1 = pathToMat(p1);
//                AutoMat mat2 = pathToMat(p2);
//                AutoMat mat3 = pathToMat(p3);
//        ) {
//            RectVector vector1 = getFace(mat1.get());
//            RectVector vector2 = getFace(mat2.get());
//            RectVector vector3 = getFace(mat3.get());
//            try (
//                    AutoMat previewMat1 = new AutoMat(drawPreview(mat1.get(), vector1.get()));
//                    AutoMat previewMat2 = new AutoMat(drawPreview(mat2.get(), vector2.get()));
//                    AutoMat previewMat3 = new AutoMat(drawPreview(mat3.get(), vector3.get()));
//            ) {
//                // 保存预览图片
//                String previewPath1 = Paths.get("img", p1.getFileName().toString()).toString();
//                String previewPath2 = Paths.get("img", p2.getFileName().toString()).toString();
//                String previewPath3 = Paths.get("img", p3.getFileName().toString()).toString();
//                opencv_imgcodecs.imwrite(previewPath1, previewMat1.get());
//                opencv_imgcodecs.imwrite(previewPath2, previewMat2.get());
//                opencv_imgcodecs.imwrite(previewPath3, previewMat3.get());
//            }
//
//
//            System.out.println("识别数量:" + vector1.size()
////                    + " - 睁眼数量:" + eyeOpenNum(mat1.get(), vector1)
//            );
//            System.out.println("识别数量:" + vector2.size()
////                    + " - 睁眼数量:" + eyeOpenNum(mat2.get(), vector2)
//            );
//
//            System.out.println("识别数量:" + vector3.size()
////                    + " - 睁眼数量:" + eyeOpenNum(mat3.get(), vector3)
//            );
//        }
//        System.out.println("总耗时:"+(System.currentTimeMillis()-sta));

    }
}
