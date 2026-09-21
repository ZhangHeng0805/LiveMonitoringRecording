# cpolar端口映射工具使用说明
> [cpolar极点云](https://www.cpolar.com/): 公开一个本地Web站点至公网。
> 只需一行命令，就可以将内网站点发布至公网，方便给客户演示。高效调试微信公众号、小程序、对接支付宝网关等云端服务，提高您的编程效率。

 本项目需要配合GitHub Page使用
 * 1、先创建一个github项目，可以参考我的项目[LiveMonitoringRecordingPage](https://github.com/ZhangHeng0805/LiveMonitoringRecordingPage)
 * 2、然后项目中创建一个空json文件
 * 3、然后使用git将项目克隆至电脑本地
 * 4、在GitHub上开启该项目的Pages功能 ```Settings > Pages``` 开启后即可获得一个静态网页的访问地址，因为没有前端文件，所有访问地址后面加上刚创建的json文件的相对路径即可访问该文件内容
 * 5、本地创建配置文件cpolar.setting
```properties
#可以利用GitHub Page功能将端口映射的公网地址自动更新至git仓库中，然后访问该文件即可实时获取最新的公网映射地址

#端口映射程序路径
execute program.path=./bin/cpolar.exe

#git仓库目录
#git.repository.dir=F:/Git Project/LiveMonitoringRecordingPage

#需要更新的git文件相对路径
#git.file.relative.path=redirect/json/config.json

```

> 建议直接克隆我的项目[LiveMonitoringRecordingPage](https://github.com/ZhangHeng0805/LiveMonitoringRecordingPage)，
> 注意修改```js/mainURL.js```文件中30行左右的configUrl的配置，改成你项目json文件的访问路径