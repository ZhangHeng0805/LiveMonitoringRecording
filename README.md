# LiveMonitoringRecording

直播监控录制工具，使用Java编写，支持抖音/B站/~~快手~~平台的直播间监控与录制。配合[息知](https://xz.qqoq.net/)通知平台，可实现开播和下播通知功能。

## 功能特性

- **直播监控**：实时监控直播间状态（如观看人数、直播状态等）
- **直播录制**：支持录制直播画面，提供两种录制方式：
    - Java原生录制（无需依赖FFmpeg）
    - FFmpeg录制（需要配置FFmpeg路径）
- **通知功能**：通过息知平台发送微信通知
- **FLV播放器**：内置简易FLV视频播放服务，```默认端口8000```
- **直播监听可视化界面**：实时监控各平台直播状态与录制信息，```默认端口8005，若端口占用，则自动向后累加，操作秘钥为设备标识ID```


## 直播监控可视化平台
![屏幕截图](image/8-10-2025_134650_localhost.jpeg)


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

# 各平台Cookie配置（可直接配置或通过文件引用，文件引用示例 file:Bili_Cookie.txt）
Cookie.Bilibili=
Cookie.DouYin=
Cookie.KuaiShou=
```

## 运行环境

- **JDK版本**：JDK 1.8 或更高
- **操作系统**：
    - 使用Java跨平台语言，理论上支持Windows，MacOS，Linux等所有主流操作系统，目前只验证了Windows操作系统

## 启动方式

### 通用启动方式

1. **编译打包**：项目打包后会在 `target/` 目录生成可执行 JAR 文件，**注意：运行程序需要激活凭证文件，获取文件请联系作者**
2. **运行环境**：
   - JDK 1.8 或更高
   - 配置文件 xxxr.setting
   - 激活凭证文件 xxxr-activation.lic (联系作者获取)
   - ffmpeg工具，根据不同的操作系统去下载对应的ffmpeg，下载完成后在配置文件中配置ffmpeg路径
3. **运行脚本命令**：

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

3. **监听文件启动**：通过读取监听文件（文件名后缀为```.room.json```）的形式启动，一个直播间一个监听文件，将要监听的直播文件统一放入同一文件夹中，程序启动后会自动读取指定目录中的监听文件，可以一个进程同时监听多个直播间，
监听文件格式：
```json
{
  //是否录制
  "isRecord": true,
  //直播间ID
  "id": "622216334529",
  //直播平台[DouYin:抖音,Bili:B站]
  "platform": "DouYin",
  //直播设置中的配置可选填，填写后覆盖配置文件中的配置
  "setting": {
    //选填，监听刷新间隔时间
    "delayIntervalSec": 30,
    //选填，监听通知接口地址
    "xiZhiUrl": "息知通知接口地址",
    //选填，根据直播平台配置Cookie
    "cookieBili": "B站Cookie",
    "cookieDouYin": "抖音Cookie",
    "runMode": "FILE"
  }
}
```
- **集成模块**（支持抖音/B站）：
  ```bash
  java -cp live-monitor-record-x.x.jar cn.zhangheng.lmr.FileModeMain [监听文件目录]
  ```
  示例：
  ```bash
  java -cp live-monitor-record-3.5.jar cn.zhangheng.lmr.FileModeMain ./room
  ```

### 获取设备标识ID方式
***运行命令***：

- **集成模块**：
  ```bash
  java -cp live-monitor-record-x.x.jar cn.zhangheng.common.activation.ActivationUtil
  ```
  示例：
  ```bash
  java -cp live-monitor-record-3.5.jar cn.zhangheng.common.activation.ActivationUtil
  pause
  ```

- **平台专用模块**：
  ```bash
  java -cp 平台-start-x.x.jar cn.zhangheng.common.activation.ActivationUtil
  ```
  示例：
  ```bash
  java -cp douyin-start--3.5.jar cn.zhangheng.common.activation.ActivationUtil
  pause
  ```

## 停止运行
1. **系统托盘操作**：
建议通过 **任务栏图标右键菜单** 退出程序，确保录制任务正常终止。  
若使用FFmpeg录制，**必须通过右键退出**，否则可能导致录制进程未终止。
2. **监控可视化界面操作**：
打开直播监控界面后，可通过点击**停止监控**的操作按钮实现停止运行，若所有直播监控都停止了，那么程序将自动终止。

## 开发者信息
- **作者**：ZhangHeng0805（星曦向荣）
- **项目地址**：[Gitee 仓库](https://gitee.com/ZhangHeng0805/LiveMonitoringRecording) | [GitHub 仓库](https://github.com/ZhangHeng0805/LiveMonitoringRecording)
- **演示视频**: [bilibili](https://www.bilibili.com/video/BV1JMhzzuE1G/) |  [抖音](https://v.douyin.com/uPsZUQICC7w/)
- **交流QQ群**：573648936 ![573648936](image/4a074f80d4f2ed6935e084f768857458.jpg)
