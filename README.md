# LiveMonitoringRecording
Java编写的直播监控录制工具，可以监控直播间的数据（观看人数...）也可以录制直播画面，目前仅支持抖音和B站

## 项目结构
```
├─bilibili-start [B站监控录制模块]
├─douyin-start [抖音监控录制模块]
├─live-common [公共模块]
├─live-monitor-record [程序集成总模块]
```

### 配置文件xxxr.setting
```properties
#微信客户端通用对象（需配置消息发送快捷键Ctrl+Enter）
#weChat.target=文件传输助手
#息知通知地址（可以微信公众号发通知）详情查看：https://xz.qqoq.net/
#xiZhi.url=https://xizhi.qqoq.net/***.send
#xiZhi.url=https://xizhi.qqoq.net/***.channel
#是否转换录制的视频
record.FlvToMp4=true
#是否循环监听直播（直播结束后，重新监听）
record.isLoop=true
#监听间隔时长（秒）
monitor.delayIntervalSec=10
#B站的Cookie(可以录制更清晰的直播画面)
Bilibili.Cookie=
```
### 运行启动
> JDK1.8+
* 通过 java -jar 的命令直接运行jar包，然后命令行按照提示输入
* 通过 java -jar live-monitor-record-x.x.jar [直播间ID,是否录制,直播平台]  接参数的形式直接运行开始
  * 例：java -jar live-monitor-record-2.0.jar 直播间ID true DouYin/Bili
