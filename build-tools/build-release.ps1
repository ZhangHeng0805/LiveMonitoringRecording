# 生成「星曦向荣直播监听」免安装发行包（exe 启动器 + 内置 JRE + 配置 + 说明）
# 用法：pwsh build-tools/build-release.ps1            # 全流程
#       pwsh build-tools/build-release.ps1 -SkipBuild # 复用已构建的 jar
#       pwsh build-tools/build-release.ps1 -SkipBuild -SkipZip
param(
    [string]$Version = '3.7',
    [string]$FfmpegPath = '',
    [string]$ActivationToolPath = '',
    [string]$OutDir = '',
    [switch]$SkipBuild,
    [switch]$SkipZip
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$appName = '星曦向荣直播监听'
$distRoot = Join-Path $root 'dist'
$appDir = if ($OutDir) { $OutDir } else { Join-Path $distRoot ("$appName-$Version-win64") }
$jarSrc = Join-Path $root "target\live-monitor-record-$Version.jar"

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'E:\Program Files\Java\jdk8u492-b09' }
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path

# 1. 构建 fat jar
if (-not $SkipBuild) {
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mvnCmd = if ($mvn) { $mvn.Source } else { 'E:\Program Files\JetBrains\IntelliJ IDEA 2024.1.1\plugins\maven\lib\maven3\bin\mvn.cmd' }
    Write-Host "Maven 构建中..." -ForegroundColor Cyan
    & $mvnCmd -q -DskipTests -f (Join-Path $root 'pom.xml') -pl live-monitor-record -am package
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败，退出码 $LASTEXITCODE" }
}
if (-not (Test-Path -LiteralPath $jarSrc)) { throw "未找到 $jarSrc，请先构建" }

# 2. 组装目录
if (Test-Path -LiteralPath $appDir) {
    try {
        Remove-Item -LiteralPath $appDir -Recurse -Force
    } catch {
        # 目录句柄被资源管理器/编辑器占用时：内容已清空就继续，别整个流程失败
        $left = @(Get-ChildItem -LiteralPath $appDir -Force -ErrorAction SilentlyContinue)
        if ($left.Count -gt 0) {
            throw "无法删除旧目录：$appDir`n可能正被记事本 / 资源管理器打开，关闭后重试。原始错误：$($_.Exception.Message)"
        }
        Write-Warning "旧目录被占用但已为空，继续在原地生成：$appDir"
    }
}
foreach ($sub in @('lib', 'bin', 'room', 'cookie', 'logs', 'tools')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $appDir $sub) | Out-Null
}
Copy-Item -LiteralPath $jarSrc -Destination (Join-Path $appDir 'lib') -Force
Copy-Item -Path (Join-Path $PSScriptRoot 'release-template\*') -Destination $appDir -Recurse -Force
# 模板 .bat 必须是 GBK/ANSI：cmd 按当前代码页解析，UTF-8 会把中文行拆断，报「xxx is not recognized」
foreach ($b in (Get-ChildItem -Path (Join-Path $PSScriptRoot 'release-template') -Recurse -Filter *.bat)) {
    $bytes = [IO.File]::ReadAllBytes($b.FullName)
    $isUtf8 = $false
    try {
        # GBK 字节几乎不可能全部通过 UTF-8 严格解码，能过的基本就是 UTF-8
        $strict = New-Object System.Text.UTF8Encoding($false, $true)
        $text = $strict.GetString($bytes)
        $isUtf8 = $text -match '[\u4e00-\u9fa5]'
    } catch { $isUtf8 = $false }
    if ($isUtf8) { Write-Warning "$($b.Name) 是 UTF-8 编码，cmd 会按 ANSI 解析导致中文乱码；请另存为 GBK/ANSI" }
}
Write-Host "已复制程序与模板文件" -ForegroundColor Cyan

# 内置 JRE（JDK8 自带 jre 目录；更高版本可用 jlink 生成）
$jreDir = Join-Path $env:JAVA_HOME 'jre'
if (-not (Test-Path -LiteralPath $jreDir)) { throw "未找到内置 JRE：$jreDir" }
robocopy $jreDir (Join-Path $appDir 'jre') /E /NFL /NDL /NJH /NJS /NP | Out-Null
# 复制一份改名的 java 启动器：任务管理器里显示为“直播监听内核.exe”，避免看起来像 JDK 进程
Copy-Item -LiteralPath (Join-Path $jreDir 'bin\javaw.exe') -Destination (Join-Path $appDir 'jre\bin\直播监听内核.exe') -Force
Write-Host "已内置 JRE：$jreDir" -ForegroundColor Cyan

# ffmpeg（录制用）
$ffmpeg = $FfmpegPath
if (-not $ffmpeg) {
    $candidates = @(
        (Join-Path $root 'bin\ffmpeg.exe'),
        'D:\直播录屏\live-monitor-record\bin\ffmpeg.exe'
    )
    foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { $ffmpeg = $c; break } }
}
if ($ffmpeg -and (Test-Path -LiteralPath $ffmpeg)) {
    Copy-Item -LiteralPath $ffmpeg -Destination (Join-Path $appDir 'bin') -Force
    Write-Host "已复制 ffmpeg：$ffmpeg" -ForegroundColor Cyan
} else {
    Write-Warning '未找到 ffmpeg.exe：发行包将无法使用 FFMPEG 录制（可稍后手动放入 bin 目录）'
}

