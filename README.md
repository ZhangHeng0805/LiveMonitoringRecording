# LiveMonitoringRecording
Java编写的直播监控录制工具，可以监控直播间的数据（观看人数...）也可以录制直播画面，目前仅支持抖音和B站，配合[息知](https://xz.qqoq.net/)通知平台可以实现直播间的开播和下播通知功能

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
#微信客户端通用对象（需配置消息发送快捷键Ctrl+Enter）通过模拟操作的形式
#notice.weChat.target=文件传输助手
#息知通知地址（可以微信公众号发通知）详情查看：https://xz.qqoq.net/
#notice.xiZhi.url=https://xizhi.qqoq.net/***.send
#notice.xiZhi.url=https://xizhi.qqoq.net/***.channel
#是否转换录制的视频(需要配置ffmpeg的路径)
record.FlvToMp4=true
#录制类型：0-使用java编写的录制(不需要依赖ffmpeg)，1-使用ffmpeg工具录制(需要配置ffmpeg的路径)
record.type=1
#ffmpeg的路径
record.ffmpegPath=bin/ffmpeg.exe
#是否循环监听直播（直播结束后，重新监听）
record.isLoop=true
#监听间隔时长（秒）
monitor.delayIntervalSec=10
#直播开始时触发的快捷键(英文小写，多个组合之间逗号分割，例：alt+tab,ctrl+s)
living.start.shortcut=
#直播结束时触发的快捷键
living.end.shortcut=
#B站的Cookie(可以录制更清晰的直播画面)
#也可以将Cookie保存至指定文本文件，然后通过 file:文本文件路径 的形式设置
Cookie.Bilibili=
#抖音的Cookie
Cookie.DouYin=
```
### 运行启动
#### 运行
> * 运行环境：JDK1.8+
> * 操作系统：如果需要录制功能建议使用Windows有图像化的操作系统，因为录制功能需要在系统任务栏图标处进行操作，如果仅使用监听通知功能则不限制操作系统
#### 启动
> * 项目编译打包后会在项目根目录下的target目录中出现打包完成的可执行jar包，有```live-monitor-record-x.x.jar```，```douyin-start-x.x.jar```,```bilibili-start-x.x.jar```等jar包文件，
>   * live-monitor-record-x.x.jar为集中功能的通用jar包，可以监听录制抖音和B站的直播
>     * 通过 java -jar live-monitor-record-x.x.jar [直播间ID,是否录制,直播平台] 接参数的形式直接运行开始；也可以不接参数的形式直接运行后通过命令交互的形式启动
>       * 例：java -jar live-monitor-record-2.0.jar 直播间ID true/false DouYin/Bili，示例命令```java -jar live-monitor-record-2.0.jar 622216334529 true DouYin```
>   * douyin-start-x.x.jar和bilibili-start-x.x.jar等以```平台-start-x.x.jar```命名格式的jar为单独监听录制该平台的jar包
>     * 通过 java -jar 平台-start-x.x.jar [直播间ID,是否录制] 接参数的形式直接运行开始；也可以不接参数的形式直接运行后通过命令交互的形式启动 
>       * 例：java -jar 平台-start-2.0.jar 直播间ID true/false，示例命令```java -jar douyin-start--2.0.jar 622216334529 true```
>   * 所有的jar包都可以通过 java -jar 的命令直接运行jar包，然后命令行按照提示输入

### 结束运行
> 结束运行程序建议通过```任务栏图标右键```退出，程序运行时任务栏会有对应的图标，右键图标可以选择对应的操作
> 注意：使用ffmpeg的录制必须使用```任务栏图标右键```退出的形式退出，否则可能会出现程序终止后，录制未终止的情况
