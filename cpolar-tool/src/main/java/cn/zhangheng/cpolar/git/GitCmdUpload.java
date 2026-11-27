package cn.zhangheng.cpolar.git;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/08 星期一 13:57
 * @version: 1.0
 * @description: 提交更新Git文件
 */
class GitCmdUpload {
    public static void main(String[] args) throws Exception {
        String repoPath = "F:\\Git Project\\LiveMonitoringRecordingPage";
        String filePath = "redirect/json/config.json";

        GitUtil.gitUploadFile(repoPath, filePath,"自动提交");
//        gitUploadFile(repoPath, filePath);

    }

    public static void gitUploadFile(String filePath, String fileName) throws Exception {
        // 执行 git 命令
        execCmd(filePath, "git", "add", fileName);
        execCmd(filePath, "git", "commit", "-m", "自动提交");
        execCmd(filePath, "git", "push");
    }


    // 执行命令工具方法
    private static void execCmd(String dir, String... cmd) throws Exception {
        Process process = new ProcessBuilder(cmd)
                .directory(new File(dir))
                .inheritIO()
                .start();
        // git push 可能因网络卡住，最多等 2 分钟，避免上传任务永久挂起
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new RuntimeException("命令执行超时(120s)：" + String.join(" ", cmd));
        }
    }
}
