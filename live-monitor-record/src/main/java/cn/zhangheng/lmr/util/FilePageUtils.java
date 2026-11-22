package cn.zhangheng.lmr.util;

import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/06/21 星期日 04:13
 * @version: 1.0
 * @description:
 */
public class FilePageUtils {
    /**
     * 目录/文件展示顺序：
     * 1. 目录排在文件前面；
     * 2. 目录按名称里的日期倒序（2026-11-21 在 2026-11-20 前面），解析不出日期的按最后修改时间倒序；
     * 3. 文件按最后修改时间倒序；
     * 4. 同优先级时按名称倒序，保证顺序稳定、分页不跳。
     */
    private static final Comparator<FileResult> FILE_PAGE_ORDER = new Comparator<FileResult>() {
        @Override
        public int compare(FileResult a, FileResult b) {
            boolean folderA = "folder".equals(a.getType());
            boolean folderB = "folder".equals(b.getType());
            if (folderA != folderB) {
                return folderA ? -1 : 1;
            }
            if (folderA) {
                LocalDate dateA = parseFolderDate(a.getName());
                LocalDate dateB = parseFolderDate(b.getName());
                if (dateA != null && dateB != null) {
                    int byDate = dateB.compareTo(dateA);
                    if (byDate != 0) {
                        return byDate;
                    }
                } else if (dateA != null) {
                    return -1;
                } else if (dateB != null) {
                    return 1;
                }
                // 解析不出日期的目录（房间名目录等）按名称正序，保持稳定易找
                return a.getName().compareToIgnoreCase(b.getName());
            } else if (a.getLastModified() != b.getLastModified()) {
                return Long.compare(b.getLastModified(), a.getLastModified());
            }
            return b.getName().compareToIgnoreCase(a.getName());
        }
    };

    /** yyyy-MM-dd / yyyy_MM_dd / yyyy年M月d日 / yyyyMMdd */
    private static final Pattern DATE_WITH_YEAR =
            Pattern.compile("(\\d{4})[-_年./]?([0-9]{1,2})[-_月./]?([0-9]{1,2})日?");
    /** MM-dd / M月d日（无年份，按当前年份补全） */
    private static final Pattern DATE_WITHOUT_YEAR =
            Pattern.compile("([0-9]{1,2})[-_月./]([0-9]{1,2})日?");

    /**
     * 计算相对房间根目录的路径，统一用 '/' 分隔；跨盘等无法相对化时退回文件名
     */
    private static String relativePath(Path baseDir, Path path) {
        try {
            if (baseDir != null) {
                String rel = baseDir.toAbsolutePath().normalize()
                        .relativize(path.toAbsolutePath().normalize())
                        .toString();
                return rel.replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return path.getFileName().toString();
    }

    /**
     * 从目录名里解析日期，解析不出返回 null
     */
    private static LocalDate parseFolderDate(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Matcher m = DATE_WITH_YEAR.matcher(name);
        while (m.find()) {
            LocalDate date = safeDate(toInt(m.group(1)), toInt(m.group(2)), toInt(m.group(3)));
            if (date != null) {
                return date;
            }
        }
        m = DATE_WITHOUT_YEAR.matcher(name);
        while (m.find()) {
            LocalDate date = safeDate(LocalDate.now().getYear(), toInt(m.group(1)), toInt(m.group(2)));
            // 只有月份看起来合法才认，避免把「3-2.flv」这类名字当成日期
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @param baseDir 房间录制根目录：返回给前端的 path 相对它计算，前端再原样传回来定位
     * @param dir     当前要列出的目录
     */
    public static FilePageResult getFilePage(Path baseDir, Path dir, int pageNum, int pageSize, Predicate<FileResult> filePredicate) throws IOException {
        pageNum = Math.max(pageNum, 1);
        pageSize = Math.max(pageSize, 1);
        long skip = (long) (pageNum - 1) * pageSize;
        // 前置校验：目录不存在 / 不是目录直接返回空分页
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            FilePageResult res = new FilePageResult();
            res.files = new ArrayList<>();
            res.total = 0;
            res.pageNum = pageNum;
            res.pageSize = pageSize;
            return res;
        }
        // 兜底：null 代表不过滤所有数据
        Predicate<FileResult> predicate = (filePredicate != null) ? filePredicate : r -> true;
        // 一次遍历，缓存所有符合条件数据，保证分页顺序完全一致
        List<FileResult> matchedList;
        try (Stream<Path> stream = Files.list(dir)) {
            matchedList = stream
                    .map(p -> buildFileResultSafe(baseDir, p))
                    .filter(Objects::nonNull)
                    .filter(predicate)
                    .collect(Collectors.toList());
        }

        // 排序必须在分页之前：目录按名称里的日期倒序（最新日期在前），文件按最后修改时间倒序
        matchedList.sort(FILE_PAGE_ORDER);


        long total = matchedList.size();
        List<FileResult> pageList = matchedList.stream()
                .skip(skip)
                .limit(pageSize)
                .collect(Collectors.toList());

        FilePageResult res = new FilePageResult();
        res.files = pageList;
        res.total = total;
        res.pageNum = pageNum;
        res.pageSize = pageSize;
        return res;
    }

    /**
     * 抽取复用：安全构造FileResult，异常返回null
     *
     * @param baseDir 房间录制根目录，用于计算前端回传的相对 path
     */
    private static FileResult buildFileResultSafe(Path baseDir, Path path) {
        try {
            FileResult result = new FileResult();
            // NIO原生获取文件名，不用转File
            String fileName = path.getFileName().toString();
            result.setName(fileName);

            // path 必须相对房间根目录：前端拿它做子目录/文件请求，服务端再 Paths.get(base, path)
            // 之前按第一个 ']' 截断，名字本身带 ']'（如 [富贵在海边]）时会越界把整条记录丢掉
            result.setPath(relativePath(baseDir, path));

            boolean isDir = Files.isDirectory(path);
            result.setType(isDir ? "folder" : "file");
            long size = 0;
            if (!isDir) {
                try {
                    size = Files.size(path);
                } catch (IOException ignored) {
                }
                result.setSize(size);
            }
            // 最后修改时间（毫秒），前端用于显示文件/目录的更新时间
            try {
                result.setLastModified(Files.getLastModifiedTime(path).toMillis());
            } catch (Exception ignored) {
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // 分页返回实体
    @Data
    public static class FilePageResult {
        private List<FileResult> files;
        private long total;
        private int pageNum;
        private int pageSize;
    }

    @Data
    public static class FileResult {
        private String name;
        private String path;
        private String type;
        private long size;
        private long lastModified;
    }
}
