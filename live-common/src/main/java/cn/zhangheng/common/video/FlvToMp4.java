package cn.zhangheng.common.video;

import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/24 星期六 17:26
 * @version: 1.0
 * @description:
 */
public class FlvToMp4 extends FFmpegService {
    private static final Logger log = LoggerFactory.getLogger(FlvToMp4.class);
    private static LogUtil logUtil = null;

    public static void main(String[] args) {
        String input = "D:\\直播录屏\\bin\\out.flv";
        String output = "D:\\直播录屏\\bin\\out.mp4";
        new FlvToMp4().convert(input, output);
    }

    public FlvToMp4() {
        super(Constant.FFmpegExePath);
    }

    public FlvToMp4(String ffmpegExePath) {
        super(ffmpegExePath);
    }

    public boolean convert(String input, String output) {
        Path inputPath = Paths.get(input);
        try {
            if (!Files.exists(inputPath)) {
                throw new FileNotFoundException("未找到输入文件(Not Found Input File):" + input);
            }
            Files.deleteIfExists(Paths.get(output));
            if (logUtil == null) {
                String parent = inputPath.getParent().toString();
                try {
                    logUtil = new LogUtil(Paths.get(parent, "视频转换.log").toString());
                } catch (IOException e) {
                    log.error("视频转换日志生成失败" + ThrowableUtil.getAllCauseMessage(e));
                }
            }
            // 构建FFmpeg命令
            List<String> command = Arrays.asList("-y",
                    "-i", input,// 输入文件
                    "-c", "copy",// 直接复制
                    output // 输出文件
            );
            boolean run = run(command);
            String res = run ? "转换成功! " : "转换失败! ";
            logUtil.log(res + output);
            return run;
        } catch (IOException | InterruptedException e) {
            log.error("视频转换失败: {}", ThrowableUtil.getAllCauseMessage(e));
        }
        return false;
    }


    @Override
    protected void processResult(String logs) {
        try {
            logUtil.log(Collections.singleton(logs));
        } catch (IOException e) {
            log.error("视频转换日志记录失败: {}", ThrowableUtil.getAllCauseMessage(e));
        }
    }
}
