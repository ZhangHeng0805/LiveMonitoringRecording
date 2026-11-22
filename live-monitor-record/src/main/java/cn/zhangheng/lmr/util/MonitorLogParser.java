package cn.zhangheng.lmr.util;

import lombok.Data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 监听日志（***监听.log）解析：把每次统计的那一行抽成时间序列，供前端画统计图。
 * <p>
 * 兼容三种平台写法：
 * <pre>
 * 抖音   ：2026-11-20 22:53:54 : 在线人数：129，点赞数：1023，总观看人数：649, 最高在线人数: 140
 * B站    ：2025-08-08 14:12:00 : 关注数：5924728，观看人数：35707
 * 快手    ：2025-08-28 23:24:29 : 粉丝数：1367.9w，点赞数：73.3w
 * </pre>
 * 数值支持千分位逗号与 万/亿/w/W/k/K 单位。
 *
 * @author: ZhangHeng
 */
public class MonitorLogParser {

    public static final String LOG_SUFFIX = "监听.log";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern LINE_TIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*[:：]\\s*(.*)$");
    /** 数字：支持小数、千分位逗号与 万/亿/w/k 单位 */
    private static final String NUM = "([0-9][0-9,.]*\\s*[万wW亿kK]?)";
    private static final Pattern P_ONLINE = Pattern.compile("在线人数[：:]\\s*" + NUM);
    private static final Pattern P_LIKES = Pattern.compile("点赞数[：:]\\s*" + NUM);
    private static final Pattern P_TOTAL = Pattern.compile("总观看人数[：:]\\s*" + NUM);
    private static final Pattern P_MAX_ONLINE = Pattern.compile("最高在线人数[：:]\\s*" + NUM);
    private static final Pattern P_VIEWERS = Pattern.compile("(?<!总)观看人数[：:]\\s*" + NUM);
    private static final Pattern P_FOLLOWERS = Pattern.compile("(?:关注数|粉丝数)[：:]\\s*" + NUM);
    /** 抖音新格式：总计礼物数：829钻 */
    private static final Pattern P_GIFTS = Pattern.compile("总计礼物数[：:]\\s*" + NUM);

    /** 单次统计点，缺省字段为 null（该平台日志没这项） */
    @Data
    public static class Point {
        private long t;
        private String time;
        private Long online;
        private Long likes;
        private Long total;
        private Long maxOnline;
        private Long viewers;
        private Long followers;
        /** 抖音新格式的「总计礼物数」 */
        private Long gifts;
    }

    /** 图表用的一条曲线 */
    @Data
    public static class Metric {
        private String key;
        private String label;
        private String color;
        private String axis; // left / right
    }

    @Data
    public static class Result {
        private List<Point> series;
        private List<Metric> metrics;
        private List<String> files;
        private long start;
        private long end;
        private long maxOnline;
        private long maxLikes;
        private long maxViewers;
        private long maxFollowers;
        /** 最后一次统计的礼物数（抖音新格式） */
        private long maxGifts;
        /** 最后一次统计的总观看人数（抖音类日志） */
        private long lastTotal;
    }

