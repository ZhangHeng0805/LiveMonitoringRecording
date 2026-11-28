package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.common.setting.ConfigKit;
import cn.zhangheng.douyin.browser.DouYinUrlParse;
import cn.zhangheng.record.activation.TrialState;
import cn.zhangheng.record.bean.Setting;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RateLimit;
import cn.zhangheng.record.httpServer.router.RequestContext;
import cn.zhangheng.record.httpServer.util.JWTUtil;
import cn.zhangheng.record.service.MonitorMain;
import cn.zhangheng.record.service.RoomMonitor;
import cn.zhangheng.record.bean.Room;
import cn.zhangheng.record.bean.enums.MonitorStatus;
import cn.zhangheng.record.record.Recorder;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.bean.RoomFileModel;
import cn.zhangheng.lmr.bean.RoomJson;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import cn.zhangheng.lmr.http_server.util.RateLimitResolvers;
import cn.zhangheng.lmr.service.CpolarToolStarter;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.zhangheng.record.httpServer.util.HandlerUtils.parseRequestBodyStr;
import static cn.zhangheng.lmr.http_server.util.CheckUtils.*;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/27 星期六 21:22
 * @version: 1.0
 * @description:
 */
public class ActionHandler extends AnnotationHandler {

    private static final Logger log = LoggerFactory.getLogger(ActionHandler.class);

    /** 抖音直播间地址，捕获其中的数字房间号 */
    private static final Pattern DOUYIN_LIVE_URL_PATTERN = Pattern.compile("live\\.douyin\\.com/(\\d{5,20})");
    /** 纯数字房间号 */
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^\\d{5,20}$");

    /**
     * 房间级锁：同一个房间的启停/录制/刷新/设置串行，不同房间之间互不阻塞。
     * 锁数量与房间数一致，不做回收（回收会让并发线程拿到不同锁）。
     */
    private static final ConcurrentHashMap<String, Object> KEY_LOCKS = new ConcurrentHashMap<>();

    private static Object lockOf(String key) {
        return KEY_LOCKS.computeIfAbsent(key == null ? "" : key, k -> new Object());
    }

    public ActionHandler(String prefix) {
        // 鉴权/限流规则交给项目自己的实现，路由框架不关心具体怎么验、怎么读配置
        super(prefix, AuthResolvers.defaults(), RateLimitResolvers.defaults());
    }

    // ======================= 接口定义（路径/鉴权/限流由注解声明） =======================