# 激活工具（生成激活文件）
$actTool = $ActivationToolPath
if (-not $actTool) {
    $candidates = @(
        (Join-Path $root "target\activation-tool-$Version.jar"),
        "D:\直播录屏\live-monitor-record\activation-tool-$Version.jar"
    )
    foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { $actTool = $c; break } }
}
if ($actTool -and (Test-Path -LiteralPath $actTool)) {
    Copy-Item -LiteralPath $actTool -Destination (Join-Path $appDir 'tools') -Force
} else {
    Write-Warning "未找到 activation-tool-$Version.jar，发行包内将没有激活工具"
}

# 3. 图标 + 编译启动器
$ico = Join-Path $PSScriptRoot 'app.ico'
& (Join-Path $PSScriptRoot 'make-icon.ps1') -PngPath (Join-Path $root 'live-common\src\main\resources\logo.png') -IcoPath $ico | Out-Null
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $csc)) { throw "未找到 C# 编译器：$csc" }
& $csc /nologo /codepage:65001 /target:winexe /optimize+ /win32icon:"$ico" /out:"$(Join-Path $appDir '直播监听.exe')" `
    /r:System.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Core.dll `
    (Join-Path $PSScriptRoot 'Launcher.cs')
if ($LASTEXITCODE -ne 0) { throw "启动器编译失败，退出码 $LASTEXITCODE" }
Write-Host "启动器 exe 已生成" -ForegroundColor Cyan

# 5. 改写 exe 版本信息与图标（任务管理器里显示“星曦向荣直播监听内核 / 星曦向荣直播监听”）
$rcedit = Join-Path $PSScriptRoot 'rcedit.exe'
if (-not (Test-Path -LiteralPath $rcedit)) {
    try {
        Invoke-WebRequest -Uri 'https://github.com/electron/rcedit/releases/download/v2.0.0/rcedit-x64.exe' -OutFile $rcedit -TimeoutSec 60 -UseBasicParsing
    } catch {
        Write-Warning "未下载到 rcedit，跳过版本信息改写：$($_.Exception.Message)"
    }
}
if (Test-Path -LiteralPath $rcedit) {
    $ver4 = "$Version.0.0"
    $coreExe = Join-Path $appDir 'jre\bin\直播监听内核.exe'
    $appExe = Join-Path $appDir '直播监听.exe'
    & $rcedit $coreExe --set-file-version $ver4 --set-product-version $ver4 `
        --set-version-string "FileDescription" "星曦向荣直播监听内核" `
        --set-version-string "ProductName" "星曦向荣直播监听" `
        --set-version-string "CompanyName" "星曦向荣" `
        --set-version-string "LegalCopyright" "星曦向荣" --set-icon $ico
    & $rcedit $appExe --set-file-version $ver4 --set-product-version $ver4 `
        --set-version-string "FileDescription" "星曦向荣直播监听" `
        --set-version-string "ProductName" "星曦向荣直播监听" `
        --set-version-string "CompanyName" "星曦向荣" `
        --set-version-string "LegalCopyright" "星曦向荣" --set-icon $ico
    Write-Host "已写入版本信息与图标" -ForegroundColor Cyan
}

# 4. 打包 zip（不包含 tools 目录：激活工具单独分发，不随安装包外流）
if (-not $SkipZip) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $zip = Join-Path $distRoot ("$appName-$Version-win64.zip")
    if (Test-Path -LiteralPath $zip) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $zip) {
            # 旧包正被预览/下载/杀软占用：换名输出，保证流程不中断
            $zip = Join-Path $distRoot ("$appName-$Version-win64-new.zip")
            Write-Warning "旧 zip 被其它程序占用，已改为输出新文件：$zip"
        }
    }
    if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
    $zipRoot = Split-Path -Leaf $appDir
    $excludePrefix = (Join-Path $appDir 'tools') + [IO.Path]::DirectorySeparatorChar
    $archive = [System.IO.Compression.ZipFile]::Open($zip, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        # 空目录显式写入，解压后仍保留
        foreach ($sub in @('room', 'cookie', 'logs')) {
            $archive.CreateEntry("$zipRoot/$sub/") | Out-Null
        }
        foreach ($f in (Get-ChildItem -LiteralPath $appDir -Recurse -File)) {
            if ($f.FullName.StartsWith($excludePrefix, [StringComparison]::OrdinalIgnoreCase)) { continue }
            $rel = $f.FullName.Substring($appDir.Length + 1).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $f.FullName, "$zipRoot/$rel", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }
    $sizeMb = [math]::Round((Get-Item -LiteralPath $zip).Length / 1MB, 1)
    Write-Host "已打包：$zip（$sizeMb MB，不含 tools 目录）" -ForegroundColor Green
}

$total = (Get-ChildItem -LiteralPath $appDir -Recurse -File | Measure-Object Length -Sum)
Write-Host ("完成：{0}（{1} 个文件，{2:N0} MB）" -f $appDir, $total.Count, ($total.Sum / 1MB)) -ForegroundColor Green
