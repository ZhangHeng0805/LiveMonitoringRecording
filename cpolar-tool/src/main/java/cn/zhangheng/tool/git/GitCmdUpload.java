package cn.zhangheng.tool.git;

import java.io.File;

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
        new ProcessBuilder(cmd)
                .directory(new File(dir))
                .inheritIO()
                .start()
                .waitFor();
    }
}
