    package cn.zhangheng.common.activation;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhangheng.util.EncryptUtil;

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


    public static boolean verifyActivationCode(String deviceUniqueId, String activationCode) throws IllegalArgumentException {
        String deJson = EncryptUtil.deBase64Str(activationCode);
        try {
            boolean verify = EncryptUtil.signDecodeJson(deJson);
            if (verify) {
                ActivationInfo info = JSONUtil.parseObj(deJson).get("data", ActivationInfo.class);
                if (!deviceUniqueId.equals(info.getDeviceUniqueId())) {
                    throw new IllegalArgumentException("激活码设备不一致，验证失败！");
                }
                if (info.getActivationCodeValidDays() > -1) {
                    long validPeriod = info.getActivationCodeValidDays() * 24 * 60 * 60 * 1000L;
                    if (System.currentTimeMillis() - info.getActivationTime() > validPeriod) {
                        throw new IllegalArgumentException("激活码已过期，请重新获取！");
                    }
                }
                return true;
            } else {
                throw new IllegalArgumentException("激活码格式异常，验证失败！");
            }
        } catch (IllegalArgumentException e1) {
            throw e1;
        } catch (Exception e) {
            throw new IllegalArgumentException("激活码验证失败！", e);
        }
    }

    public static boolean verifyActivationCodeFile(String deviceUniqueId, String filePath) throws IllegalArgumentException {
        List<String> strings = null;
        try {
            strings = Files.readAllLines(Paths.get(filePath));
        } catch (NoSuchFileException e1) {
            throw new IllegalArgumentException(e1.getLocalizedMessage() + " 激活文件不存在！");
        } catch (IOException e) {
            throw new IllegalArgumentException("激活文件读取失败！", e);
        }
        String activationCode = String.join("", strings);
        return verifyActivationCode(deviceUniqueId, activationCode);
    }
}
