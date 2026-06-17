package cn.zhangheng.tool.bean;

import org.bytedeco.opencv.opencv_core.Mat;

// 包装类实现AutoCloseable
public class AutoMat implements AutoCloseable {
    private final Mat mat;

    public AutoMat(Mat mat) {
        this.mat = mat;
    }

    public Mat get() {
        return mat;
    }

    @Override
    public void close() {
        if (mat != null && !mat.isNull()) {
            mat.close();
        }
    }
}