    /**
     * 换秘钥令牌：用操作秘钥校验，接口本身必须公开，否则会死循环
     */
    @Endpoint(value = "/auth", auth = AuthPolicy.PUBLIC)
    public void auth(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkActionKey(query, msg)) {
            long expireSec = ConfigKit.get(Setting.class).getApiExpireSec();
            String token = JWTUtil.generateApiToken(expireSec);
            msg.setMessage("秘钥令牌签发成功！");
            JSONObject data = new JSONObject();
            data.set("token", token);
            data.set("type", JWTUtil.API_TOKEN_TYPE);
            data.set("expireSec", expireSec);
            data.set("expireAt", System.currentTimeMillis() + expireSec * 1000);
            msg.setData(data);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 换普通令牌：公开接口，不需要秘钥，任何人可获取；只能访问普通（只读）接口，
     * 有效期短（server.auth.publicTokenExpireSec），按来源 IP 限流避免被刷
     */
    @Endpoint(value = "/token", auth = AuthPolicy.PUBLIC)
    @RateLimit(configKey = "server.api.publicTokenPerMinute", limit = 30)
    public void token(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Setting setting = ConfigKit.get(Setting.class);
        if (!setting.isPublicTokenEnabled()) {
            msg.setCode(1);
            msg.setMessage("普通令牌签发已关闭，请先通过操作秘钥换取秘钥令牌");
            return;
        }
        long expireSec = setting.getPublicTokenExpireSec();
        String token = JWTUtil.generatePublicToken(expireSec);
        msg.setMessage("普通接口令牌签发成功！");
        JSONObject data = new JSONObject();
        data.set("token", token);
        data.set("type", JWTUtil.PUBLIC_TOKEN_TYPE);
        data.set("expireSec", expireSec);
        data.set("expireAt", System.currentTimeMillis() + expireSec * 1000);
        msg.setData(data);
    }

    /**
     * 开始/停止监听：私密接口，必须有秘钥令牌
     */
    @Endpoint(value = "/monitor", auth = AuthPolicy.PRIVATE)
    public void monitor(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            actionMonitor(msg, query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 开始/停止录制：私密接口
     */
    @Endpoint(value = "/record", auth = AuthPolicy.PRIVATE)
    public void record(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            actionRecord(msg, query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 开始/停止弹幕录制：私密接口，仅抖音支持
     */
    @Endpoint(value = "/subtitle", auth = AuthPolicy.PRIVATE)
    public void subtitle(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            actionSubtitle(msg, query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 新增监听房间：私密接口
     */
    @Endpoint(value = "/addRoom", auth = AuthPolicy.PRIVATE)
    public void addRoom(RequestContext ctx) throws IOException {
        Message<Object> msg = ctx.message();
        addMonitor(msg, ctx.exchange());
    }

    /**
     * 删除监听房间：私密接口
     */
    @Endpoint(value = "/delRoom", auth = AuthPolicy.PRIVATE)
    public void delRoom(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            delMonitor(msg, query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 修改房间设置：私密接口
     */
    @Endpoint(value = "/setting", auth = AuthPolicy.PRIVATE)
    public void setting(RequestContext ctx) throws IOException {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            actionSetting(msg, ctx.exchange(), query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 刷新房间状态：只读操作，普通令牌即可（前端也不再前置弹框）
     */
    @Endpoint(value = "/refresh", auth = AuthPolicy.NORMAL)
    public void refresh(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        Map<String, String> query = ctx.query();
        if (checkRoomKey(query, msg)) {
            actionRefresh(msg, query);
        } else {
            msg.setCode(1);
        }
    }

    /**
     * 抖音直播间链接解析：要开浏览器，成本高，普通令牌 + 按来源 IP 限流
     * 阈值读 server.api.parseLinkPerMinute（默认 10，0 为不限）
     */
    @Endpoint(value = "/parseDouYinLink", auth = AuthPolicy.NORMAL)
    @RateLimit(configKey = "server.api.parseLinkPerMinute", limit = 10)
    public void parseDouYinLink(RequestContext ctx) {
        parseDouYinRoomId(ctx.message(), ctx.query().get("shareLink"));
    }

    /**
     * 销毁浏览器实例：私密接口
     */
    @Endpoint(value = "/destroyBrowser", auth = AuthPolicy.PRIVATE)
    public void destroyBrowser(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        PlaywrightBrowserFactory.destroy();
        msg.setMessage("浏览器销毁成功！");
    }

    /**
     * 关闭浏览器实例：私密接口
     */
    @Endpoint(value = "/closeBrowser", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT)
    public void closeBrowser(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        PlaywrightBrowserFactory.closeBrowser();
        msg.setMessage("浏览器关闭成功！");
    }

    /**
     * 重启浏览器（关闭后按当前无头配置立即重新启动）：私密接口
     */
    @Endpoint(value = "/restartBrowser", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT)
    public void restartBrowser(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        PlaywrightBrowserFactory.restart();
        msg.setMessage("浏览器已重启，当前模式：" + (PlaywrightBrowserFactory.isHeadless() ? "无头" : "有头") + "！");
    }

    /**
     * 切换浏览器无头/有头模式并重启（运行中修改不生效，必须重启才会按新配置启动）：私密接口
     * 参数：headless=true/false
     */
    @Endpoint(value = "/setHeadless", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT, methods = {"POST"})
    public void setHeadless(RequestContext ctx) throws IOException {
        Message<Object> msg = ctx.message();
        String raw = ctx.query().get("headless");
        if (StrUtil.isBlank(raw)) {
            JSONObject body = JSONUtil.parseObj(ctx.body());
            raw = body.getStr("headless");
        }
        if (StrUtil.isBlank(raw)) {
            msg.setCode(1);
            msg.setMessage("缺少参数 headless（true/false）");
            return;
        }
        boolean headless = Boolean.parseBoolean(raw.trim());
        PlaywrightBrowserFactory.setHeadless(headless);
        PlaywrightBrowserFactory.restart();
        msg.setMessage("已切换为" + (headless ? "无头" : "有头") + "模式并重启浏览器");
    }

    /**
     * 启动内网穿透（cpolar）：私密接口
     * 后台线程启动，公网地址需要几秒后由 cpolar 输出解析得到
     */
    @Endpoint(value = "/cpolarStart", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT, methods = {"POST"})
    public void cpolarStart(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        CpolarToolStarter starter = CpolarToolStarter.getInstance();
        if (starter == null) {
            msg.setCode(1);
            msg.setMessage("内网穿透组件尚未初始化");
            return;
        }
        if (!Files.exists(Paths.get("cpolar.setting"))) {
            msg.setCode(1);
            msg.setMessage("未配置内网穿透（缺少 cpolar.setting），无法启动");
            return;
        }
        try {
            starter.start();
            msg.setMessage("已下发启动指令，公网映射地址建立后会显示在组件面板");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 停止内网穿透（cpolar）：私密接口
     */
    @Endpoint(value = "/cpolarStop", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT, methods = {"POST"})
    public void cpolarStop(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        CpolarToolStarter starter = CpolarToolStarter.getInstance();
        if (starter == null || !starter.getIsRunning()) {
            msg.setCode(1);
            msg.setMessage("内网穿透当前未运行");
            return;
        }
        try {
            starter.stop();
            msg.setMessage("内网穿透已停止");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 手动把当前公网地址同步到 Git 配置：私密接口（后台队列执行）
     */
    @Endpoint(value = "/cpolarUpdateGit", auth = AuthPolicy.PRIVATE, match = Endpoint.Match.EXACT, methods = {"POST"})
    public void cpolarUpdateGit(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        CpolarToolStarter starter = CpolarToolStarter.getInstance();
        if (starter == null) {
            msg.setCode(1);
            msg.setMessage("内网穿透组件尚未初始化");
            return;
        }
        if (!starter.getIsRunning() || StrUtil.isBlank(starter.getPublicUrl())) {
            msg.setCode(1);
            msg.setMessage("内网穿透未运行或还没有拿到公网地址，无法同步 Git");
            return;
        }
        try {
            starter.updateGitURLFile();
            msg.setMessage("已提交 Git 同步任务，结果会显示在组件面板");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(ThrowableUtil.getAllCauseMessage(e));
        }
    }

    /**
     * 退出程序：私密接口，仅凭秘钥令牌即可，不再额外要求 actionKey 参数
     */
    @Endpoint(value = "/exit", auth = AuthPolicy.PRIVATE)
    public void exit(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        msg.setMessage("退出指令已接收，程序将在数秒内停止！");
        log.info("收到来自{}的退出指令，准备终止程序", ctx.exchange().getRemoteAddress());
        FileModeMain.shutdown("http:" + ctx.exchange().getRemoteAddress());
    }

    private void delMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        synchronized (lockOf(key)) {
            try {
                FileModeMain.delMain(key);
                msg.setMessage("直播监听删除成功");
            } catch (Exception e) {
                msg.setCode(1);
                msg.setMessage(e.getMessage());
            }
        }
    }

    private void addMonitor(Message msg, HttpExchange httpExchange) throws IOException {
        String bodyStr = parseRequestBodyStr(httpExchange, charset);
        try {
            RoomJson bean = JSONUtil.toBean(bodyStr, RoomJson.class);
            // 用与 FileModeMain 相同的房间标识加锁，保证同一房间的新增请求串行（避免并发重复新增）
            String lockKey = (bean.getPlatform() == null ? "?" : bean.getPlatform().name()) + "-" + bean.getId();
            synchronized (lockOf(lockKey)) {
                FileModeMain.addMain(bean);
            }
            msg.setMessage("直播监听新增成功");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(e.getMessage());
        }
    }

    private void actionMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        synchronized (lockOf(key)) {
            RoomFileModel model = FileModeMain.getModelById(key);
            if (model == null) {
                msg.setCode(1);
                msg.setMessage("标识" + key + "不存在！");
                return;
            }
            Main main = model.getMain();
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            if (flag == monitorMain.getIsRunning()) {
                msg.setCode(1);
                msg.setMessage(StrUtil.format("监听状态已{},请勿重复操作！", flag ? "开启" : "关闭"));
                return;
            }
            if (flag) {
                try {
                    FileModeMain.restartMain(key);
                } catch (RuntimeException e) {
                    msg.setCode(1);
                    msg.setMessage(e.getMessage());
                    return;
                }
                msg.setMessage("监听启动成功！");
            } else {
                FileModeMain.stopMonitor(model);
                msg.setMessage("标识[" + key + "]的直播监听已关闭！");
            }
        }
    }

    private void actionRecord(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        synchronized (lockOf(key)) {
            RoomFileModel model = FileModeMain.getModelById(key);
            if (model == null) {
                msg.setCode(1);
                msg.setMessage("标识" + key + "不存在！");
                return;
            }
            Main main = model.getMain();

            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            Recorder recorder = monitorMain.getRecorder();
            boolean isRecord = recorder != null && recorder.isRunning();
            if (flag == isRecord) {
                msg.setCode(1);
                msg.setMessage(StrUtil.format("录制状态已{},请勿重复操作！", flag ? "开启" : "停止"));
                return;
            }
            boolean res;
            if (flag) {
                res = monitorMain.startRecord();
                if (!res && TrialState.isTrialExpired()) {
                    msg.setCode(1);
                    msg.setMessage("试用期已结束，请获取激活码后再开启录制！");
                    return;
                }
                if (res) {
                    Recorder now = monitorMain.getRecorder();
                    boolean running = now != null && now.isRunning();
                    // 录制器创建是异步的，已提交但还没 run() 时不要报“成功”
                    msg.setMessage(running ? "开启录制成功！" : "录制正在启动，稍候生效！");
                    return;
                }
            } else {
                res = monitorMain.stopRecord();
            }
            msg.setMessage(StrUtil.format("{}录制{}！", flag ? "开启" : "停止", res ? "成功" : "失败"));
        }
    }

    /**
     * 弹幕录制启停：仅抖音支持；开关状态同时写回房间文件，
     * 避免手动关闭后下一次开播又被自动打开。
     */
    private void actionSubtitle(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        synchronized (lockOf(key)) {
            RoomFileModel model = FileModeMain.getModelById(key);
            if (model == null) {
                msg.setCode(1);
                msg.setMessage("标识" + key + "不存在！");
                return;
            }
            Main main = model.getMain();
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            Room room = monitorMain.getRoom();
            if (room.getPlatform() == null || !"DouYin".equals(room.getPlatform().name())) {
                msg.setCode(1);
                msg.setMessage("当前平台暂不支持弹幕录制！");
                return;
            }
            RoomMonitor<?, ?> roomMonitor = monitorMain.getRoomMonitor();
            boolean open = roomMonitor.getIsOpenSubtitle();
            if (flag == open) {
                msg.setCode(1);
                msg.setMessage(StrUtil.format("弹幕录制已{},请勿重复操作！", flag ? "开启" : "停止"));
                return;
            }
            if (flag && !room.isLiving()) {
                msg.setCode(1);
                msg.setMessage("直播间未开播，无法开启弹幕录制！");
                return;
            }
            // 和录制一样只操作运行状态：不改房间设置、不写房间文件
            if (flag) {
                roomMonitor.forceStartSubtitle();
            } else {
                roomMonitor.stopSubtitle();
            }
            boolean now = roomMonitor.getIsOpenSubtitle();
            if (flag && !now) {
                msg.setMessage("弹幕录制正在开启，弹幕连接建立后生效！");
            } else {
                msg.setMessage(StrUtil.format("弹幕录制{}！", now ? "已开启" : "已停止"));
            }
        }
    }

    private void actionRefresh(Message msg, Map<String, String> query) {
        String key = query.get("key");
        synchronized (lockOf(key)) {
            RoomFileModel model = FileModeMain.getModelById(key);
            if (model == null) {
                msg.setCode(1);
                msg.setMessage("标识" + key + "不存在！");
                return;
            }
            Main main = model.getMain();
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            if (monitorMain.getStatus() != MonitorStatus.RUNNING) {
                msg.setCode(1);
                msg.setMessage("该直播间没有启动监听!");
                return;
            }
            try {
                monitorMain.getRoomMonitor().nowRefresh();
                msg.setMessage("刷新成功!");
            } catch (Exception e) {
                msg.setCode(1);
                msg.setMessage("刷新失败!");
            }
        }
    }

    private void actionSetting(Message msg, HttpExchange httpExchange, Map<String, String> query) {
        String key = query.get("key");
        synchronized (lockOf(key)) {
            RoomFileModel model = FileModeMain.getModelById(key);
            if (model == null) {
                msg.setCode(1);
                msg.setMessage("标识" + key + "不存在！");
                return;
            }
            Main main = model.getMain();
            try {
                MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
                String bodyStr = parseRequestBodyStr(httpExchange, charset);
                RoomJson bean = JSONUtil.toBean(bodyStr, RoomJson.class);
                Room room = monitorMain.getRoom();
                Setting setting = room.getSetting();
                bean.convert(setting);
                monitorMain.setAutoRecord(bean.isAutoRecord());
                // 写回监听文件，重启后设置不丢（录制画质也在其中）
                FileModeMain.updateRoomSetting(key, bean);
                room.initSetting(setting);
                msg.setMessage("设置成功!");
            } catch (Exception e) {
                msg.setCode(1);
                msg.setMessage("设置错误! " + ThrowableUtil.getAllCauseMessage(e));
            }
        }
    }

    /**
     * 解析抖音分享链接为直播间号，并对结果做校验：
     * 1. 输入为空 → 提示缺少链接；
     * 2. 输入本身就是数字房间号 → 直接返回，不启动浏览器；
     * 3. 解析得到的地址必须是 live.douyin.com/{数字}，否则视为解析失败（不会把网页地址当房间号回填）。
     */
    private void parseDouYinRoomId(Message msg, String shareLink) {
        String raw = shareLink == null ? "" : shareLink.trim();
        if (StrUtil.isBlank(raw)) {
            msg.setCode(1);
            msg.setMessage("缺少分享链接，请粘贴抖音直播间分享文案或链接");
            return;
        }
        if (ROOM_ID_PATTERN.matcher(raw).matches()) {
            msg.setData(raw);
            msg.setMessage("输入已是直播间号，无需解析");
            return;
        }
        long start = System.currentTimeMillis();
        String url;
        try {
            url = DouYinUrlParse.parseDouyinShareLink(raw);
        } catch (Throwable e) {
            log.warn("抖音链接解析异常", e);
            msg.setCode(1);
            msg.setMessage("解析失败，请检查分享链接是否有效（" + ThrowableUtil.getAllCauseMessage(e) + "）");
            return;
        }
        String result = url == null ? "" : url.trim();
        log.info("抖音链接解析 {} -> {}（耗时 {} ms）",
                raw.length() > 80 ? raw.substring(0, 80) + "..." : raw, result,
                System.currentTimeMillis() - start);
        if (StrUtil.isBlank(result)) {
            msg.setCode(1);
            msg.setMessage("解析失败：未获取到直播间地址，分享链接可能已失效");
            return;
        }
        Matcher liveMatcher = DOUYIN_LIVE_URL_PATTERN.matcher(result);
        if (liveMatcher.find()) {
            msg.setData(liveMatcher.group(1));
            msg.setMessage("解析成功");
            return;
        }
        if (ROOM_ID_PATTERN.matcher(result).matches()) {
            msg.setData(result);
            msg.setMessage("解析成功");
            return;
        }
        msg.setCode(1);
        msg.setMessage("解析结果不是直播间地址（返回：" + briefUrl(result) + "），请确认是抖音直播间分享链接");
    }

    private static String briefUrl(String url) {
        String text = url.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

}