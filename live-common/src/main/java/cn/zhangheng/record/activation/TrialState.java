package cn.zhangheng.record.activation;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @description: 运行期试用状态：由启动校验与定时自检更新，供业务层拦截“新增监听/开启录制”。
 */
public final class TrialState {

    /** 是否已因试用到期而受限 */
    private static volatile boolean trialExpired = false;
    /** 是否已用激活码正常激活 */
    private static volatile boolean activated = false;
    /** 试用剩余天数（向上取整） */
    private static volatile long remainingDays = 0;

    private TrialState() {
    }

    public static boolean isTrialExpired() {
        return trialExpired;
    }

    public static boolean isActivated() {
        return activated;
    }

    public static long getRemainingDays() {
        return remainingDays;
    }

    public static void update(ActivationResult result) {
        if (result == null) {
            return;
        }
        if (result.isBlocked()) {
            trialExpired = result.isTrialExpired();
            activated = false;
            remainingDays = 0;
        } else if (result.isTrial()) {
            trialExpired = false;
            remainingDays = result.getTrialRemainingDays();
        } else if (result.isActivatedState()) {
            trialExpired = false;
            activated = true;
            remainingDays = 0;
        }
    }
}
