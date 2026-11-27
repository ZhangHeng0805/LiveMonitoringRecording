package cn.zhangheng.common.util;



/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/19 星期四 15:53
 * @version: 1.0
 * @description:
 */
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FileTrashUtil {

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    /**
     * Java8 版本：尝试移入回收站，失败则永久删除
     * @param path 文件/文件夹路径
     * @return true 处理成功；false 失败
     */
    public static boolean deleteOrMoveToTrash(Path path) throws IOException{
        if (path == null || !Files.exists(path)) {
            return true;
        }

        boolean sendTrashOk;
        if (isWindows()) {
            sendTrashOk = sendFileToTrashWindows(path);
        } else if (isMac()) {
            sendTrashOk = sendFileToTrashMac(path);
        } else if (isLinux()) {
            sendTrashOk = sendFileToTrashLinux(path);
        } else {
            sendTrashOk = false;
        }

        // 移入回收站失败，降级直接删除
        if (!sendTrashOk) {
            Files.deleteIfExists(path);
            return true;
        }
        return true;
    }

    // Windows 方案：调用 PowerShell 移入回收站
    private static boolean sendFileToTrashWindows(Path path) {
        String absPath = path.toAbsolutePath().toString();
        // PowerShell 命令，利用 Shell.Application 移至回收站
        String cmd = String.format(
                "powershell -Command \"$shell = New-Object -ComObject Shell.Application; $item = $shell.NameSpace('%s').ParseName('%s'); $item.InvokeVerb('delete')\"",
                path.getParent().toAbsolutePath().toString().replace("\"", "\\\""),
                path.getFileName().toString().replace("\"", "\\\"")
        );
        return runCmd(cmd);
    }

    // Mac 方案：osascript
    private static boolean sendFileToTrashMac(Path path) {
        String absPath = path.toAbsolutePath().toString();
        String cmd = String.format(
                "osascript -e 'tell application \"Finder\" to move POSIX file \"%s\" to trash'",
                absPath.replace("\"", "\\\"")
        );
        return runCmd(cmd);
    }

    // Linux桌面方案：trash-cli（需要提前安装：apt install trash-cli）
    private static boolean sendFileToTrashLinux(Path path) {
        String absPath = path.toAbsolutePath().toString();
        String cmd = "trash \"" + absPath.replace("\"", "\\\"") + "\"";
        return runCmd(cmd);
    }

    private static boolean runCmd(String cmd) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
            // 回收站命令也可能卡住（资源管理器/权限弹窗），最多等 10 秒
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            // 命令不存在、权限不足、无头环境都会进这里，返回false走降级删除
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static boolean isWindows() {
        return OS.contains("win");
    }
    private static boolean isMac() {
        return OS.contains("mac");
    }
    private static boolean isLinux() {
        return OS.contains("linux");
    }

    // 测试
    public static void main(String[] args) throws IOException {
        Path test = Paths.get("target/live-monitor-record-3.6.jar");
        boolean ret = deleteOrMoveToTrash(test);
        System.out.println("结果：" + ret);
    }
}
