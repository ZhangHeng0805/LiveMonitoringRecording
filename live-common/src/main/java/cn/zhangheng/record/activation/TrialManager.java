package cn.zhangheng.record.activation;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @description: 首次使用试用期管理：记录首次使用时间，按天递减。
 * 记录用设备ID派生的 HMAC 签名，篡改时间会判定为无效记录；同时在用户目录留一份备份，
 * 只删工作目录的文件不会重置试用期。所有异常都在内部消化，不向启动流程抛出。
 */
public final class TrialManager {

    public static final long DAY_MS = 24L * 60 * 60 * 1000L;
    /** 时钟容差：首次使用时间最多可比当前时间晚 1 天（兼容时区/时钟略快的情况） */
    private static final long CLOCK_TOLERANCE_MS = DAY_MS;
    private static final String TRIAL_FILE_NAME = "xxxr-trial.dat";
    private static final String BACKUP_FILE_NAME = ".xxxr-trial.dat";
    /** Windows 注册表（经 java.util.prefs 落到 HKCU\Software\JavaSoft\Prefs） */
    private static final String PREF_NODE = "cn/zhangheng/lmr";
    private static final String PREF_KEY = "trial-v1";
    /** Windows 公共程序数据目录下的副本 */
    private static final String PROGRAM_DATA_DIR = "XingXiXiangRong";
    private static final String PROGRAM_DATA_SUB_DIR = "LiveMonitoringRecording";
    private static final String SIGN_SALT = "xxxr-trial-v1";
    private static final String HMAC_ALG = "HmacSHA256";

    private TrialManager() {
    }

    /**
     * 试用状态
     */
    public static class TrialStatus {
        private final boolean expired;
        private final long firstUseTime;
        private final long remainingMillis;
        private final int trialDays;

        TrialStatus(boolean expired, long firstUseTime, long remainingMillis, int trialDays) {
            this.expired = expired;
            this.firstUseTime = firstUseTime;
            this.remainingMillis = remainingMillis;
            this.trialDays = trialDays;
        }

        public boolean isExpired() {
            return expired;
        }

        public long getFirstUseTime() {
            return firstUseTime;
        }

        /** 剩余天数：向上取整，不足一天按一天 */
        public long getRemainingDays() {
            if (remainingMillis <= 0) {
                return 0;
            }
            return (remainingMillis + DAY_MS - 1) / DAY_MS;
        }

        public int getTrialDays() {
            return trialDays;
        }
    }

    /**
     * 检查试用状态：首次调用会创建试用记录；已结束时返回 expired。
     *
     * @param deviceUniqueId 设备唯一标识，用于绑定与签名
     * @param trialDays      试用总天数，小于 1 时按 1 天处理
     */
    public static TrialStatus check(String deviceUniqueId, int trialDays) {
        int days = Math.max(1, trialDays);
        long totalMillis = (long) days * DAY_MS;
        long now = System.currentTimeMillis();

        TrialRecord record = load(deviceUniqueId);
        if (record == null) {
            // 首次使用：从现在开始计时
            TrialRecord created = new TrialRecord(now, now);
            save(created, deviceUniqueId);
            return new TrialStatus(false, now, totalMillis, days);
        }

        // 防时钟回拨：以“当前时间”和“上次检查时间”中较晚者为基准，把时间往回调不会延长试用
        long baseNow = Math.max(now, record.lastCheckTime);
        long deadline = record.firstUseTime + totalMillis;
        long remaining = deadline - baseNow;

        record.lastCheckTime = baseNow;
        save(record, deviceUniqueId);

        if (remaining <= 0) {
            return new TrialStatus(true, record.firstUseTime, 0, days);
        }
        return new TrialStatus(false, record.firstUseTime, remaining, days);
    }

    /**
     * 读取有效记录：工作目录、用户目录、公共程序数据目录、注册表四处并存，
     * 取最早的首次使用时间——只删其中一两处不会重置试用期。
     */
    private static TrialRecord load(String deviceUniqueId) {
        List<TrialRecord> records = new ArrayList<>();
        collect(records, readQuietly(primaryPath()), deviceUniqueId);
        collect(records, readQuietly(backupPath()), deviceUniqueId);
        Path programData = programDataPath();
        if (programData != null) {
            collect(records, readQuietly(programData), deviceUniqueId);
        }
        collect(records, readPrefs(), deviceUniqueId);
        if (records.isEmpty()) {
            return null;
        }
        // 首次使用时间取最早（防止旧副本被删），上次检查时间取最新（防时钟回拨）
        long earliestFirstUse = records.get(0).firstUseTime;
        long latestCheck = records.get(0).lastCheckTime;
        for (TrialRecord r : records) {
            earliestFirstUse = Math.min(earliestFirstUse, r.firstUseTime);
            latestCheck = Math.max(latestCheck, r.lastCheckTime);
        }
        return new TrialRecord(earliestFirstUse, Math.max(earliestFirstUse, latestCheck));
    }

