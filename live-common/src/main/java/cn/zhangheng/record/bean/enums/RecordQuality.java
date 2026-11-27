package cn.zhangheng.record.bean.enums;

import cn.hutool.core.util.StrUtil;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/11/27 星期五
 * @version: 1.0
 * @description: 录制画质档位：取直播流清晰度排序中的第几档
 */
public enum RecordQuality {
    /**
     * 最高画质：清晰度第一档
     */
    HIGH("最高"),
    /**
     * 中等画质：中间档（档位数为偶数时偏下）
     */
    MIDDLE("中等"),
    /**
     * 最低画质：最后一档
     */
    LOW("最低"),
    ;

    private final String label;

    RecordQuality(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 解析画质：兼容中文标签（最高/中等/最低）、枚举名（HIGH/MIDDLE/LOW）和英文别名，
     * 无法识别或为空时按“最高”处理，避免配置写错导致录制选不到流
     */
    public static RecordQuality of(String value) {
        if (StrUtil.isBlank(value)) return HIGH;
        String text = value.trim();
        for (RecordQuality quality : values()) {
            if (quality.name().equalsIgnoreCase(text) || quality.label.equals(text)) {
                return quality;
            }
        }
        if ("high".equalsIgnoreCase(text) || "max".equalsIgnoreCase(text)) return HIGH;
        if ("middle".equalsIgnoreCase(text) || "medium".equalsIgnoreCase(text)) return MIDDLE;
        if ("low".equalsIgnoreCase(text) || "min".equalsIgnoreCase(text)) return LOW;
        return HIGH;
    }

    /**
     * 在给定档位数量里取下标：最高=0，最低=size-1，中等=size/2（偶数档偏下）
     */
    public int indexOf(int size) {
        if (size <= 1) return 0;
        if (this == LOW) return size - 1;
        if (this == MIDDLE) return Math.min(size - 1, size / 2);
        return 0;
    }
}
