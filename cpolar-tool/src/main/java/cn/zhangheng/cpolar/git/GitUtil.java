package cn.zhangheng.cpolar.git;

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
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class GitUtil {

    // 命令执行超时时间 30秒
    private static final long PROCESS_TIMEOUT = 30_000L;

    // 流读取最大字符数（防止输出过大撑爆内存）
    private static final int MAX_STREAM_OUTPUT_SIZE = 1024 * 128;

    // 按仓库路径加锁，防止多线程并发操作同一个本地git仓库（避免 index.lock 冲突）
    private static final ConcurrentHashMap<String, ReentrantLock> REPO_LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * 提交并推送单个文件到远程git仓库（使用默认commit message）
     * @param repoDir 仓库本地根目录
     * @param fileRelativePath 文件在仓库内相对路径/文件名
     */
    public static void gitUploadFile(String repoDir, String fileRelativePath) throws GitOperateException {
        gitUploadFile(repoDir, fileRelativePath, "自动提交：" + fileRelativePath);
    }

    /**
     * 提交并推送单个文件到远程git仓库（自定义commit message，主方法）
     * @param repoDir       仓库本地根目录
     * @param fileRelativePath 文件在仓库内相对路径/文件名
     * @param commitMsg     提交备注
     */
    public static void gitUploadFile(String repoDir, String fileRelativePath, String commitMsg) throws GitOperateException {
        // ===== 入参统一校验 =====
        if (repoDir == null || repoDir.isEmpty()) {
            throw new GitOperateException("仓库目录不能为空");
        }
        if (fileRelativePath == null || fileRelativePath.isEmpty()) {
            throw new GitOperateException("提交文件名称不能为空");
        }
        if (commitMsg == null || commitMsg.isEmpty()) {
            throw new GitOperateException("提交备注不能为空");
        }

        File repoFolder = new File(repoDir);
        if (!repoFolder.exists() || !repoFolder.isDirectory()) {
            throw new GitOperateException("仓库目录不存在或不是文件夹：" + repoDir);
        }
        File gitFolder = new File(repoFolder, ".git");
        if (!gitFolder.exists() || !gitFolder.isDirectory()) {
            throw new GitOperateException("目标目录不是Git仓库：" + repoDir);
        }

        // ===== 按仓库加锁，防止并发操作同一个git仓库 =====
        ReentrantLock lock = REPO_LOCK_MAP.computeIfAbsent(repoFolder.getAbsolutePath(), k -> new ReentrantLock());
        // tryLock：拿不到锁说明有另一个任务在跑，直接快速失败，不阻塞等待
        if (!lock.tryLock()) {
            throw new GitOperateException("仓库[" + repoDir + "]正在执行git操作，请勿重复提交");
        }

        try {
            // 1. git add 文件
            execCommand(repoDir, "git", "add", fileRelativePath);

            // 2. 检查是否有变更，无变更直接结束
            String changeInfo = getCommandOutput(repoDir, "git", "status", "--porcelain");
            if (changeInfo.isEmpty()) {
                System.out.println("无文件变更，无需提交推送");
                return;
            }

            // 3. commit提交（单独处理 exitCode=1 无变更的正常场景）
            CommandResult commitRes = runProcess(repoDir, "git", "commit", "-m", commitMsg);
            if (commitRes.exitCode == 1 && commitRes.errorMsg.isEmpty()) {
                System.out.println("文件无实质修改，跳过commit");
                return;
            } else if (commitRes.exitCode != 0) {
                throw new GitOperateException("commit失败：" + commitRes.errorMsg);
            }

            // 4. push推送到远程
            execCommand(repoDir, "git", "push");
            System.out.println("文件[" + fileRelativePath + "]推送远程仓库成功");

        } finally {
            lock.unlock();
        }
    }

    /**
     * 执行命令，失败直接抛异常
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
     * 底层执行进程：并行读取 stdout/stderr 防止缓冲区死锁，带超时控制
     */
    private static CommandResult runProcess(String workDir, String... cmd) throws GitOperateException {
        File dir = new File(workDir);
        final Process[] processHolder = new Process[1];
        CommandResult result = new CommandResult();
        ExecutorService streamExecutor = Executors.newFixedThreadPool(2);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            processHolder[0] = pb.start();
            Process process = processHolder[0];

            // 并行异步读取 stdout、stderr，避免串行读取导致缓冲区满死锁
            Future<String> outFuture = streamExecutor.submit(() -> readStream(processHolder[0].getInputStream()));
            Future<String> errFuture = streamExecutor.submit(() -> readStream(processHolder[0].getErrorStream()));

            // 等待进程退出（30s超时）
            boolean finished = process.waitFor(PROCESS_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitOperateException("命令执行超时(" + PROCESS_TIMEOUT / 1000 + "s)：" + String.join(" ", cmd));
            }

            // 获取输出结果，额外加2s流读取超时，防止流读取卡死
            result.outputMsg = outFuture.get(2, TimeUnit.SECONDS);
            result.errorMsg = errFuture.get(2, TimeUnit.SECONDS);
            result.exitCode = process.exitValue();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitOperateException("命令线程被中断：" + String.join(" ", cmd), e);
        } catch (TimeoutException e) {
            throw new GitOperateException("读取进程输出流超时：" + String.join(" ", cmd), e);
        } catch (Exception e) {
            throw new GitOperateException("启动进程异常：" + String.join(" ", cmd), e);
        } finally {
            streamExecutor.shutdown();
            if (processHolder[0] != null && processHolder[0].isAlive()) {
                processHolder[0].destroy();
            }
        }
        return result;
    }

    /**
     * 读取流文本内容，带最大长度限制
     */
    private static String readStream(InputStream inputStream) {
        if (inputStream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (sb.length() + line.length() + System.lineSeparator().length() > MAX_STREAM_OUTPUT_SIZE) {
                    sb.append("【输出截断，超出最大长度限制】");
                    break;
                }
                sb.append(line).append(System.lineSeparator());
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
}
