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
#FLV播放器服务-端口号
server.flvPlayer.port=8000
#微信客户端通用对象（需配置消息发送快捷键Ctrl+Enter）
#notice.weChat.target文件传输助手
#息知通知地址（可以微信公众号发通知）详情查看：https://xz.qqoq.net/
#notice.xiZhi.url=https://xizhi.qqoq.net/***.send
#notice.xiZhi.url=https://xizhi.qqoq.net/***.channel
#是否转换录制的视频
record.FlvToMp4=true
#录制类型：0-使用java编写的录制，1-使用ffmpeg工具录制
record.type=0
#ffmpeg的路径
record.ffmpegPath=bin/ffmpeg.exe
#是否循环监听直播（直播结束后，重新监听）
record.isLoop=true
#监听间隔时长（秒）
monitor.delayIntervalSec=10
#B站的Cookie(可以录制更清晰的直播画面)
Cookie.Bilibili=
#抖音的Cookie
Cookie.DouYin=
```
### 运行启动
> JDK1.8+
* 通过 java -jar 的命令直接运行jar包，然后命令行按照提示输入
* 通过 java -jar live-monitor-record-x.x.jar [直播间ID,是否录制,直播平台]  接参数的形式直接运行开始
  * 例：java -jar live-monitor-record-2.0.jar 直播间ID true DouYin/Bili

### 结束运行
> 结束运行程序建议通过```任务栏图标右键```退出，程序运行时任务栏会有对应的图标，右键图标可以选择对应的操作
