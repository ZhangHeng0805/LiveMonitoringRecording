@echo off
chcp 936 >nul
title 生成激活文件
cd /d "%~dp0.."
if not exist "jre\bin\java.exe" (
  echo [错误] 未找到 jre\bin\java.exe，请把本工具放在程序目录的 tools 子目录下运行。
  pause
  exit /b 1
)
set "JAR="
for %%f in ("tools\activation-tool-*.jar") do if exist "%%~f" set "JAR=%%~f"
if not exist "%JAR%" (
  echo [错误] 未找到 tools\activation-tool-*.jar，请确认激活工具已放入 tools 目录。
  pause
  exit /b 1
)
echo 请输入要激活的设备标识（32 位）和有效天数，生成的激活文件保存在程序目录。
echo.
"jre\bin\java.exe" "-Dsun.stdout.encoding=GBK" "-Dsun.stderr.encoding=GBK" -jar "%JAR%"
if errorlevel 1 (
  echo.
  echo [失败] 生成失败，请检查上面的报错信息。
  pause
  exit /b 1
)
echo.
pause
