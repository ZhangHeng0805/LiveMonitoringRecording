package cn.zhangheng.common.activation;

import lombok.Data;

import java.util.Date;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/29 星期五 19:15
 * @version: 1.0
 * @description:
 */
@Data
public class ActivationInfo {
    private String deviceUniqueId;//设备唯一ID
    private Long activationTime;//激活时间
    private int activationCodeValidDays;//激活码有效天数，小于0永久
}
