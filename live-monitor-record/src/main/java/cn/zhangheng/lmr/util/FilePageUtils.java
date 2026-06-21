package cn.zhangheng.lmr.util;

import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
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
    public static FilePageResult getFilePage(Path dir, int pageNum, int pageSize, Predicate<FileResult> filePredicate) throws IOException {
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
                    .map(FilePageUtils::buildFileResultSafe)
                    .filter(Objects::nonNull)
                    .filter(predicate)
                    .collect(Collectors.toList());
        }


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
     */
    private static FileResult buildFileResultSafe(Path path) {
        try {
            FileResult result = new FileResult();
            // NIO原生获取文件名，不用转File
            String fileName = path.getFileName().toString();
            result.setName(fileName);

            String filePath = path.toAbsolutePath().toString().replace('\\', '/');
            int idx = filePath.indexOf(']');
            String realPath = idx > -1 ? filePath.substring(idx + 2) : filePath;
            result.setPath(realPath);

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
    }
}
