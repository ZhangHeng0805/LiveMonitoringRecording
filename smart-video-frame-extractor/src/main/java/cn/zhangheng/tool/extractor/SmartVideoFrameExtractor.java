package cn.zhangheng.tool.extractor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/11 星期四 00:38
 * @version: 1.0
 * @description:
 */

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.zhangheng.tool.bean.FrameScoreResult;
import com.zhangheng.file.FileUtil;
import lombok.Getter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static cn.zhangheng.tool.util.OpenCvUtil.IMG_SUFFIX;

/**
 * Java8 + FFmpeg + OpenCV 智能关键帧提取
 * 终极修复：彻底删除 mean/val/meanStdDev 所有不存在API
 * 适配版本：opencv 4.6.0-1.5.8 / JDK8 零编译报错
 */
public class SmartVideoFrameExtractor {
    // ====================== 可自定义阈值参数 ======================
    //清晰度（拉普拉斯方差阈值）
    private static final double LAPLACE_CLEAR_THRESHOLD = 7000.0;
    //黑屏最大占比
    private static final double BLACK_RATIO_LIMIT = 0.80;
    //色彩丰富度最低分值
    private static final double COLOR_VAR_MIN = 2800.0;
    //抽帧时间间隔 单位：秒
    private static final int FRAME_STEP_SECOND = 2;
    private static final int THUMBNAIL = 10;
    //最终保存最大关键帧总数
    private static final int MAX_SAVE_FRAME = 50;
    //线程池并发数
    private static final int THREAD_POOL_SIZE = 8;
    //PHash哈希相似度汉明距离阈值（去重，越小越严）
    private static final int PHASH_SIMILAR_THRESHOLD = 1;

    // ============================================================

    @Getter
    private final String videoPath, tempFrameDir, resultDir;
    @Getter
    private final ExecutorService executor;
    private final Set<String> savedFrameHashSet = new ConcurrentHashSet<>();
    private static Process proc;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopExtractRawFrame();
        }));
    }

    public static void stopExtractRawFrame() {
        if (proc != null && proc.isAlive()) {
            proc.destroyForcibly();
            try {
                proc.waitFor();
            } catch (InterruptedException ignored) {
            }
        }
    }


    public SmartVideoFrameExtractor(String videoFilePath) {
        Path parent = Paths.get(videoFilePath).getParent();
        String tempDir = Paths.get(parent.toString(), "video_temp_frames").toString();
        String resultDir = Paths.get(parent.toString(), "video_high_quality_frames").toString();
        this.videoPath = videoFilePath;
        this.tempFrameDir = tempDir;
        this.resultDir = resultDir;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mkdir(tempFrameDir);
        mkdir(resultDir);
    }

    public SmartVideoFrameExtractor(String video, String tempDir, String resDir) {
        this.videoPath = video;
        this.tempFrameDir = tempDir;
        this.resultDir = resDir;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mkdir(tempFrameDir);
        mkdir(resultDir);
    }

    private void mkdir(String path) {
        FileUtil.mkdir(path);
    }

    // FFmpeg 抽帧
    public boolean ffmpegExtractRawFrame() throws IOException, InterruptedException {
        String framePattern = tempFrameDir + File.separator + "raw_%06d"+IMG_SUFFIX;
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(videoPath);
        cmd.add("-vf");
        cmd.add("blackdetect=black_min_duration=0.3:picture_black_ratio_th=0.9,fps=1/" + FRAME_STEP_SECOND + ",thumbnail=n="+THUMBNAIL);
        cmd.add("-q:v");
        cmd.add("0");
        cmd.add(framePattern);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);// 合并错误流
        proc = pb.start();
        consumeStreamThread(proc).start();
        int code = proc.waitFor();
        if (code != 0) {
            System.err.println("FFmpeg抽帧失败，退出码:" + code);
            return false;
        }
        System.out.println("✅ FFmpeg 原始帧抽取完成");
        return true;
    }

    // 生成吞流线程
    private Thread consumeStreamThread(Process process) {
        return new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // 可注释打印，生产环境不打印减少开销
                    // System.out.printf("[%s] %s%n", tag, line);
                }
            } catch (IOException e) {
                // 流关闭正常忽略
            }
        });
    }

    // 异步多线程筛选
    public void asyncFilterHighQualityFrame() throws ExecutionException, InterruptedException, IOException {
        File tempFolder = new File(tempFrameDir);
        List<Path> rawFrames = Arrays.stream(Objects.requireNonNull(tempFolder.listFiles()))
                .filter(f -> f.getName().endsWith(IMG_SUFFIX))
                .sorted(Comparator.comparing(File::getName))
                .map(File::toPath)
                .collect(Collectors.toList());
        System.out.println("原始帧抽取" + rawFrames.size() + "张");

        List<CompletableFuture<FrameScoreResult>> futureList = rawFrames.stream()
                .map(frameFile -> CompletableFuture.supplyAsync(() -> FrameScoreResult.scoreFrame(frameFile), executor))
                .collect(Collectors.toList());

        List<FrameScoreResult> validFrameList = new ArrayList<>();
        for (CompletableFuture<FrameScoreResult> future : futureList) {
            FrameScoreResult result = future.get();
            if (result.isValid()) {
                validFrameList.add(result);
            }
        }

        // 综合得分排序
        List<FrameScoreResult> sortedFrames = validFrameList.stream()
                .sorted((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()))
                .limit(MAX_SAVE_FRAME)
                .collect(Collectors.toList());

        int saveCount = 0;
        for (FrameScoreResult frame : sortedFrames) {
            if (isSimilarFrame(frame.getHash())) continue;
            saveCount++;
            String outName = String.format("key_%04d_score_%.2f"+IMG_SUFFIX, saveCount, frame.getTotalScore());
            Path target = Paths.get(resultDir, outName);
            Files.copy(frame.getFrame(), target);
            System.out.printf("🎉 保存优质帧 %s | 得分:%.2f 有人脸:%b%n", outName, frame.getTotalScore(), frame.getHash());
        }

        System.out.println("\n🎯 筛选完成！共保留关键帧：" + saveCount + "/" + validFrameList.size());
        executor.shutdown();
        cleanTemp(rawFrames);
    }


    // 单帧打分
