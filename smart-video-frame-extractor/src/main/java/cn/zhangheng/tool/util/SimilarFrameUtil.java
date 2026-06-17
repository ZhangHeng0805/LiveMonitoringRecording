package cn.zhangheng.tool.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/12 星期五 00:34
 * @version: 1.0
 * @description:
 */
public class SimilarFrameUtil {
    private static final int PHASH_SIMILAR_THRESHOLD = 10;
    // 限制最大缓存数量，防止无限膨胀
    private static final int MAX_CACHE_SIZE = 200;
    private static final List<FrameHashItem> frameItemList = new ArrayList<>();
    // 读写锁：读共享、写独占
    private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private static final Lock writeLock = rwLock.writeLock();

    /**
     * @param newHash  当前帧aHash
     * @param newScore 当前帧综合打分
     * @return true=相似舍弃当前帧；false=不相似/当前分更高，保留
     */
    public static boolean isSimilarFrame(String newHash, double newScore) {
        // 空哈希直接丢弃
        if (newHash == null || newHash.isEmpty()) {
            return true;
        }
        writeLock.lock();
        // 查找同相似组里最高分条目
        FrameHashItem similarBestItem = null;
        // ========== 读阶段：共享锁，多线程可同时进入 ==========
        try {
            for (FrameHashItem item : frameItemList) {
                int diff = calcHammingDistance(newHash, item.hash);
                if (diff <= PHASH_SIMILAR_THRESHOLD) {
                    if (similarBestItem == null || item.score > similarBestItem.score) {
                        similarBestItem = item;
                    }
                }
            }

            if (similarBestItem != null) {
                // 存在相似画面
                if (newScore > similarBestItem.score) {
                    // 新帧分数更高，替换旧的哈希和分数，保留高质量帧
                    similarBestItem.hash = newHash;
                    similarBestItem.score = newScore;
                    return false; // 当前帧胜出，可保存
                } else {
                    // 旧帧分数更高，舍弃当前
                    return true;
                }
            } else {
                // 无相似画面，新增记录
                frameItemList.add(new FrameHashItem(newHash, newScore));
                // 超出容量，淘汰最旧一条
                while (frameItemList.size() > MAX_CACHE_SIZE) {
                    frameItemList.remove(0);
                }
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static int calcHammingDistance(String h1, String h2) {
        return fastHamming(h1.toCharArray(), h2.toCharArray());
    }


    private static int fastHamming(char[] a, char[] b) {
        int diff = 0;
        int len = a.length;
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                diff++;
            }
        }
        return diff;
    }

    static class FrameHashItem {
        String hash;
        double score;

        FrameHashItem(String hash, double score) {
            this.hash = hash;
            this.score = score;
        }
    }
}
