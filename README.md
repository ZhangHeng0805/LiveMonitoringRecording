# LiveMonitoringRecording

> 直播监控录制工具 —— 一个 Java 进程，同时值守多个直播间：开播自动录制、下播自动转码归档，弹幕实时落盘并生成字幕，本地可视化界面随时查看与操作。

<p>
  <img src="https://img.shields.io/badge/JDK-1.8%2B-orange" alt="JDK 1.8+">
  <img src="https://img.shields.io/badge/平台-抖音%20%7C%20B站%20%7C%20快手-critical" alt="平台">
  <img src="https://img.shields.io/badge/控制台端口-8005-informational" alt="控制台端口">
  <img src="https://img.shields.io/badge/部署-单机单进程-success" alt="部署">
</p>

支持 **抖音 / B站 / 快手** 的直播间监控与录制，配合 [息知](https://xz.qqoq.net/) 通知平台可实现开播、下播微信通知。提供本地可视化控制台，可在不重启进程的前提下增删、启停直播间监听，并支持内网穿透远程访问。

## 目录

- [功能特性](#功能特性)
- [界面预览](#界面预览)
- [程序架构](#程序架构)
- [快速开始](#快速开始)
- [使用说明](#使用说明)
- [配置说明](#配置说明)
- [常见问题](#常见问题)
- [目录结构](#目录结构)
- [开发者信息](#开发者信息)

## 功能特性

| 能力 | 抖音 | B站 | 快手 |
| --- | :---: | :---: | :---: |
| 直播状态监控（开播状态 / 观看人数 / 封面） | ✔ | ✔ | ✔ |
| 自动录制（Java 原生 / FFmpeg 两种方式） | ✔ | ✔ | ✔ |
| 循环监听（下播后自动重新等待下一场） | ✔ | ✔ | ✔ |
| 开播 / 下播通知（息知推送微信） | ✔ | ✔ | ✔ |
| 弹幕实时采集与本地持久化 | ✔ | — | — |
| ASS / SRT 字幕自动生成 | ✔ | — | — |
| 可视化控制台 + 录制文件在线回放 | ✔ | ✔ | ✔ |

**能力说明**

- **直播监控**：实时获取直播间状态（观看人数、是否开播、封面、清晰度等），未开播按 `monitor.delayIntervalSec` 间隔轮询，最小 20 秒。
- **直播录制**：提供两种录制方式 —— `Java 原生录制`不依赖任何外部程序，`FFmpeg 录制`支持读取录制进度；单文件超过 15G 或直播切换清晰度时自动分段，录制结束可自动转 MP4 并把源文件移入回收站。
- **抖音弹幕**：实时获取聊天、进场、点赞、统计、在线、排名、关注、状态、送礼等消息，本地持久化保存；直播结束后可自动转换为**顶部飘动形式的 ASS 字幕**文件。`礼物信息需要配置账号 Cookie`。
- **通知功能**：通过息知平台推送开播 / 下播消息到微信。
- **FLV 播放页**：控制台内置 `/FLVPlayer.html` 页面，可直接预览直播源；历史版本使用的独立 8000 端口已合并进控制台服务，无需额外端口。
- **可视化界面**：实时查看各平台直播状态与录制信息，`默认端口 8005，端口占用时自动向后累加`，操作秘钥为**设备标识 ID**。

## 界面预览

![直播监控可视化界面](image/8-10-2025_134650_localhost.jpeg)

## 程序架构

整体是**单进程常驻的多直播间值守程序**：一个房间一条监听线程，轮询探测是否开播，开播后并行拉起弹幕采集与录制，同时把控制面和录制文件通过本地 HTTP 服务（含内网穿透）对外暴露。

### 分层结构

![分层架构](image/architecture.png)

### 核心运行链路

![核心运行链路](image/run-flow.png)

### 设计要点

- **取数双通道**：抖音优先走 Playwright 模拟真实页面拿 `dataUrl`，再切 OkHttp 请求；B站 / 快手直接走接口或页面内嵌 State，不常驻浏览器。
- **录制双实现**：`FlvStreamRecorder` 纯 Java 拉流写文件，无外部依赖；`FFmpegFlvRecorder` 走 ffmpeg 子进程，可读录制进度。二者由 `record.type`（`JAVA` / `FFMPEG`）切换。
- **控制面与文件面同端口**：`/api`、`/action` 管理监听，`/fileRes` 提供带 Range 分片的录制文件下载，`/room-recycle` 管理回收站，鉴权为设备标识 ID。
- **弹幕与录制解耦**：弹幕连接只在开播期间建立，录制异常由 `RecorderTask` 重试恢复，两者互不阻塞。
- **线程模型**：业务侧使用 `record.bean.Task` 的可重建线程池（每个监听 / 录制任务一个池），队列侧使用 `common.task.AsyncTaskQueue`，二者语义不同，改动时注意区分。

<details>
<summary>模块职责明细（11 个 Maven 模块）</summary>

| 模块 | 性质 | 职责 |
| --- | --- | --- |
| live-monitor-record | 主程序 | 集成入口（FileModeMain / Main）、本地 HTTP 控制台、服务与隧道编排 |
| live-common | 核心 | 监听生命周期、录制、HTTP 基类、通知托盘、授权、Room 模型 |
| douyin-start | 平台适配 | 抖音取数、弹幕服务、字幕生成、统计表 |
| bilibili-start | 平台适配 | B站接口取数 + 登录 Cookie |
| kuaishou-start | 平台适配 | 快手页面 InitialState 提取直播信息 |
| browser-utils | 基础能力 | Playwright 浏览器 / 上下文池、定时清理、登录取 Cookie |
| douyin-fetcher | 基础能力 | 抖音弹幕协议：WebSocket、protobuf 解码、消息分发 |
| common-libs | 基础设施 | 配置注入、JMX 动态 MBean、单飞缓存、优先级任务队列、通用工具 |
| cpolar-tool | 基础能力 | 内网穿透进程管理与公网地址解析 |
| activation-tool | 独立工具 | 激活码生成，不参与主程序运行 |
| smart-video-frame-extractor | 独立工具 | 视频抽帧与画面质量评分，主程序未依赖 |

</details>

## 快速开始

### 1. 环境准备

| 依赖 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 1.8 或更高 | 开发与运行均为 JDK 8 验证通过 |
| 操作系统 | Windows（已验证） | Java 跨平台，理论支持 macOS / Linux，但仅 Windows 实测 |
| 配置文件 | `xxxr.setting` | 仓库内已提供模板，放在 jar 同目录 |
| 激活凭证 | `xxxr-activation.lic` | **必须**，与设备绑定，[联系作者获取](https://github.com/ZhangHeng0805/LiveMonitoringRecording) |
| ffmpeg | 可选 | 使用 FFmpeg 录制或转 MP4 时需要，放在 `bin/` 下并配置路径 |

### 2. 编译打包

```bash
mvn -DskipTests package
```

打包后会在 `target/` 目录生成可执行 JAR（fat jar，约 185MB，内含全部依赖）。

### 3. 配置与激活

1. 把 `xxxr.setting`、`xxxr-activation.lic`、`bin/`（含 ffmpeg）与 jar 放在同一目录。
2. 若还没有激活凭证，先获取设备标识 ID 发给作者：

   ```bash
   java -cp live-monitor-record-x.x.jar cn.zhangheng.record.activation.ActivationUtil
   pause
   ```

### 4. 启动

**推荐方式：监听文件模式**（一个进程监听多个直播间，一个直播间一个 `.room.json`）

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.lmr.FileModeMain ./room
```

示例：

```bash
java -cp live-monitor-record-3.6.jar cn.zhangheng.lmr.FileModeMain ./room
```

**命令行单房间模式**

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.lmr.Main [直播间ID] [是否录制] [平台]
```

示例：

```bash
java -cp live-monitor-record-3.6.jar cn.zhangheng.lmr.Main 622216334529 true DouYin
```

<details>
<summary>其他启动方式（平台专用模块 / 交互式 / 已弃用的 java -jar）</summary>

- **平台专用模块**：只打包单个平台时使用，通用于调试与二次开发。

  ```bash
  java -jar 平台-start-x.x.jar [直播间ID] [是否录制]
  ```

- **交互式启动**：不带参数运行，按命令行提示输入直播间信息。

- **~~java -jar 集成模块~~**（已弃用，保留兼容）：

  ```bash
  java -jar live-monitor-record-x.x.jar [直播间ID] [是否录制] [平台]
  ```

</details>

### 5. 打开可视化界面

浏览器访问 `http://localhost:8005`（端口被占用会自动累加，实际端口见启动日志），操作秘钥为**设备标识 ID**。若需公网访问，可搭配 `cpolar` 内网穿透（见 [配置说明](#配置说明)）。

## 使用说明

### 监听文件格式

一个直播间一个监听文件，文件名后缀为 `.room.json`，统一放入同一目录（如 `./room`），程序启动后自动读取。格式示例：[示例文件](小兰花.room.json.del)

```json
{
  "id": "172730382391",
  "name": "麦小鼠大王",
  "platform": "DouYin",
  "isAutoRecord": true,
  "isEnable": true,
  "setting": {
    "delayIntervalSec": 30,
    "isLoop": true,
    "openSubtitle": true,
    "convertFlvToMp4": true,
    "cookie": "",
    "xiZhiUrl": ""
  },
  "savePath": ""
}
```

### 获取设备标识 ID

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.record.activation.ActivationUtil
pause
```

平台专用模块同理：`java -cp 平台-start-x.x.jar cn.zhangheng.record.activation.ActivationUtil`

### 获取登录 Cookie

**抖音**：运行后完成登录，程序自动获取并保存到文件。

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.douyin.browser.DouYinLogin
pause
```

**B站**：登录完成后需**手动把鼠标移到账号头像处**，帮助程序识别并获取，保存到文件。

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.browser.login.BiliLogin
pause
```

### 仅抓取抖音弹幕

启动后按命令行提示输入即可。注意命令行窗口文字编码问题；Cookie 可直接输入，也可用文件引用形式 `file:douyin-cookie.txt`。**没有账号 Cookie 获取不到礼物信息**。

```bash
java -cp live-monitor-record-x.x.jar cn.zhangheng.douyin.browser.DouYinWebcast
pause
```

### 停止运行

1. **托盘「退出监听」（推荐）**：结束当前房间的监听；`FILE` 模式下只退该房间，所有房间都退出后进程才结束，`COMMAND` 模式直接退出进程。用 FFmpeg 录制时建议走这个入口，直接 `taskkill` 结束 JVM 可能残留 `ffmpeg.exe`。
2. **可视化界面停止**：在监控界面点击**停止监控**，当所有监听都停止时程序自动终止。
3. **强制结束**：`taskkill /f /im javaw.exe`，仅在前两种方式失效时使用，结束后需检查是否有残留的 `ffmpeg.exe` / `chromium` 进程。

## 配置说明

全局配置为 `xxxr.setting`（放在 jar 同目录，`key=value` 结构，`#` 为注释，支持 `\uXXXX` 转义）。**配置在启动时加载，修改后需重启程序生效。**

> 图例：标 **✅ 必填** 的三项由程序启动时强校验，缺失或为空会直接启动失败；其余项留空/删除即走默认值。

```properties
# 监控平台服务端口，占用自动累加
server.monitor.port=8005
# [必填] 运行模式：COMMAND-命令行交互（单房间），FILE-读取监听文件（多房间）
server.runMode=FILE
# 微信客户端通用对象（通过 PC 微信自动发送，留空则关闭）
#notice.weChat.target=文件传输助手
# 息知通知地址，多个地址用逗号分隔
#notice.xiZhi.url=https://xizhi.qqoq.net/***.send
#notice.xiZhi.url=https://xizhi.qqoq.net/***.channel
# 是否转换录制的视频（需要配置 ffmpeg）
record.FlvToMp4=true
# [必填] 录制类型：JAVA-使用 java 编写的录制，FFMPEG-使用 ffmpeg 工具录制
record.type=FFMPEG
# ffmpeg 的路径
record.ffmpegPath=bin/ffmpeg.exe
# [必填] 激活凭证文件路径，默认 xxxr-activation.lic
activation.filePath=xxxr-activation.lic
# 是否循环监听直播（直播结束后，重新监听）
record.isLoop=true
# 是否开启弹幕记录（目前只对抖音有效）
record.openSubtitle=true
# 监听间隔时长（秒）
monitor.delayIntervalSec=30
# 是否隐藏监听浏览器
#monitor.browser.headless=false
# 直播开始时触发的快捷键(小写，多个组合之间逗号分割，例：alt+tab,ctrl+s)
living.start.shortcut=
# 直播结束时触发的快捷键
living.end.shortcut=
# B站 Cookie，也可以指定文本文件 file:Bili.txt
Cookie.Bilibili=
# 抖音 Cookie
Cookie.DouYin=
# 快手 Cookie
Cookie.KuaiShou=
# 模板未包含、代码仍支持的可选项，需要时手动添加
#monitor.maxMonitorThreads=10
#server.api.expireSec=7200
```

| 配置 | 必填 | 默认 | 说明 |
| --- | :---: | --- | --- |
| `server.runMode` | ✅ | `COMMAND` | 运行模式，枚举 `COMMAND` / `FILE`。多房间文件监听必须为 `FILE`，否则只监听单个房间 |
| `record.type` | ✅ | 无 | 录制类型，枚举 `JAVA` / `FFMPEG`（**大写**，不是 0/1） |
| `activation.filePath` | ✅ | `xxxr-activation.lic` | 激活凭证路径；凭证与设备绑定，文件缺失或过期会导致启动校验不通过 |
| `server.monitor.port` | — | 8005 | 可视化控制台端口，范围 1-65535，占用时自动向后累加 |
| `record.FlvToMp4` | — | true | 录制结束转 MP4，需 `record.ffmpegPath` 可用 |
| `record.ffmpegPath` | — | `bin/ffmpeg.exe` | FFmpeg 可执行文件路径 |
| `record.isLoop` | — | true | 下播后是否重新等待下一场 |
| `record.openSubtitle` | — | true | 是否开启弹幕记录，目前仅抖音有效 |
| `monitor.delayIntervalSec` | — | 20 | 轮询间隔秒数，须 ≥10 |
| `monitor.browser.headless` | — | true | 是否隐藏监听浏览器，排查取数问题时设为 `false` |
| `notice.xiZhi.url` | — | 空 | 息知推送地址，多个地址用逗号分隔，留空不推送 |
| `notice.weChat.target` | — | 空 | PC 微信自动发送的聊天对象（如 `文件传输助手`），留空不发送 |
| `Cookie.Bilibili` / `Cookie.DouYin` / `Cookie.KuaiShou` | — | 空 | 支持 `file:xxx.txt` 文件引用，按平台分别配置 |
| `living.start.shortcut` / `living.end.shortcut` | — | 空 | 开播 / 下播触发的快捷键，小写、多个用逗号分隔（如 `alt+tab,ctrl+s`） |
| `monitor.maxMonitorThreads` | — | 10 | 同时监听上限，1-100，模板未列出需手动添加 |
| `server.api.expireSec` | — | 7200 | 控制台接口会话有效期（秒），模板未列出需手动添加 |

> 浏览器上下文清理策略已改为代码内置固定值（`isPageClear=false`、`updateContextCounts=50`），不再提供配置项。

### 房间级配置（覆盖全局）

`*.room.json` 中的 `setting` 覆盖全局配置，当前格式：

> 规则：`setting` 内的四个字段是包装类型，**不写该字段就沿用全局配置**（`xxxr.setting` 的值）；只有显式写 `0` / `false` 才会覆盖全局（例如写 `"delayIntervalSec": 0` 会导致极高频轮询）。

```json
{
  "id": "172730382391",
  "name": "麦小鼠大王",
  "platform": "DouYin",
  "isAutoRecord": false,
  "isEnable": true,
  "setting": {
    "delayIntervalSec": 60,
    "isLoop": true,
    "openSubtitle": false,
    "convertFlvToMp4": true
  },
  "savePath": "【星曦向荣】直播监听工具/抖音/麦小鼠大王[172730382391]"
}
```

| 字段 | 必填 | 说明 |
| --- | :---: | --- |
| `id` | ✅ | 直播间 ID，为空会抛出「直播ID不能为空」并跳过该文件 |
| `name` | — | 昵称，程序停止监听时自动回写为实际昵称，可留空 |
| `platform` | ✅ | 平台枚举：`DouYin` / `Bili` / `KuaiShou`，为空会导致该文件解析失败 |
| `isAutoRecord` | — | 是否开播自动录制，默认 `false` |
| `isEnable` | — | 是否启用，默认 `true`，置 `false` 则跳过该文件 |
| `setting` | ✅ | 配置对象，缺失会导致该文件运行异常并被跳过 |
| `setting.delayIntervalSec` | — | 该房间轮询间隔（秒）；不填沿用全局 |
| `setting.isLoop` | — | 该房间下播后是否重新监听；不填沿用全局 |
| `setting.openSubtitle` | — | 该房间是否记录弹幕（抖音）；不填沿用全局 |
| `setting.convertFlvToMp4` | — | 该房间录制是否转 MP4；不填沿用全局 |
| `setting.cookie` | — | 该房间单独使用的 Cookie，会按 `platform` 自动写入对应平台 |
| `setting.xiZhiUrl` | — | 该房间单独的息知推送地址 |
| `savePath` | — | 录制目录，程序停止时按昵称重命名目录并回写，一般无需手改 |

> 文件监听模式下程序会强制 `runMode=FILE`，房间文件中即使填了 `COMMAND` 也会被改回 `FILE`。

## 常见问题

**升级打包时提示 jar 被占用**
程序正在运行，jar 文件被 JVM 锁定。先托盘右键退出程序（必要时 `taskkill /f /im javaw.exe`），再执行打包。

**提示激活失败或凭证无效**
`xxxr-activation.lic` 与设备绑定，换机、重装系统都会失效；确认文件与 jar 同目录，或检查 `activation.filePath` 配置。

**可视化界面打不开**
默认 8005 起，端口被占用会自动向后累加，请以启动日志里的实际端口为准；确认防火墙未拦截，或改用 `http://127.0.0.1:端口`。

**监控界面提示操作秘钥错误**
秘钥即**设备标识 ID**，用 `ActivationUtil` 生成的值，注意区分大小写。

**取不到直播状态 / Cookie 失效**
登录态过期是常见原因。重新执行 `DouYinLogin` 或 `BiliLogin` 更新 Cookie，并写入 `xxxr.setting` 或房间配置。

**录制文件为 0 字节或没有画面**
先检查 `record.type`：`FFMPEG` 需要 `record.ffmpegPath` 指向可执行文件；没有 ffmpeg 的环境改成 `JAVA` 用原生录制。同时确认直播确实在推流。

**弹幕里没有送礼信息**
需要配置抖音账号 Cookie，未登录只能拿到聊天、点赞等公开消息。

**退出后仍有 ffmpeg 进程**
必须通过托盘右键退出；直接杀 JVM 会留下子进程，可手动结束 `ffmpeg.exe`。

## 目录结构

```
├─ bilibili-start   [B站监控录制模块]
├─ douyin-start     [抖音监控录制模块]
├─ kuaishou-start   [快手监控录制模块]
├─ live-common      [公共模块，包含核心功能与工具类]
├─ browser-utils    [浏览器模块，以浏览器模拟请求]
├─ douyin-fetcher   [抖音直播弹幕模块，获取抖音直播弹幕]
├─ live-monitor-record [主程序模块，集成所有平台功能]
├─ common-libs      [通用基础设施：配置注入 / JMX / 缓存 / 任务队列]
├─ cpolar-tool      [内网穿透工具封装，公网访问可视化界面]
├─ activation-tool  [激活码生成工具，不参与主程序运行]
├─ smart-video-frame-extractor [视频抽帧分析工具，独立使用]
├─ image/           [README 配图与架构图]
├─ bin/             [ffmpeg、cpolar 等外部可执行程序]
└─ room/            [直播间监听文件目录（*.room.json）]
```

更多细节见 [说明文档](instructions.md)。

## 开发者信息

- **作者**：ZhangHeng0805（星曦向荣）
- **项目地址**：[Gitee 仓库](https://gitee.com/ZhangHeng0805/LiveMonitoringRecording) | [GitHub 仓库](https://github.com/ZhangHeng0805/LiveMonitoringRecording)
- **演示视频**：[bilibili](https://www.bilibili.com/video/BV1JMhzzuE1G/) | [抖音](https://v.douyin.com/uPsZUQICC7w/)
- **交流 QQ 群**：573648936 ![573648936](image/4a074f80d4f2ed6935e084f768857458.jpg)
