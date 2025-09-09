package cn.zhangheng.common.activation;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhangheng.util.EncryptUtil;
import com.zhangheng.util.TimeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/29 星期五 19:36
 * @version: 1.0
 * @description:
 */
public class ActivationUtil {

    public static void main(String[] args) {
        System.out.println("该设备的唯一软件标识为: " + new DeviceInfoCollector().getDeviceUniqueId());
    }


    public static boolean verifyActivationCode(String deviceUniqueId, String activationCode) throws ErrorException, WarnException {
        String deJson = EncryptUtil.deBase64Str(activationCode);
        try {
            boolean verify = EncryptUtil.signDecodeJson(deJson);
            if (verify) {
                ActivationInfo info = JSONUtil.parseObj(deJson).get("data", ActivationInfo.class);
                if (!deviceUniqueId.equals(info.getDeviceUniqueId())) {
                    throw new ErrorException("激活码设备不一致，验证失败！");
                }
                if (info.getActivationCodeValidDays() > -1) {
                    long validPeriod = info.getActivationCodeValidDays() * 24 * 60 * 60 * 1000L;
                    long currentTimeMillis = System.currentTimeMillis();
                    if (currentTimeMillis - info.getActivationTime() > validPeriod) {
                        throw new ErrorException("激活码已过期，请重新获取！");
                    }
                    long warnPeriod = 7 * 24 * 60 * 60 * 1000L;//剩余7天警告
                    if (currentTimeMillis - info.getActivationTime() > (validPeriod - warnPeriod)) {
                        long remaining = validPeriod - (currentTimeMillis - info.getActivationTime());
                        long l = remaining / (24 * 60 * 60 * 1000L);
                        throw new WarnException("激活码有效期剩余" + l + "天，请及时更新激活码！以免影响后续使用");
                    }
                }
                return true;
            } else {
                throw new ErrorException("激活码格式异常，验证失败！");
            }
        } catch (ErrorException | WarnException e1) {
            throw e1;
        } catch (Exception e) {
            throw new ErrorException("激活码验证失败！", e);
        }
    }

    public static boolean verifyActivationCodeFile(String deviceUniqueId, String filePath) throws ErrorException, WarnException {
        List<String> strings = null;
        try {
            strings = Files.readAllLines(Paths.get(filePath));
        } catch (NoSuchFileException e1) {
            throw new ErrorException(e1.getLocalizedMessage() + " 激活文件不存在！");
        } catch (IOException e) {
            throw new ErrorException("激活文件读取失败！", e);
        }
        String activationCode = String.join("", strings);
        return verifyActivationCode(deviceUniqueId, activationCode);
    }
}
