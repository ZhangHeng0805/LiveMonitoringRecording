# LiveMonitoringRecording

直播监控录制工具，使用Java编写，支持抖音/B站/快手平台的直播间监控与录制。配合[息知](https://xz.qqoq.net/)通知平台，可实现开播和下播通知功能。

## 功能特性

- **直播监控**：实时监控直播间状态（如观看人数、直播状态等）
- **直播录制**：支持录制直播画面，提供两种录制方式：
    - Java原生录制（无需依赖FFmpeg）
    - FFmpeg录制（需要配置FFmpeg路径）
- **通知功能**：通过息知平台发送微信通知
- **FLV播放器**：内置简易FLV视频播放服务

## 项目结构

```
├─ bilibili-start   [B站监控录制模块]
├─ douyin-start     [抖音监控录制模块]
├─ kuaishou-start   [快手监控录制模块]
├─ live-common      [公共模块，包含核心功能与工具类]
├─ live-monitor-record [主程序模块，集成所有平台功能]
```

## 配置说明

配置文件：`xxxr.setting`

```properties
# FLV播放器服务端口
server.flvPlayer.port=8000

# 息知通知地址（可选）
# notice.xiZhi.url=https://xizhi.qqoq.net/xxx.send
# notice.xiZhi.url=https://xizhi.qqoq.net/xxx.channel

# 是否转换录制的视频为MP4格式（需要配置FFmpeg）
record.FlvToMp4=true

# 录制类型：0-Java录制，1-FFmpeg录制
record.type=1

# FFmpeg可执行文件路径
record.ffmpegPath=bin/ffmpeg.exe

# 激活凭证文件路径（可选）
# activation.filePath=xxxr-activation.lic

# 是否循环监听直播（直播结束后重新监听）
record.isLoop=true

# 监听间隔时间（秒）
monitor.delayIntervalSec=10

# 直播开始/结束触发的快捷键（英文小写，逗号分隔）
living.start.shortcut=
living.end.shortcut=

# 各平台Cookie配置（可直接配置或通过文件引用）
Cookie.Bilibili=
Cookie.DouYin=
Cookie.KuaiShou=
```

## 运行环境

- **JDK版本**：JDK 1.8 或更高
- **操作系统**：
    - 若需录制功能，建议使用Windows（需图形界面）
    - 仅使用监听通知功能时，支持所有操作系统

## 启动方式

### 通用启动方式

1. **编译打包**：项目打包后会在 `target/` 目录生成可执行 JAR 文件
2. **运行命令**：

    - **集成模块**（支持抖音/B站）：
      ```bash
      java -jar live-monitor-record-x.x.jar [直播间ID] [是否录制] [平台]
      ```
      示例：
      ```bash
      java -jar live-monitor-record-2.0.jar 622216334529 true DouYin
      ```

    - **平台专用模块**：
      ```bash
      java -jar 平台-start-x.x.jar [直播间ID] [是否录制]
      ```
      示例：
      ```bash
      java -jar douyin-start--2.0.jar 622216334529 true
      ```

3. **交互式启动**：不带参数运行后，通过命令行输入直播间信息启动

## 停止运行

建议通过 **任务栏图标右键菜单** 退出程序，确保录制任务正常终止。  
若使用FFmpeg录制，**必须通过右键退出**，否则可能导致录制进程未终止。

## 开发者信息

- **作者**：ZhangHeng0805
- **项目地址**：[Gitee 仓库](https://gitee.com/ZhangHeng0805/LiveMonitoringRecording) | [GitHub 仓库](https://github.com/ZhangHeng0805/LiveMonitoringRecording)
- **演示视频**: [bilibili](https://www.bilibili.com/video/BV1JMhzzuE1G/) |  [抖音](https://v.douyin.com/uPsZUQICC7w/)