//    public FrameScoreResult scoreFrame(File frameFile) {
//        Mat mat = OpenCvUtil.pathToMat(frameFile.toPath());
//        if (mat == null || mat.empty()) {
//            return new FrameScoreResult(frameFile, false, 0, "");
//        }
//
//        //清晰度评分
//        double lapScore = OpenCvUtil.fastBlurScore(mat);
//        if (lapScore < LAPLACE_CLEAR_THRESHOLD) {
//            mat.release();
//            return new FrameScoreResult(frameFile, false, 0, false, "");
//        }
        // 黑屏占比计算
//        double blackRatio = OpenCvUtil.calcBlackPixelRatio(mat);
//        if (blackRatio > BLACK_RATIO_LIMIT) {
//            mat.release();
//            return new FrameScoreResult(frameFile, false, 0, false, "");
//        }
        //色彩丰富度
//        double colorVar = OpenCvUtil.calcColorVariance(mat);
//        if (colorVar < COLOR_VAR_MIN) {
//            mat.release();
//            return new FrameScoreResult(frameFile, false, 0, false, "");
//        }
        //人脸检测
//        double faceBonus = detectFace(mat);
        //PHash感知哈希
//        String aHash = OpenCvUtil.calcAHash(mat);

//        System.out.println((sta2-sta1)+"-"+(sta3-sta2)+"-"+(sta4-sta3)+"-"+(sta5-sta4)+"-"+(sta6-sta5)+"-"+(sta7-sta6));
//        System.out.println(frameFile.getName() + " : " + MathUtil.numberFormat(lapScore) + " - " + MathUtil.numberFormat(colorVar) + " - " + MathUtil.numberFormat(blackRatio) + " - " + faceBonus);

//        double totalScore = (lapScore / 10_000_000);
//
//        mat.release();
//        return new FrameScoreResult(frameFile, true, totalScore, aHash);
//    }

    // 哈希去重
    public synchronized boolean isSimilarFrame(String newHash) {
        for (String oldHash : savedFrameHashSet) {
            int diff = calcHammingDistance(newHash, oldHash);
            if (diff <= PHASH_SIMILAR_THRESHOLD) return true;
        }
        savedFrameHashSet.add(newHash);
        return false;
    }

    private int calcHammingDistance(String s1, String s2) {
        int diff = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) diff++;
        }
        return diff;
    }

    // 人脸检测
//    private double detectFace(Mat src) {
//        long faceNum = OpenCvUtil.getFace(src).size();
//        double score = faceNum > 0 ? 50 : 0;
//        //每个人脸加分
//        score += faceNum;
//        return score;
//    }


    // 清空临时文件
    private void cleanTemp(List<Path> files) {
        files.forEach(p->{
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        });
        System.out.println("✅ 临时帧文件已清空");
    }

    // 执行入口
    public void run() throws IOException, ExecutionException, InterruptedException {
        if (ffmpegExtractRawFrame()) {
            asyncFilterHighQualityFrame();
        }
    }
}