    /**
     * 解析目录下所有 ***监听.log（递归一层，兼容日志放在子目录）
     */
    public static Result parseDir(Path dir) throws IOException {
        List<Path> logs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(LOG_SUFFIX))
                    .sorted()
                    .forEach(logs::add);
        }
        if (logs.isEmpty()) {
            // 目录里没有再往下找一层，兼容按场次分子目录的情况
            try (Stream<Path> stream = Files.walk(dir, 2)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(LOG_SUFFIX))
                        .sorted()
                        .limit(200)
                        .forEach(logs::add);
            }
        }
        List<Point> points = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Path log : logs) {
            names.add(log.getFileName().toString());
            points.addAll(readPoints(log));
        }
        points.sort((a, b) -> Long.compare(a.getT(), b.getT()));
        return build(points, names);
    }

    public static Result parseFile(Path file) throws IOException {
        List<Point> points = readPoints(file);
        List<String> names = new ArrayList<>();
        names.add(file.getFileName().toString());
        return build(points, names);
    }

    /**
     * 读单个日志文件，抽成统计点列表（无有效行时返回空列表）
     */
    private static List<Point> readPoints(Path file) throws IOException {
        List<Point> points = new ArrayList<>();
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return points;
        }
        List<String> lines;
        try (Stream<String> stream = Files.lines(file, StandardCharsets.UTF_8)) {
            lines = stream.collect(Collectors.toList());
        }
        for (String raw : lines) {
            Point p = parseLine(raw);
            if (p != null) {
                points.add(p);
            }
        }
        points.sort((a, b) -> Long.compare(a.getT(), b.getT()));
        return points;
    }

    static Point parseLine(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = LINE_TIME.matcher(raw.trim());
        if (!m.find()) {
            return null;
        }
        String body = m.group(2);
        Point p = new Point();
        p.setTime(m.group(1).replaceAll("\\s+", " "));
        try {
            p.setT(LocalDateTime.parse(p.getTime(), TIME_FMT)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (Exception e) {
            return null;
        }
        boolean matched = false;
        Long online = longOf(P_ONLINE, body);
        if (online != null) {
            p.setOnline(online);
            p.setLikes(longOf(P_LIKES, body));
            p.setTotal(longOf(P_TOTAL, body));
            p.setMaxOnline(longOf(P_MAX_ONLINE, body));
            matched = true;
        }
        Long viewers = longOf(P_VIEWERS, body);
        if (viewers != null) {
            p.setViewers(viewers);
            p.setFollowers(longOf(P_FOLLOWERS, body));
            matched = true;
        }
        Long likes = longOf(P_LIKES, body);
        if (likes != null && p.getLikes() == null) {
            p.setLikes(likes);
            p.setFollowers(longOf(P_FOLLOWERS, body));
            matched = true;
        }
        Long gifts = longOf(P_GIFTS, body);
        if (gifts != null) {
            p.setGifts(gifts);
            matched = true;
        }
        return matched ? p : null;
    }

    private static Long longOf(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        if (!m.find()) {
            return null;
        }
        return parseNumber(m.group(1));
    }

    /**
     * "1023" / "1,023" / "1367.9w" / "1.2亿" 统一换成整数
     */
    static Long parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(",", "").replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        double unit = 1D;
        char last = s.charAt(s.length() - 1);
        if (last == '万' || last == 'w' || last == 'W') {
            unit = 10000D;
            s = s.substring(0, s.length() - 1);
        } else if (last == '亿') {
            unit = 100000000D;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'k' || last == 'K') {
            unit = 1000D;
            s = s.substring(0, s.length() - 1);
        }
        try {
            return Math.round(Double.parseDouble(s) * unit);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Result build(List<Point> points, List<String> files) {
        Result result = new Result();
        result.setFiles(files);
        // 点数过多时按步长稀释，避免一次返回几 MB JSON
        List<Point> series = points;
        int limit = 3000;
        if (points.size() > limit) {
            int step = (int) Math.ceil(points.size() / (double) limit);
            series = new ArrayList<>();
            for (int i = 0; i < points.size(); i += step) {
                series.add(points.get(i));
            }
            Point lastPoint = points.get(points.size() - 1);
            if (series.get(series.size() - 1) != lastPoint) {
                series.add(lastPoint);
            }
        }
        result.setSeries(series);
        if (!series.isEmpty()) {
            result.setStart(series.get(0).getT());
            result.setEnd(series.get(series.size() - 1).getT());
        }
        for (Point p : series) {
            if (p.getOnline() != null) result.setMaxOnline(Math.max(result.getMaxOnline(), p.getOnline()));
            if (p.getLikes() != null) result.setMaxLikes(Math.max(result.getMaxLikes(), p.getLikes()));
            if (p.getViewers() != null) result.setMaxViewers(Math.max(result.getMaxViewers(), p.getViewers()));
            if (p.getFollowers() != null) result.setMaxFollowers(Math.max(result.getMaxFollowers(), p.getFollowers()));
            if (p.getTotal() != null) result.setLastTotal(p.getTotal());
            if (p.getGifts() != null) result.setMaxGifts(p.getGifts());
        }
        result.setMetrics(buildMetrics(series));
        return result;
    }

    /**
     * 按日志里真实出现的字段决定画哪几条线；值一直没变动的（比如固定的粉丝数）不画，避免一条平线挤乱坐标轴
     */
    private static List<Metric> buildMetrics(List<Point> series) {
        Map<String, long[]> bound = new LinkedHashMap<>();
        for (Point p : series) {
            bump(bound, "online", p.getOnline());
            bump(bound, "viewers", p.getViewers());
            bump(bound, "total", p.getTotal());
            bump(bound, "followers", p.getFollowers());
            bump(bound, "likes", p.getLikes());
            bump(bound, "gifts", p.getGifts());
        }
        List<Metric> metrics = new ArrayList<>();
        metrics.add(varyingMetric(bound, "online", "在线人数", "#6366f1", "left"));
        metrics.add(varyingMetric(bound, "viewers", "观看人数", "#0ea5e9", "left"));
        metrics.add(varyingMetric(bound, "total", "总观看人数", "#10b981", "right"));
        metrics.add(varyingMetric(bound, "followers", "粉丝/关注数", "#94a3b8", "right"));
        metrics.add(varyingMetric(bound, "likes", "点赞数", "#f59e0b", "right"));
        metrics.add(varyingMetric(bound, "gifts", "礼物数", "#ec4899", "right"));
        metrics.removeIf(m -> m == null);
        if (metrics.isEmpty()) {
            // 整段日志值都没变化：仍然把出现的字段画出来（平线总比空白强）
            for (String key : bound.keySet()) {
                Metric m = fixedMetric(key);
                if (m != null) {
                    metrics.add(m);
                }
            }
        }
        assignAxis(metrics, bound);
        return metrics;
    }

    /**
     * 左轴放「实时人数」类（在线/观看），右轴放「累计互动」类（总观看、点赞、礼物、粉丝）。
     * 如果日志里没有人数类指标（快手只有点赞、粉丝），左轴就空着，此时统一挪到左轴，避免图表只剩半根轴
     */
    private static void assignAxis(List<Metric> metrics, Map<String, long[]> bound) {
        if (metrics.isEmpty()) {
            return;
        }
        boolean hasLeft = false;
        for (Metric m : metrics) {
            if (!"right".equals(m.getAxis())) {
                hasLeft = true;
                break;
            }
        }
        if (!hasLeft) {
            for (Metric m : metrics) {
                m.setAxis("left");
            }
        }
    }

    private static Metric varyingMetric(Map<String, long[]> bound, String key, String label, String color, String axis) {
        long[] b = bound.get(key);
        if (b == null) {
            return null;
        }
        // max == min 说明整段没变化，不画
        return b[0] == b[1] ? null : metric(key, label, color, axis);
    }

    private static Metric fixedMetric(String key) {
        if ("online".equals(key)) return metric(key, "在线人数", "#6366f1", "left");
        if ("viewers".equals(key)) return metric(key, "观看人数", "#0ea5e9", "left");
        if ("total".equals(key)) return metric(key, "总观看人数", "#10b981", "right");
        if ("followers".equals(key)) return metric(key, "粉丝/关注数", "#94a3b8", "right");
        if ("likes".equals(key)) return metric(key, "点赞数", "#f59e0b", "right");
        if ("gifts".equals(key)) return metric(key, "礼物数", "#ec4899", "right");
        return null;
    }

    private static void bump(Map<String, long[]> bound, String key, Long value) {
        if (value == null) {
            return;
        }
        long[] b = bound.get(key);
        if (b == null) {
            bound.put(key, new long[]{value, value});
        } else {
            b[0] = Math.min(b[0], value);
            b[1] = Math.max(b[1], value);
        }
    }

    private static Metric metric(String key, String label, String color, String axis) {
        Metric m = new Metric();
        m.setKey(key);
        m.setLabel(label);
        m.setColor(color);
        m.setAxis(axis);
        return m;
    }
}
