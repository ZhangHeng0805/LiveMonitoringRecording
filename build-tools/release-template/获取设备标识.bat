@echo off
chcp 936 >nul
title 获取设备标识
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
echo 正在读取本机设备标识（可能需要几秒）...
echo.
"jre\bin\java.exe" "-Dsun.stdout.encoding=GBK" "-Dsun.stderr.encoding=GBK" -cp "%JAR%" cn.zhangheng.record.activation.ActivationUtil
if errorlevel 1 (
  echo.
  echo [失败] 读取失败，请把上面的报错截图反馈给提供方。
  pause
  exit /b 1
)
echo.
echo 上面这串 32 位字符就是本机操作秘钥，复制到面板里即可。
pause
