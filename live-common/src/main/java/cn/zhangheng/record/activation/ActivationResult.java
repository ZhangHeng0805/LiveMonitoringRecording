package cn.zhangheng.record.activation;

import lombok.Getter;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @description: 启动时的激活状态判定结果：正常激活、试用中、被阻断
 */
@Getter
public class ActivationResult {

    public enum State {
        /** 激活码校验通过 */
        ACTIVATED,
        /** 没有激活文件，处于试用期 */
        TRIAL,
        /** 不允许启动（激活码非法/过期、试用已结束） */
        BLOCKED
    }

    private final State state;
    /** 提示信息：阻断原因、试用剩余天数、即将过期提醒 */
    private final String message;
    /** 试用剩余天数（向上取整，仅 TRIAL 有效） */
    private final long trialRemainingDays;
    /** 试用总天数（TRIAL 与试用到期阻断时有效） */
    private final int trialDays;
    /** 激活信息（激活码校验通过时返回） */
    private final ActivationInfo info;
    /** 是否因为“试用到期”而阻断：运行期业务拦截据此判断 */
    private final boolean trialExpired;

    private ActivationResult(State state, String message, long trialRemainingDays, int trialDays, ActivationInfo info, boolean trialExpired) {
        this.state = state;
        this.message = message;
        this.trialRemainingDays = trialRemainingDays;
        this.trialDays = trialDays;
        this.info = info;
        this.trialExpired = trialExpired;
    }

    public static ActivationResult activated(ActivationInfo info) {
        return new ActivationResult(State.ACTIVATED, null, 0, 0, info, false);
    }

    public static ActivationResult activatedWithWarning(String warning, ActivationInfo info) {
        return new ActivationResult(State.ACTIVATED, warning, 0, 0, info, false);
    }

    /**
     * 试用中：按剩余天数分级提示，剩 3 天以内开始“催激活”
     */
    public static ActivationResult trial(long remainingDays, int trialDays) {
        String message;
        if (remainingDays <= 1) {
            message = "试用仅剩最后 1 天！到期后必须放置激活文件才能继续使用，请尽快获取激活码。";
        } else if (remainingDays <= 3) {
            message = "试用仅剩" + remainingDays + "天（共" + trialDays + "天），请尽快获取激活码，到期后需放置激活文件才能继续使用。";
        } else {
            message = "首次使用试用中：剩余" + remainingDays + "天（共" + trialDays + "天），到期后需放置激活文件才能继续使用。";
        }
        return new ActivationResult(State.TRIAL, message, remainingDays, trialDays, null, false);
    }

    public static ActivationResult blocked(String message) {
        return new ActivationResult(State.BLOCKED, message, 0, 0, null, false);
    }

    /** 试用到期导致阻断：区别于“激活码非法”的阻断 */
    public static ActivationResult trialExpiredBlocked(String message, int trialDays) {
        return new ActivationResult(State.BLOCKED, message, 0, trialDays, null, true);
    }

    public boolean isBlocked() {
        return state == State.BLOCKED;
    }

    public boolean isTrial() {
        return state == State.TRIAL;
    }

    public boolean isActivatedState() {
        return state == State.ACTIVATED;
    }

    public boolean hasMessage() {
        return message != null && !message.isEmpty();
    }
}
