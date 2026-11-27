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
- [接口鉴权与远程访问](#接口鉴权与远程访问)
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

## [程序架构](https://zhangheng0805.github.io/LiveMonitoringRecording/%E7%9B%B4%E6%92%AD%E7%9B%91%E6%8E%A7%E5%BD%95%E5%88%B6-%E7%A8%8B%E5%BA%8F%E6%9E%B6%E6%9E%84%E5%9B%BE.html)

整体是**单进程常驻的多直播间值守程序**：一个房间一条监听线程，轮询探测是否开播，开播后并行拉起弹幕采集与录制，同时把控制面和录制文件通过本地 HTTP 服务（含内网穿透）对外暴露。

### 分层结构

![分层架构](image/architecture.png)

### 核心运行链路

![核心运行链路](image/run-flow.png)

### 设计要点

- **取数双通道**：抖音优先走 Playwright 模拟真实页面拿 `dataUrl`，再切 OkHttp 请求；B站 / 快手直接走接口或页面内嵌 State，不常驻浏览器。
- **录制双实现**：`FlvStreamRecorder` 纯 Java 拉流写文件，无外部依赖；`FFmpegFlvRecorder` 走 ffmpeg 子进程，可读录制进度。二者由 `record.type`（`JAVA` / `FFMPEG`）切换。
- **控制面与文件面同端口**：`/api`、`/action` 管理监听，`/fileRes` 提供带 Range 分片的录制文件下载，`/room-recycle` 管理回收站，鉴权按「公开 / 普通令牌 / 秘钥令牌」三级收敛。
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
| 激活凭证 | `xxxr-activation.lic` | 可选；与设备绑定，[联系作者获取](https://github.com/ZhangHeng0805/LiveMonitoringRecording)。缺失时进入首次试用期（默认 7 天） |
| ffmpeg | 可选 | 使用 FFmpeg 录制或转 MP4 时需要，放在 `bin/` 下并配置路径 |

### 2. 编译打包

```bash
mvn -DskipTests package
```

打包后会在 `target/` 目录生成可执行 JAR（fat jar，约 185MB，内含全部依赖）。

### 3. 配置与激活

1. 把 `xxxr.setting`、`bin/`（含 ffmpeg）与 jar 放在同一目录。
2. 首次使用可以直接启动，不需要激活凭证：程序会进入试用期（默认 7 天，由 `activation.trialDays` 控制），并在启动日志与托盘提示剩余天数。
3. 想正式使用或试用结束，先获取设备标识 ID 发给作者：

   ```bash
   java -cp live-monitor-record-x.x.jar cn.zhangheng.record.activation.ActivationUtil
   pause
   ```
4. 拿到 `xxxr-activation.lic` 后放到 jar 同一目录（或按 `activation.filePath` 指定路径），重启即生效。

### 4. 启动

> 从源码编译、打包 fat jar、生成免安装 exe 发行包的完整步骤见 [docs/构建与打包流程.md](docs/构建与打包流程.md)。

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

**免安装发行版（给不需要自己装 Java 的人）**

`dist/星曦向荣直播监听-3.7-win64.zip` 是可直接分发的绿色包：解压后双击 `直播监听.exe` 即可运行，内置 JRE8、ffmpeg、fat jar 与配置模板，目标机器不需要安装 Java。压缩包不含 `tools` 目录（激活工具涉及私钥，单独分发），解压后也没有 `activation.trialDays` 配置项，试用期默认 7 天。

- 启动器行为：定位内置 jre 与 `lib` 下的 jar（不写死版本）、单实例检查（已在运行则提示并打开面板）、首次缺少 Playwright 浏览器时询问并自动安装、stdout/stderr 落 `logs/launcher.log`，异常用弹窗提示而不是静默退出。
- 进程显示：真正干活的 JVM 由 `jre/bin/直播监听内核.exe`（`javaw.exe` 的副本，已用 rcedit 写入中文版本信息与图标）拉起，任务管理器里显示为“直播监听内核 / 星曦向荣直播监听内核”，不再显示 javaw 或 JDK 字样；`javaw.exe` 仍保留在 jre 里作为兜底。
- 首次运行需联网下载监听浏览器（约 150MB），也可以先双击 `安装监听浏览器.bat`。
- 需要登录态时，双击 `获取抖音登录信息.bat` / `获取B站登录信息.bat` 扫码登录：脚本复用 jar 里的 `DouYinLogin` / `BiliLogin`，把 cookie 存成 `cookie/DouYin.cookie`、`cookie/Bili.cookie`，**不改 `xxxr.setting`**——`Setting` 在 `Cookie.DouYin` / `Cookie.Bilibili` 未配置时会自动回退到这两个默认文件（文件存在才生效）。脚本内置 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，避免浏览器版本不匹配时静默下载 150MB 而看起来卡死。
- 重新生成发行包：`pwsh build-tools/build-release.ps1`（`-SkipBuild` 复用已构建 jar，`-SkipZip` 只生成目录）；模板与启动器源码在 `build-tools/`。
- 发行包默认不带内网穿透：没有 `cpolar.setting` 时程序会跳过 cpolar，不再打印误导性的启动失败。

监听文件目录为空或不存在时也能正常启动：程序会自动建目录、照常开启网页接口并保持常驻，同时常驻一个"面板入口"托盘图标（右键 →「监控界面」或点图标直接打开），启动时还会自动用浏览器打开面板，不会出现"托盘没图标、又不知道面板地址"的情况。所有监听都停止后，这个入口图标会自动补回来。

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

程序默认**常驻**：所有监听结束（下播、停止监控）后不再自动退出，网页服务与接口继续运行，可以随时再新增或恢复监听。

1. **网页「退出程序」（推荐）**：监控界面右上角的电源按钮，需二次确认并输入**操作秘钥**（即设备唯一 ID，与页面其它操作同一套秘钥）；确认后程序按「停监听 → 关浏览器 → 收线程池 → 停 HTTP/cpolar 服务」的顺序优雅退出。用 FFmpeg 录制时建议走这个入口，直接 `taskkill` 结束 JVM 可能残留 `ffmpeg.exe`。
2. **接口调用**：`action/exit` 是私密接口，先 `GET action/auth?actionKey=<设备唯一ID>` 换秘钥令牌，再带 `Authorization: Bearer <令牌>` 调用 `GET/POST action/exit`。适合远程或脚本调用。
3. **托盘「退出监听」**：只结束当前房间的监听，进程继续常驻（`COMMAND` 模式除外）。
4. **强制结束**：`taskkill /f /im javaw.exe`，仅在前几种方式失效时使用，结束后需检查是否有残留的 `ffmpeg.exe` / `chromium` 进程。

## 接口鉴权与远程访问

控制台支持两种访问形态，鉴权统一收敛成三级令牌：

| 形态 | 前端位置 | 鉴权方式 |
| --- | --- | --- |
| 本地直连 | 后端自带页面 `http://127.0.0.1:8005/` | Cookie 会话（`_token`）或 Bearer 令牌，二者都认 |
| 跨站远程 | GitHub Pages / 任意静态托管 + cpolar 域名 | `Authorization: Bearer <令牌>`（浏览器会拦截第三方 Cookie，不能依赖 Cookie） |

**三级鉴权**

| 级别 | 访问凭证 | 凭怎么拿 | 覆盖接口 |
| --- | --- | --- | --- |
| 公开 `PUBLIC` | 不需要令牌 | — | 页面/脚本/皮肤、`info/*`、`client-info/index`、`client-info/*` 埋点、`api/videoParsing` |
| 普通 `NORMAL` | 普通令牌 | `action/token` 公开签发，无需秘钥 | `api/rooms`（返回体自动脱敏）、`action/refresh`、`action/parseDouYinLink` |
| 私密 `PRIVATE` | 秘钥令牌 | `action/auth?actionKey=<设备标识ID>` 用操作秘钥换取 | `action/*` 写操作（监听/录制/新增/删除/设置/浏览器/退出）、`fileRes/*`、`room-recycle/*`、`client-info/log` |

秘钥令牌也能访问普通接口；普通令牌访问私密接口一律 403。页面本身可公开分享，陌生人拿到普通令牌即可看实时面板，但敏感字段已脱敏。

**令牌怎么来的**

1. 普通令牌：`GET action/token` → `{token, type:"public", expireSec, expireAt}`，有效期 `server.auth.publicTokenExpireSec`（默认 1800 秒），按来源 IP 限流 `server.api.publicTokenPerMinute`（默认 30 次/分钟，`0` 为不限）；
2. 秘钥令牌：`GET action/auth?actionKey=<设备标识ID>` → `{token, type:"api", expireSec, expireAt}`，有效期 `server.api.expireSec`（默认 7200 秒）；
3. 两种令牌都由 `JWT` 签发，前端缓存在 `localStorage`，剩余不足 5 分钟自动续期；
4. 普通接口用普通令牌访问，失败/被拒时自动升级秘钥令牌；私密接口弹「操作验证」输入框，用刚输入的秘钥换取秘钥令牌（同一把秘钥不会重复弹框）；
5. `img` / `video` / `<a>` 这类无法带请求头的地址（如录制文件下载），用查询参数 `token=<令牌>` 携带，录制文件需要秘钥令牌。

**本地存储键**

| 键 | 说明 |
| --- | --- |
| `actionKey` | 操作秘钥（设备标识 ID），勾选「记住秘钥」后写入 |
| `lmr.api.pubToken` / `lmr.api.pubTokenExp` | 普通令牌及其到期时间戳（毫秒） |
| `lmr.api.secretToken` / `lmr.api.secretTokenExp` | 秘钥令牌及其到期时间戳（毫秒） |
| `lmr.api.token` / `lmr.api.tokenExp` | 旧版单一令牌键，读取后自动迁移为秘钥令牌 |

**接口侧规则**

- 鉴权失败返回 `403` + JSON 提示（私密接口提示需要秘钥令牌，普通接口提示可调用 `action/token` 获取）；前端收到 403 会自动换令牌并重试一次，仍失败才提示；
- 服务端按顺序尝试：`Authorization: Bearer` → `?token=` → `_token` Cookie，任一通过即放行；
- `api/rooms` 匿名/普通令牌访问时自动脱敏（剔除录制路径、平台 Cookie、通知地址），只有秘钥令牌或同源会话返回全量；`api/videoParsing`（抖音作品解析）完全公开，任何人可直接调用，但按来源 IP 限流（`server.api.parsePerMinute`，默认 3 次/分钟，超限返回 `429` + `Retry-After`）；
- 跨域响应已放开 `Authorization`、`Range` 请求头与 `Content-Range`、`Content-Length` 等响应头，录制文件的分片下载/断点续传在跨站下同样可用；`OPTIONS` 预检直接返回 `204`；
- 退出接口 `action/exit` 是私密接口，只认秘钥令牌，不再额外校验 `actionKey` 参数，避免令牌泄露后被人远程关停；
- `action/parseDouYinLink?shareLink=` 会对解析结果做校验：入参是纯数字时直接原样返回并提示「已是直播间号」；启动无头浏览器跳转后，只有最终地址命中 `live.douyin.com/{数字}`（或本身是纯数字）才回填房间号，否则返回 `code=1` 并说明返回内容，不会把普通网页地址当房间号写入；
- `/proxy` 流媒体代理拒绝远程客户端访问内网/本机地址（本机请求不受限），避免被当作内网探测跳板。

**部署到 GitHub Pages 的用法**

把 `index.html`、`js/`、`css/`、`fonts/`、`img/` 上传到 Pages 仓库，用下面任一方式指向后端：

```
https://你的用户名.github.io/项目名/?url=https://xxxx.cpolar.cn
https://你的用户名.github.io/项目名/?redirect=<配置键>
```

`?redirect=` 会读取 `LiveMonitoringRecordingPage/redirect/json/config.json` 中的 `redirectUrl`（缓存 10 分钟），适合 cpolar 免费版域名会变的情况；只换 `redirectUrl` 就能让所有页面跟着切换。

## 配置说明

全局配置为 `xxxr.setting`（放在 jar 同目录，`key=value` 结构，`#` 为注释，支持 `\uXXXX` 转义）。**配置在启动时加载，修改后需重启程序生效。**

> 图例：标 **✅ 必填** 的三项由程序启动时强校验，缺失或为空会直接启动失败；其余项留空/删除即走默认值。

```properties
# 监控平台服务端口，占用自动累加
server.monitor.port=8005
# [必填] 运行模式：COMMAND-命令行交互（单房间），FILE-读取监听文件（多房间）
server.runMode=FILE
# 单实例运行：true=同一目录只允许开一个，重复启动会提示并打开已有实例面板；false=允许同时开多个
app.singleInstance=true
# 微信客户端通用对象（通过 PC 微信自动发送，留空则关闭）
#notice.weChat.target=文件传输助手
# 息知通知地址，多个地址用逗号分隔
#notice.xiZhi.url=https://xizhi.qqoq.net/***.send
#notice.xiZhi.url=https://xizhi.qqoq.net/***.channel
# 是否转换录制的视频（需要配置 ffmpeg）
record.FlvToMp4=true
# [必填] 录制类型：JAVA-使用 java 编写的录制，FFMPEG-使用 ffmpeg 工具录制
record.type=FFMPEG
# 抖音清晰度：直接按接口返回的分辨率/帧率/码率自动选最高，不写死档位名
# 录制画质：HIGH=最高 / MIDDLE=中等 / LOW=最低，房间设置里可单独覆盖
#record.quality=HIGH
# ffmpeg 的路径
record.ffmpegPath=bin/ffmpeg.exe
# [必填] 激活凭证文件路径，默认 xxxr-activation.lic
activation.filePath=xxxr-activation.lic
# 试用天数不写即默认 7 天；需要调整时才加下面这行
#activation.trialDays=7
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
# B站 Cookie，也可以指定文本文件 file:cookie/Bili.cookie
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
| `record.quality` | — | `HIGH` | 录制画质，枚举 `HIGH`（最高）/ `MIDDLE`（中等）/ `LOW`（最低）：取清晰度排序的对应档位（中等取中间档），房间设置里可单独覆盖 |
| `activation.filePath` | ✅ | `xxxr-activation.lic` | 激活凭证路径；凭证与设备绑定，文件存在但过期/损坏会导致启动校验不通过（不会退回试用） |
| `activation.trialDays` | — | 7 | 首次使用试用天数；激活文件缺失时生效，0 或负数表示不开放试用 |
| `server.monitor.port` | — | 8005 | 可视化控制台端口，范围 1-65535，占用时自动向后累加 |
| `app.singleInstance` | — | true | 单实例运行；`true` 时同一目录重复启动会直接打开已运行实例的面板并退出，`false` 允许同时跑多个（锁文件为 `logs/instance.lock`，进程退出自动释放） |
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
| `server.api.expireSec` | — | 7200 | 秘钥令牌有效期（秒），模板未列出需手动添加 |
| `server.auth.publicTokenEnabled` | — | true | 是否允许公开签发普通令牌；设 `false` 后普通接口也必须先换秘钥令牌 |
| `server.auth.publicTokenExpireSec` | — | 1800 | 普通令牌有效期（秒），模板未列出需手动添加 |
| `server.api.publicTokenPerMinute` | — | 30 | 普通令牌签发限流：同一 IP 每分钟最多次数，`0` 为不限，模板未列出需手动添加 |
| `server.api.parsePerMinute` | — | 3 | 抖音作品解析限流：同一 IP 每分钟最多调用次数，`0` 为不限，模板未列出需手动添加 |

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
程序正在运行，jar 文件被 JVM 锁定。先在网页点「退出程序」（或调用 `action/exit`），必要时 `taskkill /f /im javaw.exe`，再执行打包。

**提示激活失败或凭证无效**
`xxxr-activation.lic` 与设备绑定，换机、重装系统都会失效；确认文件与 jar 同目录，或检查 `activation.filePath` 配置。若还没有凭证，删掉（或改名）lic 文件后重启即可进入试用期。

**试用期结束提示**
试用天数由 `activation.trialDays` 控制（默认 7 天）。试用记录在工作目录 `xxxr-trial.dat`、用户目录 `.xxxr-trial.dat`、`%ProgramData%\XingXiXiangRong\LiveMonitoringRecording\xxxr-trial.dat` 和注册表（`HKCU\Software\JavaSoft\Prefs`）各存一份，取最早的首次使用时间，删掉单份不会重置试用。

提醒方式：试用期内每次启动提示剩余天数；剩 3 天以内（含 1 天）文案升级为催激活，并每小时自检一次、每天最多提醒一次；到期当天提醒"重启后将无法启动"，但不会打断正在运行的监听，到期后不再允许新增监听、重启监听和开启新的录制。

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
必须走网页「退出程序」或 `action/exit` 接口（内部会先停录制再退出）；直接杀 JVM 会留下子进程，可手动结束 `ffmpeg.exe`。

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
