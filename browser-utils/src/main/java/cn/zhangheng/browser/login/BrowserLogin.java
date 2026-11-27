package cn.zhangheng.browser.login;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/09/20 星期日 17:56
 * @version: 1.0
 * @description:
 */
public class BrowserLogin {
    /**
     * 登录脚本保存的 Cookie 文件后缀，与 cn.zhangheng.record.bean.Constant#CookieFileSuffix 保持一致
     */
    public static final String FileSuffix = ".cookie";
    public static final int maxWaitTimeSec = 360;

    public static Path writeCookieFile(String cookieStr, String fileName) {
        try {
            Path path = Paths.get("cookie", fileName + BrowserLogin.FileSuffix);
            Files.write(path, Collections.singletonList(cookieStr),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);
            return path;
        } catch (IOException e) {
            System.err.println("cookie保存失败: " + e.getMessage());
        }
        return null;
    }
}
