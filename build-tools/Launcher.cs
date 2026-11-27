using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace XingXiLauncher
{
    /// <summary>
    /// 星曦向荣直播监听 外层启动器（无控制台）。
    /// 职责：定位内置 JRE 与 fat jar、单实例检查、首次运行安装监听浏览器、以 FileModeMain 启动并接管日志。
    /// </summary>
    internal static class Program
    {
        private const string AppTitle = "星曦向荣直播监听";
        private const string MainClass = "cn.zhangheng.lmr.FileModeMain";
        private const string JvmArgs =
            "-Xmx512m -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 " +
            "-Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -Dsun.awt.disablegrab=true " +
            "-Dsun.awt.noerasebackground=true -Dsun.awt.enableExtraMouseInfo=false -Djava.awt.suppressSwingDropSupport=true";

        [STAThread]
        private static void Main(string[] args)
        {
            try
            {
                Run(args);
            }
            catch (Exception ex)
            {
                MessageBox.Show("启动失败：" + Environment.NewLine + ex.Message, AppTitle,
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static string BaseDir
        {
            get { return AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\'); }
        }

        private static void Run(string[] args)
        {
            string baseDir = BaseDir;
            string jar = FindJar(baseDir);
            if (jar == null)
            {
                MessageBox.Show("未找到程序文件：lib\\live-monitor-record-*.jar" + Environment.NewLine +
                                "请确认压缩包已完整解压，不要只把 exe 单独拷出来。", AppTitle,
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string javaExe = FindJava(baseDir);
            if (javaExe == null)
            {
                MessageBox.Show("未找到内置 Java 运行环境（jre 目录）。" + Environment.NewLine +
                                "请确认整个文件夹已完整解压后再启动。", AppTitle,
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string logsDir = Path.Combine(baseDir, "logs");
            try { Directory.CreateDirectory(logsDir); }
            catch { }

            string pidFile = Path.Combine(logsDir, "jvm.pid");
            int runningPid = FindRunningPid(pidFile);
            if (runningPid > 0)
            {
                int port = ReadPort(baseDir);
                MessageBox.Show("程序已经在运行了（进程 " + runningPid + "），不用重复启动。" + Environment.NewLine +
                                "现在为你打开监听面板。", AppTitle,
                    MessageBoxButtons.OK, MessageBoxIcon.Information);
                OpenUrl("http://localhost:" + port + "/");
                return;
            }

            if (!HasBrowser())
            {
                DialogResult answer = MessageBox.Show(
                    "首次使用需要下载「监听浏览器」组件（约 150MB，需要联网）。" + Environment.NewLine + Environment.NewLine +
                    "现在自动下载吗？下载过程会弹出窗口显示进度，几分钟内完成。",
                    "首次运行", MessageBoxButtons.YesNo, MessageBoxIcon.Question);
                if (answer != DialogResult.Yes)
                {
                    MessageBox.Show("可以稍后双击目录里的「安装监听浏览器.bat」完成安装，再启动本程序。", AppTitle,
                        MessageBoxButtons.OK, MessageBoxIcon.Information);
                    return;
                }
                InstallBrowser(javaExe, jar, baseDir);
                if (!HasBrowser())
                {
                    MessageBox.Show("浏览器组件仍未安装成功，请检查网络后重试，或手动运行「安装监听浏览器.bat」。", AppTitle,
                        MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }
            }

            Launch(javaExe, jar, baseDir, pidFile);
        }

        private static string FindJar(string baseDir)
        {
            string libDir = Path.Combine(baseDir, "lib");
            if (!Directory.Exists(libDir)) return null;
            string fixedJar = Path.Combine(libDir, "live-monitor-record-3.7.jar");
            if (File.Exists(fixedJar)) return fixedJar;
            string[] jars = Directory.GetFiles(libDir, "live-monitor-record-*.jar");
            if (jars.Length == 0) return null;
            Array.Sort(jars, delegate(string a, string b)
            {
                return File.GetLastWriteTime(b).CompareTo(File.GetLastWriteTime(a));
            });
            return jars[0];
        }

        private static string FindJava(string baseDir)
        {
            // 优先用发行包里改名过的 java 启动器：任务管理器里显示为“直播监听内核.exe”，不再是 javaw.exe
            string renamed = Path.Combine(baseDir, "jre\\bin\\直播监听内核.exe");
            if (File.Exists(renamed)) return renamed;
            string javaw = Path.Combine(baseDir, "jre\\bin\\javaw.exe");
            if (File.Exists(javaw)) return javaw;
            string java = Path.Combine(baseDir, "jre\\bin\\java.exe");
            if (File.Exists(java)) return java;
            // 兜底：用系统环境里的 Java（用户自己装了 JDK8+ 时仍可运行）
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string homeJavaw = Path.Combine(javaHome, "bin\\javaw.exe");
                if (File.Exists(homeJavaw)) return homeJavaw;
            }
            return "javaw.exe";
        }

        private static string BrowserDir()
        {
            string custom = Environment.GetEnvironmentVariable("PLAYWRIGHT_BROWSERS_PATH");
            if (!string.IsNullOrEmpty(custom) && custom != "0") return custom;
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ms-playwright");
        }

        private static bool HasBrowser()
        {
            try
            {
                string dir = BrowserDir();
                if (!Directory.Exists(dir)) return false;
                return Directory.GetDirectories(dir, "chromium-*").Length > 0 ||
                       Directory.GetDirectories(dir, "chromium_headless_shell-*").Length > 0;
            }
            catch
            {
                return false;
            }
        }

        private static void InstallBrowser(string javaExe, string jar, string baseDir)
        {
            string inner = "\"" + javaExe + "\" -cp \"" + jar + "\" com.microsoft.playwright.CLI install chromium";
            string cmdLine = "/c \"" + inner + " & echo. & echo 安装结束，按任意键关闭窗口 & pause\"";
            ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", cmdLine);
            psi.WorkingDirectory = baseDir;
            psi.UseShellExecute = true;
            Process p = Process.Start(psi);
            p.WaitForExit();
        }

        private static int FindRunningPid(string pidFile)
        {
            try
            {
                if (!File.Exists(pidFile)) return 0;
                string text = File.ReadAllText(pidFile).Trim();
                int pid = int.Parse(text);
                Process p = Process.GetProcessById(pid);
                string name = p.ProcessName.ToLowerInvariant();
                if (name.Contains("java")) return pid;
            }
            catch
            {
                // 进程已退出或无权访问，按未运行处理
            }
            return 0;
        }

        private static int ReadPort(string baseDir)
        {
            int port = 8005;
            try
            {
                string setting = Path.Combine(baseDir, "xxxr.setting");
                if (File.Exists(setting))
                {
                    foreach (string raw in File.ReadAllLines(setting, Encoding.UTF8))
                    {
                        string line = raw.Trim();
                        if (line.StartsWith("#") || !line.Contains("=")) continue;
                        string key = line.Substring(0, line.IndexOf('=')).Trim();
                        string val = line.Substring(line.IndexOf('=') + 1).Trim();
                        if (key == "server.monitor.port" && val.Length > 0)
                        {
                            port = int.Parse(val);
                            break;
                        }
                    }
                }
            }
            catch { }
            return port;
        }

        private static void OpenUrl(string url)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo(url);
                psi.UseShellExecute = true;
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show("打开浏览器失败，请手动访问：" + url + Environment.NewLine + ex.Message, AppTitle,
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private static void Launch(string javaExe, string jar, string baseDir, string pidFile)
        {
            string arguments = JvmArgs + " -cp \"" + jar + "\" " + MainClass + " ./room";
            ProcessStartInfo psi = new ProcessStartInfo(javaExe, arguments);
            psi.WorkingDirectory = baseDir;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.StandardOutputEncoding = Encoding.UTF8;
            psi.StandardErrorEncoding = Encoding.UTF8;

            Process process = Process.Start(psi);
            try { File.WriteAllText(pidFile, process.Id.ToString()); }
            catch { }

            StreamWriter log = null;
            try
            {
                FileStream fs = new FileStream(Path.Combine(baseDir, "logs", "launcher.log"),
                    FileMode.Append, FileAccess.Write, FileShare.ReadWrite);
                log = new StreamWriter(fs, new UTF8Encoding(false));
            }
            catch { }

            if (log != null)
            {
                lock (log)
                {
                    log.WriteLine("[" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "] 启动 " + Path.GetFileName(jar) + "，pid=" + process.Id);
                    log.Flush();
                }
            }

            StartPump(process.StandardOutput, log, "OUT");
            StartPump(process.StandardError, log, "ERR");

            Thread waiter = new Thread(delegate()
            {
                try { process.WaitForExit(); }
                catch { }
                try
                {
                    string text = File.Exists(pidFile) ? File.ReadAllText(pidFile).Trim() : "";
                    if (text == process.Id.ToString()) File.Delete(pidFile);
                }
                catch { }
                if (log != null)
                {
                    lock (log)
                    {
                        log.WriteLine("[" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "] 进程已退出，退出码 " + process.ExitCode);
                        log.Flush();
                    }
                }
            });
            waiter.IsBackground = true;
            waiter.Start();
        }

        private static void StartPump(StreamReader reader, StreamWriter log, string tag)
        {
            Thread t = new Thread(delegate()
            {
                try
                {
                    string line;
                    while ((line = reader.ReadLine()) != null)
                    {
                        if (log == null) continue;
                        lock (log)
                        {
                            log.WriteLine("[" + DateTime.Now.ToString("HH:mm:ss") + "] [" + tag + "] " + line);
                            log.Flush();
                        }
                    }
                }
                catch { }
            });
            t.IsBackground = true;
            t.Start();
        }
    }
}
