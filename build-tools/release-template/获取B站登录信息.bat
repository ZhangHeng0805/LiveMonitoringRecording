@echo off
chcp 936 >nul
title 获取B站登录信息
cd /d "%~dp0"
if not exist "jre\bin\java.exe" (
  echo [错误] 未找到 jre\bin\java.exe，请确认已完整解压后再运行。
  pause
  exit /b 1
)
set "JAR="
for %%f in ("lib\live-monitor-record-*.jar") do if exist "%%~f" set "JAR=%%~f"
if not exist "%JAR%" (
  echo [错误] 未找到 lib\live-monitor-record-*.jar，请确认程序文件完整。
  pause
  exit /b 1
)
if not exist "cookie" mkdir cookie
if not exist "%LOCALAPPDATA%\ms-playwright\chromium-*" (
  echo [错误] 还没安装监听浏览器，请先双击「安装监听浏览器.bat」，装好后再运行本脚本。
  pause
  exit /b 1
)
rem 已有浏览器：禁止 Playwright 现场下载（国内网络会长时间无响应），缺浏览器时直接报错
set "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1"

echo ==================================================
echo   获取B站登录信息（扫码登录）
echo   1. 会弹出一个浏览器窗口，用手机 B 站 App 扫码登录
echo   2. 登录后页面右上角出现头像即为成功，程序自动保存
echo   3. 浏览器最多等 6 分钟，超时会自动关闭
echo   没装监听浏览器的话，先跑「安装监听浏览器.bat」
echo ==================================================
echo.
pause

"jre\bin\java.exe" "-Dsun.stdout.encoding=GBK" "-Dsun.stderr.encoding=GBK" -cp "%JAR%" cn.zhangheng.browser.login.BiliLogin
if errorlevel 1 (
  echo.
  echo [失败] 获取登录信息出错，请把上面的报错截图反馈给提供方。
  pause
  exit /b 1
)

set "LATEST="
for /f "delims=" %%f in ('dir /b /o-d "cookie\Bili-*.cookie" 2^>nul') do if not defined LATEST set "LATEST=%%f"
if not defined LATEST (
  echo.
  echo [警告] 没有找到 cookie\Bili-*.cookie，登录可能没有完成。
  pause
  exit /b 1
)

copy /y "cookie\%LATEST%" "cookie\Bili.cookie" >nul
echo.
echo 已保存登录信息：cookie\Bili.cookie（来源 %LATEST%）

echo.
echo 无需修改配置：程序会自动使用 cookie\Bili.cookie，重启「直播监听.exe」后生效。
pause
