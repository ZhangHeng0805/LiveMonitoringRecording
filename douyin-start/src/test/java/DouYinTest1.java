import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.douyin.service.DouYinMain;
import cn.zhangheng.douyin.bean.DouYinRoom;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 08:29
 * @version: 1.0
 * @description:
 */
public class DouYinTest1 {
    public static void main(String[] args) {
        Setting setting=new Setting();

        DouYinMain douYinMain = new DouYinMain(setting);

        //主播直播间ID
        //小兰花-622216334529
        //超级喜欢uu子-816560967344
        //电影频道央影传媒-208823316033
        //孜由基-498534107811
        //百变小晨🐻-384647754992
        //coke老师-989125753255
        //笑笑 💨-180298813313
        //兔娘-142381563944

        douYinMain.start(new DouYinRoom("208823316033"),true);

    }
}
