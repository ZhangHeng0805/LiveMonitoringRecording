@echo off
chcp 936 >nul
setlocal
cd /d "%~dp0.."
echo 正在生成发行包，请勿关闭窗口……
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1" %*
set "code=%ERRORLEVEL%"
echo.
if not "%code%"=="0" (
    echo [失败] 构建退出码 %code%，请向上滚动查看错误信息。
) else (
    echo [完成] 发行包已生成，位置见上方输出。
)
pause