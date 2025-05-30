import cn.zhangheng.bilibili.BiliMain;
import cn.zhangheng.bilibili.bean.BiliRoom;
import cn.zhangheng.common.bean.Constant;
import com.zhangheng.util.SettingUtil;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 22:34
 * @version: 1.0
 * @description:
 */
public class BiliTest1 {
    public static void main(String[] args) throws Exception {
        SettingUtil setting = new SettingUtil(Constant.Setting_Name);
        BiliMain biliMain = new BiliMain(setting);

        //小兰花2号-1842356262
        //哔哩哔哩王者荣耀赛事-55
        //崽子要奋斗-26043381
        //Ahri小狐狸呀-23680601
        BiliRoom room = new BiliRoom("55");
        biliMain.start(room,true);
    }
}
