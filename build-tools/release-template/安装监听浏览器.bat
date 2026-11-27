@echo off
chcp 936 >nul
title 安装监听浏览器
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
echo 正在安装监听浏览器（约 150MB，需要联网），请耐心等待...
echo.
"jre\bin\java.exe" -cp "%JAR%" com.microsoft.playwright.CLI install chromium
if errorlevel 1 (
  echo.
  echo [失败] 浏览器安装失败。常见原因：网络访问不了下载源、代理未配置、磁盘空间不足。
  echo        如使用代理，可先设置环境变量再重试，例如：
  echo        set HTTPS_PROXY=http://127.0.0.1:7890
  pause
  exit /b 1
)
echo.
echo 安装完成，现在可以双击「直播监听.exe」了。
pause
