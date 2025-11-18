package cn.zhangheng.douyin;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/11/11 星期二 22:57
 * @version: 1.0
 * @description: 将直播监听数据log文件数据封装成Excel
 */
public class MonitorDataTableGenerate {

    private static final String regex = "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) : 在线人数：(.+)，点赞数：(\\d+)，总观看人数：(.+)$";
    private static final Pattern pattern = Pattern.compile(regex);

    public static void main(String[] args) throws IOException {
        System.out.println("=====直播监听数据log文件数据封装成Excel=====");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("请输入监听log文件保存目录路径(输入0退出): ");
            String p = scanner.nextLine();
            if (!"0".equals(p)) {
                try {
                    Path path = Paths.get(p);
                    File file = path.toFile();
                    if (file.exists() && file.isDirectory()) {
                        String name = file.getName();
                        if (name.startsWith("[") && name.endsWith("]")) {
                            findRoomDir(path);
                        } else if (name.startsWith("2")) {
                            findMonitorFile(path);
                        } else {
                            throw new RuntimeException(p + " 不是一个监听文件保存目录路径");
                        }
                    } else {
                        throw new RuntimeException(p + " 不是一个已存在的目录路径");
                    }
                } catch (Exception e) {
                    System.err.println("程序发生异常："+ ThrowableUtil.toString(e));
//                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        System.out.println("程序退出！");

//        Path path1 = Paths.get("D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]");
//        findRoomDir(path1);
//
//        Path path2 = Paths.get("D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[小兰花]\\2025-11-17");
//        Path path2 = Paths.get("D:\\直播录屏\\live-monitor-record\\【星曦向荣】直播监听工具\\抖音\\[兰小美]\\2025-11-18");
//        Path path2 = Paths.get("【星曦向荣】直播监听工具/抖音/[超级喜欢uu子]/2025-09-24");
//        findMonitorFile(path2);

    }

    private static List<Path> findRoomDir(Path roomDir) {
        List<Path> datas = new ArrayList<>();
        File file = roomDir.toFile();
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        List<Path> monitorFile = findMonitorFile(f.toPath());
                        datas.addAll(monitorFile);
                    }
                }
            }
        }
        return datas;
    }


    private static List<Path> findMonitorFile(Path dateDir) {
        List<Path> paths = new ArrayList<>();
        File file = dateDir.toFile();
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                String n = dateDir.getParent().toFile().getName();
                n = n.startsWith("[") && n.endsWith("]") ? n : "";
                String xlsxName = Paths.get(dateDir.toString(), dateDir.toFile().getName() + n + "直播监听数据.xlsx").toString();
                try (ExcelWriter excelWriter = EasyExcel.write(xlsxName).build()) {
                    int index = 0;
                    for (File f : files) {
                        String name = f.getName();
                        if (f.isFile() && name.endsWith("监听.log")) {
                            try {
                                paths.add(f.toPath());
                                if (monitorFileToXlsx(f.toPath(), excelWriter, index)) {
                                    index++;
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } finally {
                    System.out.println("封装Excel完成! " + xlsxName);
                }
            }
        }
        return paths;
    }

    private static boolean monitorFileToXlsx(Path file, ExcelWriter excelWriter, int index) throws IOException {
        List<String> strings = Files.readAllLines(file);
        List<Data> datas = new ArrayList<>();
        String temp = null;
        for (String string : strings) {
            if (string.startsWith("2") && string.contains("在线人数")) {
                Matcher matcher = pattern.matcher(string);
                if (matcher.matches()) {
                    Data data = new Data();
                    data.setTime(TimeUtil.parse(matcher.group(1)).toJdkDate());
                    data.setUserCountStr(matcher.group(2).trim());
                    data.setLikeCount(Integer.parseInt(matcher.group(3)));
                    data.setTotalUserStr(matcher.group(4));
                    datas.add(data);
                } else {
                    System.out.println("格式不匹配，无法提取数据: " + string);
                }
            } else if (string.startsWith("【") && temp == null) {
                temp = string.trim().substring(0, string.lastIndexOf(" - "));
            }
        }
        if (!datas.isEmpty()) {
            String name = file.toFile().getName();
            String sheetName = temp + name.substring(0, name.lastIndexOf("."));
            WriteSheet sheet = EasyExcel.writerSheet(index, sheetName)
                    .registerWriteHandler(getStyleStrategy())
                    .registerWriteHandler(new SimpleColumnWidthStyleStrategy(20)) // 列宽设为20
                    .head(Data.class)
                    .build();
            excelWriter.write(datas, sheet);
            System.out.println(sheetName + " Sheet封装Excel完成！");
            return true;
        }
        return false;
    }

    private static HorizontalCellStyleStrategy getStyleStrategy() {
        // 1. 定义表头样式（居中）
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER); // 水平居中
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);   // 垂直居中

        // 2. 定义内容样式（居中）
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 3. 创建样式策略（表头和内容均居中）
        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    @lombok.Data
    static class Data {
        //时间
        @ExcelProperty("时间")
        @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
        private Date time;
        //当前在线人数
        @ExcelProperty("在线人数")
        private String userCountStr;
        //喜欢点赞数
        @ExcelProperty("点赞数")
        private int likeCount;
        //总观看人数
        @ExcelProperty("总观看人数")
        private String totalUserStr;
    }
}
