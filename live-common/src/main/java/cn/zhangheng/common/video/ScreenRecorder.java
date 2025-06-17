package cn.zhangheng.common.video;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/17 星期二 05:11
 * @version: 1.0
 * @description: 屏幕录制
 */

import cn.zhangheng.common.bean.Constant;
import com.zhangheng.util.ThrowableUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ScreenRecorder extends FFmpegService {
    private final String osName;

    // 录制参数
    private int width;
    private int height;
    private int x;
    private int y;
    private int frameRate;
    private String outputPath;
    private String windowTitle;
    private boolean recordMicrophone;
    private boolean recordSystemAudio;

    /**
     * 初始化屏幕录制器
     *
     * @param ffmpegPath FFmpeg可执行文件路径（如未配置环境变量）
     */
    public ScreenRecorder(String ffmpegPath) {
        super(ffmpegPath);
        this.osName = System.getProperty("os.name").toLowerCase();
        this.frameRate = 30;
        this.recordMicrophone = false;       // 默认不录制麦克风
        this.recordSystemAudio = true;       // 默认录制系统声音
    }

    @Override
    protected void processResult(String logs) {
        System.out.println(logs);
    }

    // 设置录制参数
    public ScreenRecorder setFrameRate(int frameRate) {
        this.frameRate = frameRate;
        return this;
    }

    /**
     * 输出文件路径
     *
     * @param outputPath
     * @return
     */
    public ScreenRecorder setOutputPath(String outputPath) {
        if (outputPath.endsWith(".mp4")) {
            this.outputPath = outputPath;
        } else {
            this.outputPath = outputPath + ".mp4";
        }
        return this;
    }

    public ScreenRecorder setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
        return this;
    }

    /**
     * 设置录制区域
     *
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public ScreenRecorder setRegion(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * 设置是否录制麦克风
     */
    public ScreenRecorder setRecordMicrophone(boolean recordMicrophone) {
        this.recordMicrophone = recordMicrophone;
        return this;
    }

    /**
     * 设置是否录制系统声音
     */
    public ScreenRecorder setRecordSystemAudio(boolean recordSystemAudio) {
        this.recordSystemAudio = recordSystemAudio;
        return this;
    }

    public void start(String outputPath) throws IOException {
        setOutputPath(outputPath);
        start();
    }

    /**
     * 开始录制
     */
    public void start() throws IOException {
        if (outputPath == null) {
            throw new IllegalStateException("请先设置输出路径");
        }
        try {
            run(Arrays.asList(buildFFmpegCommand()));
        } catch (InterruptedException e) {
            log.error("屏幕录制失败: {}", ThrowableUtil.getAllCauseMessage(e));
        }

    }

    /**
     * 停止录制
     */
    public void stop() {
        stop(false);
    }

    /**
     * 根据操作系统构建FFmpeg命令
     */
    private String[] buildFFmpegCommand() {
        String[] command;

        if (osName.contains("win")) {
            // Windows系统
            command = buildWindowsScreenCommand();
        } else if (osName.contains("mac")) {
            // macOS系统
            command = buildMacScreenCommand();
        } else {
            // Linux系统
            command = buildLinuxScreenCommand();
        }

//        System.out.println("执行命令: " + Arrays.toString(command));
        return command;
    }

    // Windows全屏命令构建
    private String[] buildWindowsScreenCommand() {
        String[] baseCommand;
        if (width > 0 && height > 0) {
            //指定区域
            baseCommand = new String[]{
                    "-y",
                    "-f", "gdigrab",
                    "-framerate", String.valueOf(frameRate),
                    "-offset_x", String.valueOf(x),  // 区域左上角X坐标
                    "-offset_y", String.valueOf(y),  // 区域左上角Y坐标
                    "-video_size", width + "x" + height,  // 区域尺寸
                    "-i", "desktop",
            };
        } else if (windowTitle != null) {
            //指定窗口，目前有点问题
            baseCommand = new String[]{
                    "-y",
                    "-f", "gdigrab",
                    "-framerate", String.valueOf(frameRate),
                    "-i", "title=" + escapeWindowTitle(windowTitle),  // 指定窗口标题
            };
        } else {
            //全屏录制
            baseCommand = new String[]{
                    "-y",
                    "-f", "gdigrab",
                    "-framerate", String.valueOf(frameRate),
                    "-i", "desktop"
            };
        }

        // 音频捕获部分
        String[] audioOptions;
        if (recordSystemAudio && recordMicrophone) {
            // 同时录制系统声音和麦克风（需要虚拟音频设备）
            audioOptions = new String[]{
                    "-f", "dshow",
                    "-i", "audio=立体声混音 (Realtek High Definition Audio)", // 系统声音
                    "-f", "dshow",
                    "-i", "audio=麦克风 (Realtek High Definition Audio)", // 麦克风
                    "-filter_complex", "[1:a][2:a]amix=inputs=2", // 混合两路音频
                    "-c:a", "aac"
            };
        } else if (recordSystemAudio) {
            // 仅录制系统声音
            audioOptions = new String[]{
                    "-f", "dshow",
                    "-i", "audio=立体声混音 (Realtek High Definition Audio)",
                    "-c:a", "aac"
            };
        } else if (recordMicrophone) {
            // 仅录制麦克风
            audioOptions = new String[]{
                    "-f", "dshow",
                    "-i", "audio=麦克风 (Realtek High Definition Audio)",
                    "-c:a", "aac"
            };
        } else {
            // 无音频
            audioOptions = new String[]{"-an"};
        }

        // 合并所有选项
        return concatArrays(baseCommand, audioOptions, getOutputOptions(outputPath));
    }

    public String[] getOutputOptions(String outputPath) {
        return new String[]{
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-movflags", "+faststart+frag_keyframe+empty_moov", // 优化 MP4 文件结构
                "-flush_packets", "1", // 强制刷新数据包
                outputPath
        };
    }

    // macOS全屏命令构建
    private String[] buildMacScreenCommand() {
        String[] baseCommand;
        if (width > 0 && height > 0) {
            baseCommand = new String[]{
                    "-y",
                    "-f", "avfoundation",
                    "-framerate", String.valueOf(frameRate),
                    "-offset_x", String.valueOf(x),  // 区域左上角X坐标
                    "-offset_y", String.valueOf(y),  // 区域左上角Y坐标
                    "-video_size", width + "x" + height,  // 区域尺寸
                    "-i", "1:0",  // 1=屏幕, 0=麦克风
            };
        } else {
            baseCommand = new String[]{
                    "-y",
                    "-f", "avfoundation",
                    "-framerate", String.valueOf(frameRate),
                    "-i", "1:0"  // 1=屏幕, 0=麦克风
            };
        }

        // 音频捕获部分
        String[] audioOptions;
        if (recordSystemAudio && recordMicrophone) {
            // 同时录制系统声音和麦克风（需要BlackHole）
            audioOptions = new String[]{
                    "-f", "avfoundation",
                    "-i", ":1",  // 系统声音（BlackHole设备）
                    "-filter_complex", "[0:a][1:a]amix=inputs=2",
                    "-c:a", "aac"
            };
        } else if (recordSystemAudio) {
            // 仅录制系统声音
            audioOptions = new String[]{
                    "-f", "avfoundation",
                    "-i", ":1",  // 系统声音
                    "-map", "1:a",
                    "-c:a", "aac"
            };
        } else if (recordMicrophone) {
            // 仅录制麦克风（默认选项）
            audioOptions = new String[]{
                    "-map", "0:a",
                    "-c:a", "aac"
            };
        } else {
            // 无音频
            audioOptions = new String[]{"-an"};
        }


        return concatArrays(baseCommand, audioOptions, getOutputOptions(outputPath));
    }

    // Linux全屏命令构建
    private String[] buildLinuxScreenCommand() {
        String[] baseCommand;
        if (width > 0 && height > 0) {
            baseCommand = new String[]{
                    "-y",
                    "-f", "x11grab",
                    "-framerate", String.valueOf(frameRate),
                    "-offset_x", String.valueOf(x),  // 区域左上角X坐标
                    "-offset_y", String.valueOf(y),  // 区域左上角Y坐标
                    "-video_size", width + "x" + height,  // 区域尺寸
                    "-i", ":0.0"  // X11显示设备
            };
        } else {
            baseCommand = new String[]{
                    "-y",
                    "-f", "x11grab",
                    "-framerate", String.valueOf(frameRate),
                    "-i", ":0.0"  // X11显示设备
            };
        }
        // 音频捕获部分
        String[] audioOptions;
        if (recordSystemAudio && recordMicrophone) {
            // 同时录制系统声音和麦克风
            audioOptions = new String[]{
                    "-f", "pulse",
                    "-i", "default",  // 系统声音
                    "-f", "pulse",
                    "-i", "alsa_input.pci-0000_00_1b.0.analog-stereo",  // 麦克风
                    "-filter_complex", "[0:a][1:a]amix=inputs=2",
                    "-c:a", "aac"
            };
        } else if (recordSystemAudio) {
            // 仅录制系统声音
            audioOptions = new String[]{
                    "-f", "pulse",
                    "-i", "default",  // 系统声音
                    "-c:a", "aac"
            };
        } else if (recordMicrophone) {
            // 仅录制麦克风
            audioOptions = new String[]{
                    "-f", "pulse",
                    "-i", "alsa_input.pci-0000_00_1b.0.analog-stereo",  // 麦克风
                    "-c:a", "aac"
            };
        } else {
            // 无音频
            audioOptions = new String[]{"-an"};
        }
        return concatArrays(baseCommand, audioOptions, getOutputOptions(outputPath));
    }

    // 数组合并工具方法
    private String[] concatArrays(String[]... arrays) {
        int totalLength = 0;
        for (String[] arr : arrays) {
            totalLength += arr.length;
        }

        String[] result = new String[totalLength];
        int currentIndex = 0;

        for (String[] arr : arrays) {
            System.arraycopy(arr, 0, result, currentIndex, arr.length);
            currentIndex += arr.length;
        }

        return result;
    }

    // 列出系统可用的音频设备
    public void listAudioDevices() throws IOException, InterruptedException {
        List<String> command;
        if (osName.contains("win")) {
            command = Arrays.asList("-list_devices", "true", "-f", "dshow", "-i", "dummy");
        } else if (osName.contains("mac")) {
            command = Arrays.asList("-list_devices", "true", "-f", "avfoundation", "-i", "dummy");
        } else {
            command = Arrays.asList("-list_devices", "true", "-f", "pulse", "-i", "dummy");
        }
        FFmpegService service = new FFmpegService(ffmpegExePath) {
            @Override
            protected void processResult(String logs) {
                int ri = logs.indexOf("] \"");
                if (ri > 0) {
                    System.out.println(logs.substring(ri + 2));
                }
            }
        };
        service.run(command);
    }

    // 转义窗口标题中的特殊字符
    private String escapeWindowTitle(String title) {
        return title.replace(":", "\\:")
                .replace("\\", "\\\\")
                .replace(",", "\\,");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ScreenRecorder screenRecorder = new ScreenRecorder(Constant.FFmpegExePath);
        screenRecorder.listAudioDevices();
    }
}