    private static void collect(List<TrialRecord> records, byte[] bytes, String deviceUniqueId) {
        TrialRecord record = parse(bytes, deviceUniqueId);
        if (record != null) {
            records.add(record);
        }
    }

    private static TrialRecord parse(byte[] bytes, String deviceUniqueId) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(new String(bytes, StandardCharsets.UTF_8));
            String device = obj.getStr("deviceUniqueId");
            Long firstUseTime = obj.getLong("firstUseTime");
            Long lastCheckTime = obj.getLong("lastCheckTime");
            String signature = obj.getStr("signature");
            if (device == null || firstUseTime == null || lastCheckTime == null || signature == null) {
                return null;
            }
            // 设备变了、签名不符（时间被改过）都视为无效记录
            if (!deviceUniqueId.equals(device)) {
                return null;
            }
            if (!signature.equals(sign(deviceUniqueId, firstUseTime, lastCheckTime))) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (firstUseTime <= 0 || firstUseTime > now + CLOCK_TOLERANCE_MS) {
                return null;
            }
            if (lastCheckTime < firstUseTime) {
                return null;
            }
            return new TrialRecord(firstUseTime, lastCheckTime);
        } catch (Exception e) {
            return null;
        }
    }

    private static void save(TrialRecord record, String deviceUniqueId) {
        JSONObject obj = new JSONObject();
        obj.set("deviceUniqueId", deviceUniqueId);
        obj.set("firstUseTime", record.firstUseTime);
        obj.set("lastCheckTime", record.lastCheckTime);
        obj.set("signature", sign(deviceUniqueId, record.firstUseTime, record.lastCheckTime));
        byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
        writeQuietly(primaryPath(), bytes);
        writeQuietly(backupPath(), bytes);
        Path programData = programDataPath();
        if (programData != null) {
            writeQuietly(programData, bytes);
        }
        writePrefs(bytes);
    }

    /**
     * 先写临时文件再原子替换，避免写到一半断电导致记录损坏
     */
    private static void writeQuietly(Path path, byte[] bytes) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // 写失败不影响本次判定：内存中的状态已经算出来了
        }
    }

    private static byte[] readQuietly(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path primaryPath() {
        return Paths.get(TRIAL_FILE_NAME).toAbsolutePath();
    }

    private static Path backupPath() {
        return Paths.get(System.getProperty("user.home", "."), BACKUP_FILE_NAME);
    }

    /**
     * 公共程序数据目录副本（Windows 为 %ProgramData%，其它系统没有则跳过）
     */
    private static Path programDataPath() {
        String programData = System.getenv("ProgramData");
        if (programData == null || programData.trim().isEmpty()) {
            return null;
        }
        return Paths.get(programData.trim(), PROGRAM_DATA_DIR, PROGRAM_DATA_SUB_DIR, TRIAL_FILE_NAME);
    }

    /**
     * 注册表副本：用 java.util.prefs，Windows 下落在 HKCU\Software\JavaSoft\Prefs 的对应节点，
     * 不需要管理员权限，也不弹任何窗口。
     */
    private static byte[] readPrefs() {
        try {
            String value = Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, null);
            if (value == null || value.isEmpty()) {
                return null;
            }
            return value.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writePrefs(byte[] bytes) {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            prefs.put(PREF_KEY, new String(bytes, StandardCharsets.UTF_8));
            prefs.flush();
        } catch (Exception ignored) {
            // 注册表写失败不影响其它副本
        }
    }

    /**
     * 签名 = HMAC-SHA256(key = 设备ID + 盐, data = 首次使用时间|上次检查时间)
     */
    private static String sign(String deviceUniqueId, long firstUseTime, long lastCheckTime) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec((deviceUniqueId + "|" + SIGN_SALT).getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] hash = mac.doFinal((firstUseTime + "|" + lastCheckTime).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static class TrialRecord {
        private final long firstUseTime;
        private long lastCheckTime;

        TrialRecord(long firstUseTime, long lastCheckTime) {
            this.firstUseTime = firstUseTime;
            this.lastCheckTime = lastCheckTime;
        }
    }
}
