package cn.zhangheng.record.activation;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhangheng.util.EncryptUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/29 星期五 19:36
 * @version: 1.0
 * @description: 激活码校验工具：解码 → 结构校验 → 签名校验 → 设备校验 → 有效期校验
 */
public class ActivationUtil {

    /** 一天的毫秒数 */
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** 剩余有效期的提醒阈值：7 天 */
    private static final long WARN_REMAIN_MS = 7L * DAY_MS;
    /** 允许的系统时钟容差：激活时间最多可比当前时间晚 5 分钟 */
    private static final long CLOCK_TOLERANCE_MS = 5L * 60 * 1000;
    /** 激活文件大小上限，防止误选大文件（如日志、镜像）导致内存占用 */
    private static final long MAX_ACTIVATION_FILE_BYTES = 1024 * 1024;

    public static void main(String[] args) {
        String deviceUniqueId = new DeviceInfoCollector().getDeviceUniqueId();
        System.out.println("该设备的唯一软件标识为: " + deviceUniqueId);
    }

    /**
     * 校验激活码，仅关心是否通过；失败抛 {@link ErrorException}（不可用）或 {@link WarnException}（即将过期）
     */
    public static void verifyActivationCode(String deviceUniqueId, String activationCode) throws ErrorException, WarnException {
        verifyActivationCodeAndGetInfo(deviceUniqueId, activationCode);
    }

    /**
     * 校验激活码并返回激活信息，便于调用方展示到期时间或剩余天数
     *
     * @return 通过校验的激活信息
     */
    public static ActivationInfo verifyActivationCodeAndGetInfo(String deviceUniqueId, String activationCode) throws ErrorException, WarnException {
        if (StrUtil.isBlank(deviceUniqueId)) {
            throw new ErrorException("设备唯一标识为空，无法校验激活码！");
        }
        ActivationInfo info = parseAndVerify(activationCode);
        if (!deviceUniqueId.equals(info.getDeviceUniqueId())) {
            throw new ErrorException("激活码设备不一致，验证失败！");
        }
        checkValidPeriod(info, System.currentTimeMillis());
        return info;
    }

    public static void verifyActivationCodeFile(String deviceUniqueId, String filePath) throws ErrorException, WarnException {
        verifyActivationCode(deviceUniqueId, readActivationCode(filePath));
    }

    /**
     * 启动时的统一校验入口：
     * 1. 激活文件存在 → 按激活码校验，非法/过期直接阻断（不退回试用，避免过期码变相续期）；
     * 2. 激活文件不存在 → 首次使用进入试用期，{@code trialDays <= 0} 表示不开放试用。
     *
     * @return 判定结果，调用方按 isBlocked()/hasMessage() 决定退出或提示
     */
    public static ActivationResult checkActivation(String deviceUniqueId, String licensePath, int trialDays) {
        if (licenseExists(licensePath)) {
            try {
                ActivationInfo info = parseAndVerify(readActivationCode(licensePath));
                if (!deviceUniqueId.equals(info.getDeviceUniqueId())) {
                    throw new ErrorException("激活码设备不一致，验证失败！");
                }
                try {
                    checkValidPeriod(info, System.currentTimeMillis());
                } catch (WarnException warning) {
                    // 即将过期：仍允许启动，只做提醒
                    return ActivationResult.activatedWithWarning(warning.getMessage(), info);
                }
                return ActivationResult.activated(info);
            } catch (ErrorException e) {
                return ActivationResult.blocked(e.getMessage());
            }
        }
        if (trialDays <= 0) {
            return ActivationResult.blocked("未找到激活文件（" + licensePath + "），且未开放试用，请获取激活码后重试！");
        }
        TrialManager.TrialStatus status = TrialManager.check(deviceUniqueId, trialDays);
        if (status.isExpired()) {
            return ActivationResult.trialExpiredBlocked(
                    "试用期已结束（共" + status.getTrialDays() + "天），请将激活文件放到程序目录后重新启动！",
                    status.getTrialDays());
        }
        return ActivationResult.trial(status.getRemainingDays(), status.getTrialDays());
    }

