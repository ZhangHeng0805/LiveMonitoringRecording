package cn.zhangheng.common.activation;

import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Formatter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/29 星期五 18:37
 * @version: 1.0
 * @description:
 */
public class DeviceInfoCollector {
    private static final Logger log = LoggerFactory.getLogger(DeviceInfoCollector.class);

    /**
     * 获取设备的唯一标识
     *
     * @return 设备唯一标识字符串
     */
    public String getDeviceUniqueId() {
        StringBuilder deviceInfo = new StringBuilder();

        try {
            // 获取主板序列号
            String motherboardSerial = getMotherboardSerial();
            if (motherboardSerial != null) {
                deviceInfo.append(motherboardSerial);
            }

            // 获取CPU信息
            String cpuInfo = getCPUInfo();
            if (cpuInfo != null) {
                deviceInfo.append(cpuInfo);
            }

            // 获取MAC地址
            String macAddress = getMacAddress();
            if (macAddress != null) {
                deviceInfo.append(macAddress);
            }
//            System.out.println(deviceInfo);
            // 对设备信息进行MD5哈希，生成固定长度的唯一标识
            return generateMD5Hash(deviceInfo.toString());
        } catch (Exception e) {
            log.error("获取设备的唯一标识失败：" + ThrowableUtil.getAllCauseMessage(e), e);
            return null;
        }
    }

    /**
     * 获取主板序列号
     */
    private String getMotherboardSerial() {
        String result = "";
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"wmic", "baseboard", "get", "serialnumber"});
            process.getOutputStream().close();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equals("SerialNumber")) {
                    result = line;
                    break;
                }
            }
        } catch (IOException e) {
            // 在非Windows系统上会抛出异常，这里忽略
        }
        return result;
    }

    /**
     * 获取CPU信息
     */
    private String getCPUInfo() {
        String result = "";
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"wmic", "cpu", "get", "processorid"});
            process.getOutputStream().close();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equals("ProcessorId")) {
                    result = line;
                    break;
                }
            }
        } catch (IOException e) {
            // 在非Windows系统上会抛出异常，这里忽略
        }
        return result;
    }

    /**
     * 获取MAC地址
     */
    private String getMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] hardwareAddress = ni.getHardwareAddress();
                if (hardwareAddress != null && hardwareAddress.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hardwareAddress) {
                        sb.append(String.format("%02X:", b));
                    }
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    return sb.toString();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成MD5哈希值
     */
    private String generateMD5Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes());

        try (Formatter formatter = new Formatter()) {
            for (byte b : hashBytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }
}
