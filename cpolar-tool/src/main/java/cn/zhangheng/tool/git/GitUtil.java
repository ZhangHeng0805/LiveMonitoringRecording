package cn.zhangheng.tool.git;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/10 星期三 17:35
 * @version: 1.0
 * @description:
 */
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class GitUtil {

    // 命令执行超时时间 30秒
    private static final long PROCESS_TIMEOUT = 30000L;

    /**
     * 提交并推送单个文件到远程git仓库
     * @param repoDir 仓库本地根目录
     * @param fileRelativePath 文件在仓库内相对路径/文件名
     * @throws GitOperateException git操作异常
     */
    public static void gitUploadFile(String repoDir, String fileRelativePath) throws GitOperateException {
        // 入参校验
        if (repoDir == null || repoDir.isEmpty()) {
            throw new GitOperateException("仓库目录不能为空");
        }
        if (fileRelativePath == null || fileRelativePath.isEmpty()) {
            throw new GitOperateException("提交文件名称不能为空");
        }

        File repoFolder = new File(repoDir);
        if (!repoFolder.exists() || !repoFolder.isDirectory()) {
            throw new GitOperateException("仓库目录不存在或不是文件夹：" + repoDir);
        }
        // 判断是否为git仓库（存在.git隐藏文件夹）
        File gitFolder = new File(repoFolder, ".git");
        if (!gitFolder.exists() || !gitFolder.isDirectory()) {
            throw new GitOperateException("目标目录不是Git仓库：" + repoDir);
        }

        // 1. git add 文件
        execCommand(repoDir, "git", "add", fileRelativePath);

        // 2. 检查是否有变更，无变更直接结束
        String changeInfo = getCommandOutput(repoDir, "git", "status", "--porcelain");
        if (changeInfo.isEmpty()) {
            System.out.println("无文件变更，无需提交推送");
            return;
        }

        // 3. commit提交
        CommandResult commitRes = runProcess(repoDir, "git", "commit", "-m", "自动提交：" + fileRelativePath);
        // 退出码1=无变更，属于正常场景
        if (commitRes.exitCode == 1 && commitRes.errorMsg.isEmpty()) {
            System.out.println("文件无实质修改，跳过commit");
            return;
        } else if (commitRes.exitCode != 0) {
            throw new GitOperateException("commit失败：" + commitRes.errorMsg);
        }

        // 4. push推送到远程
        execCommand(repoDir, "git", "push");
        System.out.println("文件[" + fileRelativePath + "]推送远程仓库成功");
    }

    /**
     * 执行命令，失败直接抛异常
     * @param workDir 工作目录
     * @param cmd 命令参数
     */
    private static void execCommand(String workDir, String... cmd) throws GitOperateException {
        CommandResult result = runProcess(workDir, cmd);
        if (result.exitCode != 0) {
            String errMsg = String.format(
                    "命令执行失败【%s】，退出码：%d，错误信息：%s",
                    String.join(" ", cmd),
                    result.exitCode,
                    result.errorMsg
            );
            throw new GitOperateException(errMsg);
        }
    }

    /**
     * 仅获取命令标准输出，不抛异常（用于status查询）
     */
    private static String getCommandOutput(String workDir, String... cmd) throws GitOperateException {
        CommandResult result = runProcess(workDir, cmd);
        return result.outputMsg.trim();
    }

    /**
     * 底层执行进程，捕获输出、错误、退出码、超时
     */
    private static CommandResult runProcess(String workDir, String... cmd) throws GitOperateException {
        File dir = new File(workDir);
        Process process = null;
        CommandResult result = new CommandResult();

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            // 分开流，方便读取日志
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            process = pb.start();

            // 异步读取标准输出、错误流，防止缓冲区阻塞卡死
            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());
            result.outputMsg = output;
            result.errorMsg = error;

            // 带超时等待进程结束
            boolean finished = process.waitFor(PROCESS_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitOperateException("命令执行超时(" + PROCESS_TIMEOUT / 1000 + "s)：" + String.join(" ", cmd));
            }
            result.exitCode = process.exitValue();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperateException("命令线程被中断：" + String.join(" ", cmd), e);
        } catch (Exception e) {
            throw new GitOperateException("启动进程异常：" + String.join(" ", cmd), e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }

    /**
     * 读取流文本内容
     */
    private static String readStream(InputStream inputStream) {
        if (inputStream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine()).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /**
     * 命令执行结果封装实体
     */
    private static class CommandResult {
        int exitCode = -1;
        String outputMsg = "";
        String errorMsg = "";
    }

    // ========== 扩展重载方法示例 ==========

    /**
     * 自定义提交备注
     */
    public static void gitUploadFile(String repoDir, String fileRelativePath, String commitMsg) throws GitOperateException {
        if (repoDir == null || repoDir.isEmpty()) throw new GitOperateException("仓库目录不能为空");
        if (fileRelativePath == null || fileRelativePath.isEmpty()) throw new GitOperateException("文件名称不能为空");
        File repoFolder = new File(repoDir);
        File gitFolder = new File(repoFolder, ".git");
        if (!repoFolder.exists() || !gitFolder.exists()) throw new GitOperateException("非法Git仓库");

        execCommand(repoDir, "git", "add", fileRelativePath);
        String change = getCommandOutput(repoDir, "git", "status", "--porcelain");
        if (change.isEmpty()) return;
        execCommand(repoDir, "git", "commit", "-m", commitMsg);
        execCommand(repoDir, "git", "push");
    }
}