    private static boolean licenseExists(String licensePath) {
        if (StrUtil.isBlank(licensePath)) {
            return false;
        }
        try {
            return Files.exists(Paths.get(licensePath.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取激活文件并清洗内容：去掉 BOM、换行、空格（激活码常被编辑器按行折行保存）
     */
    private static String readActivationCode(String filePath) throws ErrorException {
        if (StrUtil.isBlank(filePath)) {
            throw new ErrorException("未配置激活文件路径！");
        }
        Path path;
        try {
            path = Paths.get(filePath.trim());
        } catch (Exception e) {
            throw new ErrorException("激活文件路径非法：" + filePath, e);
        }
        if (!Files.exists(path)) {
            throw new ErrorException("激活文件不存在：" + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ErrorException("激活文件不是普通文件：" + path);
        }
        try {
            if (Files.size(path) > MAX_ACTIVATION_FILE_BYTES) {
                throw new ErrorException("激活文件过大，请确认选择的是激活码文件：" + path);
            }
            String code = cleanCode(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            if (StrUtil.isEmpty(code)) {
                throw new ErrorException("激活文件内容为空：" + path);
            }
            return code;
        } catch (IOException e) {
            throw new ErrorException("激活文件读取失败：" + path, e);
        }
    }

    /**
     * 解析并校验签名，任何一步失败都给出明确原因，不再统一提示“验证失败”
     */
    private static ActivationInfo parseAndVerify(String activationCode) throws ErrorException {
        String json = decode(activationCode);

        JSONObject entries;
        try {
            entries = JSONUtil.parseObj(json);
        } catch (Exception e) {
            throw new ErrorException("激活码内容无法解析，请检查激活码是否复制完整！", e);
        }
        if (!entries.containsKey("data")) {
            throw new ErrorException("激活码缺少数据内容，验证失败！");
        }
        String signature = StrUtil.trimToEmpty(entries.getStr("signature", ""));
        if (StrUtil.isEmpty(signature)) {
            throw new ErrorException("激活码缺少签名信息，验证失败！");
        }

        ActivationInfo info;
        try {
            info = entries.get("data", ActivationInfo.class);
        } catch (Exception e) {
            throw new ErrorException("激活码数据格式异常，验证失败！", e);
        }
        if (info == null || StrUtil.isBlank(info.getDeviceUniqueId()) || info.getActivationTime() == null) {
            throw new ErrorException("激活码数据不完整，验证失败！");
        }

        String expected;
        try {
            expected = info.generateSignature();
        } catch (Exception e) {
            throw new ErrorException("激活码签名计算失败！", e);
        }
        if (!signatureEquals(signature, expected)) {
            throw new ErrorException("激活码签名校验失败，激活码可能已损坏或被篡改！");
        }
        return info;
    }

    /**
     * 解码激活码：清洗空白 → 兼容 URL-safe 字符 → 按需补齐 Base64 padding
     */
    private static String decode(String activationCode) throws ErrorException {
        String code = cleanCode(activationCode);
        if (StrUtil.isEmpty(code)) {
            throw new ErrorException("激活码为空！");
        }
        // 兼容 Base64URL 写法（部分工具导出的激活码使用 - 和 _）
        code = code.replace('-', '+').replace('_', '/');
        if (!code.matches("[A-Za-z0-9+/=]+")) {
            throw new ErrorException("激活码包含非法字符，请确认复制的是完整激活码！");
        }
        int remainder = code.length() % 4;
        if (remainder == 1) {
            throw new ErrorException("激活码长度异常，请检查是否复制完整！");
        } else if (remainder == 2) {
            code = code + "==";
        } else if (remainder == 3) {
            code = code + "=";
        }
        try {
            String json = EncryptUtil.deBase64Str(code);
            if (StrUtil.isBlank(json)) {
                throw new ErrorException("激活码解码后内容为空，请检查激活码是否完整！");
            }
            return json;
        } catch (ErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new ErrorException("激活码格式异常（Base64 解码失败），请确认激活码完整！", e);
        }
    }

    /**
     * 校验激活时间与有效期
     */
    private static void checkValidPeriod(ActivationInfo info, long now) throws ErrorException, WarnException {
        long activationTime = info.getActivationTime();
        if (activationTime > now + CLOCK_TOLERANCE_MS) {
            throw new ErrorException("激活码激活时间异常（晚于当前系统时间），请检查系统时间后重试！");
        }
        int validDays = info.getActivationCodeValidDays();
        if (validDays < 0) {
            // 小于 0 表示永久有效
            return;
        }
        long validPeriod = (long) validDays * DAY_MS;
        long used = Math.max(0L, now - activationTime);
        if (used > validPeriod) {
            throw new ErrorException("激活码已过期，请重新获取！");
        }
        long remaining = validPeriod - used;
        if (remaining <= WARN_REMAIN_MS) {
            // 不足一天按一天算，避免出现“有效期剩余 0 天”却仍能使用的歧义
            long remainingDays = (remaining + DAY_MS - 1) / DAY_MS;
            throw new WarnException("激活码有效期剩余" + remainingDays + "天，请及时更新激活码！以免影响后续使用");
        }
    }

    /**
     * 签名比较：忽略大小写差异，并按常量时间比较，避免逐字符短路
     */
    private static boolean signatureEquals(String actual, String expected) {
        if (expected == null) {
            return false;
        }
        byte[] a = actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * 清洗文本：去掉所有空白（含全角空格）、BOM 和不可见占位字符
     */
    private static String cleanCode(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\u00A0\\u3000\\uFEFF]", "");
    }
}
