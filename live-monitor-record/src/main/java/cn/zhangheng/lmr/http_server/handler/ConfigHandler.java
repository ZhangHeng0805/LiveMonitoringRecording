package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import cn.zhangheng.record.bean.Constant;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RequestContext;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 系统配置文件（xxxr.setting）读写：私密接口，需操作秘钥。
 * 只允许读写工作目录下的固定文件名，避免任意文件写入。
 */
@Slf4j
public class ConfigHandler extends AnnotationHandler {

    public ConfigHandler(String prefix) {
        super(prefix, AuthResolvers.defaults());
    }

    /**
     * 读取配置文件原文（保留注释与空行，便于直接编辑）
     */
    @Endpoint(value = "/raw", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT)
    public void raw(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        File file = configFile();
        if (!file.isFile()) {
            msg.setCode(1);
            msg.setMessage("配置文件不存在：" + file.getAbsolutePath());
            return;
        }
        try {
            byte[] bytes = FileUtil.readBytes(file);
            Charset sourceCharset = detectCharset(bytes);
            String content = new String(bytes, sourceCharset);
            if (content.startsWith("\uFEFF")) content = content.substring(1);
            // 兼容被 Properties 工具转义过的旧文件：把 Unicode 转义（uXXXX）还原成中文，避免面板里整片注释不可读
            content = decodeUnicodeEscapes(content);
            Map<String, Object> data = new HashMap<>();
            data.put("path", file.getAbsolutePath());
            data.put("content", content);
            data.put("charset", sourceCharset.name());
            data.put("lastModified", file.lastModified());
            msg.setData(data);
            msg.setMessage(Constant.Setting_Name + " 读取成功");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("读取配置文件失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 保存配置文件：
     * 1. 先校验能否被 Properties 正常解析，并保留关键键，避免写坏后程序起不来；
     * 2. 原文件备份为 xxxr.setting.bak；
     * 3. 写回后立即 ConfigKit.refresh 重新加载配置（端口/运行模式等需重启生效）。
     */
    @Endpoint(value = "/save", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT, methods = {"POST"})
    public void save(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        try {
            JSONObject body = JSONUtil.parseObj(ctx.body());
            String content = body.getStr("content");
            if (StrUtil.isBlank(content)) {
                msg.setCode(1);
                msg.setMessage("配置内容不能为空");
                return;
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (Exception e) {
                msg.setCode(1);
                msg.setMessage("配置文件格式错误，未保存：" + ThrowableUtil.getAllCauseMessage(e));
                return;
            }
            // 关键键必须保留，避免误删后程序无法启动
            String[] required = {"server.runMode", "server.monitor.port"};
            for (String key : required) {
                if (StrUtil.isBlank(properties.getProperty(key))) {
                    msg.setCode(1);
                    msg.setMessage("缺少关键配置 " + key + "，未保存");
                    return;
                }
            }

            File file = configFile();
            Path backup = file.toPath().resolveSibling(file.getName() + ".bak");
            boolean hasBackup = false;
            if (file.isFile()) {
                Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
                hasBackup = true;
            }
            FileUtil.writeString(content, file, StandardCharsets.UTF_8);
            try {
                // 真正按配置类字段解析一遍：格式能 load 不代表字段值合法（端口范围、枚举等）
                ConfigKit.refresh(Setting.class);
            } catch (Exception e) {
                if (hasBackup) {
                    Files.copy(backup, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    try {
                        ConfigKit.refresh(Setting.class);
                    } catch (Exception ignored) {
                        log.error("配置回滚后重新加载仍失败：{}", ThrowableUtil.getAllCauseMessage(ignored));
                    }
                }
                msg.setCode(1);
                msg.setMessage("配置校验失败，已回滚到修改前的文件：" + ThrowableUtil.getAllCauseMessage(e));
                log.warn("{} 校验失败，已回滚：{}", Constant.Setting_Name, ThrowableUtil.getAllCauseMessage(e));
                return;
            }
            msg.setMessage("已保存并重新加载 " + Constant.Setting_Name + "（统一写入 UTF-8）；端口、运行模式等改动需重启程序生效");
            log.info("{} 已更新（{} 字节）", Constant.Setting_Name, content.length());
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("保存配置文件失败：" + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    private static File configFile() {
        return new File(Constant.Setting_Name);
    }

    /**
     * 识别配置文件原始编码：UTF-8（含 BOM）优先，不是合法 UTF-8 时按 GB18030（兼容 GBK）读取。
     * 保存时统一写成 UTF-8，避免把 GBK 文件直接当 UTF-8 读成乱码后再存坏。
     */
    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return Charset.forName("GB18030");
        }
    }

    /**
     * 还原注释/配置值里的 Unicode 转义序列（uXXXX，Properties 工具或 native2ascii 处理过的文件会变成这样）。
     * 连续两个反斜杠（如 Windows 路径中的转义）不处理，避免误伤。
     */
    static String decodeUnicodeEscapes(String s) {
        if (s == null || s.indexOf("\\u") < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u'
                    && (i == 0 || s.charAt(i - 1) != '\\')
                    && isHex4(s, i + 2)) {
                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 5;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isHex4(String s, int from) {
        for (int i = from; i < from + 4; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
