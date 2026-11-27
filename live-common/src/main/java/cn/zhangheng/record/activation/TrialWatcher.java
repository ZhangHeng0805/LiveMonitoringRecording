package cn.zhangheng.record.activation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @description: 试用期运行期自检：每小时检查一次，
 * 剩余 3 天以内每天提醒一次，到期当天提醒“重启后将无法启动”，但不打断已在运行的监听。
 */
public final class TrialWatcher {

    private static final Logger log = LoggerFactory.getLogger(TrialWatcher.class);
    /** 自检间隔：默认 1 小时（只在剩 3 天以内或到期时才会真正提醒）；可用 -Dxxxr.trial.checkIntervalMs 覆盖，便于联调 */
    private static final long CHECK_INTERVAL_MS = Long.getLong("xxxr.trial.checkIntervalMs", 60L * 60 * 1000L);

    private static ScheduledExecutorService executor;
    /** 通知回调：由界面层注入，(消息, 是否错误) */
    private static volatile BiConsumer<String, Boolean> notifier;
    /** 到期只提醒一次 */
    private static volatile boolean expiredNotified = false;
    /** 剩余天数提醒按日期去重 */
    private static volatile String lastRemindedDate;

    private TrialWatcher() {
    }

    /**
     * 注入通知实现：托盘程序弹气泡，命令行程序只打印日志
     */
    public static void setNotifier(BiConsumer<String, Boolean> notifier) {
        TrialWatcher.notifier = notifier;
    }

    /**
     * 启动自检：已激活（没有试用）时什么都不做
     */
    public static synchronized void start(String deviceUniqueId, String licensePath, int trialDays) {
        ActivationResult initial = ActivationUtil.checkActivation(deviceUniqueId, licensePath, trialDays);
        TrialState.update(initial);
        if (!initial.isTrial()) {
            log.debug("当前不是试用状态，跳过试用期自检");
            return;
        }
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trial-watcher");
            t.setDaemon(true);
            return t;
        });
        // 首次检查延后一个周期：启动时已经提示过一次剩余天数
        executor.scheduleWithFixedDelay(() -> check(deviceUniqueId, licensePath, trialDays),
                CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("试用期自检已启动：剩余 {} 天（共 {} 天）", initial.getTrialRemainingDays(), initial.getTrialDays());
    }

    public static synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void check(String deviceUniqueId, String licensePath, int trialDays) {
        try {
            ActivationResult result = ActivationUtil.checkActivation(deviceUniqueId, licensePath, trialDays);
            TrialState.update(result);

            if (result.isBlocked()) {
                if (result.isTrialExpired() && !expiredNotified) {
                    expiredNotified = true;
                    notice("试用期已结束：程序将在下次启动时要求激活文件；当前正在运行的监听不受影响。"
                            + "到期后不能再新增监听或开启新的录制。", true);
                }
                return;
            }
            if (result.isTrial() && result.getTrialRemainingDays() <= 3) {
                String today = LocalDate.now().toString();
                if (!today.equals(lastRemindedDate)) {
                    lastRemindedDate = today;
                    notice(result.getMessage(), false);
                }
            }
        } catch (Throwable e) {
            log.warn("试用期自检异常：{}", e.getMessage());
        }
    }

    private static void notice(String message, boolean error) {
        if (error) {
            log.error(message);
        } else {
            log.warn(message);
        }
        BiConsumer<String, Boolean> n = notifier;
        if (n != null) {
            try {
                n.accept(message, error);
            } catch (Throwable e) {
                log.warn("试用提醒推送失败：{}", e.getMessage());
            }
        }
    }
}
