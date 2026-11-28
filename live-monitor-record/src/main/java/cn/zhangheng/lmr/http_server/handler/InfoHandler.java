package cn.zhangheng.lmr.http_server.handler;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.browser.factory.PlaywrightBrowserFactory;
import cn.zhangheng.common.cache.CacheWithOnceLoadHelper;
import cn.zhangheng.lmr.http_server.util.CheckUtils;
import cn.zhangheng.record.httpServer.router.AnnotationHandler;
import cn.zhangheng.record.httpServer.router.AuthPolicy;
import cn.zhangheng.record.httpServer.router.Endpoint;
import cn.zhangheng.record.httpServer.router.RequestContext;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.http_server.util.AuthResolvers;
import cn.zhangheng.lmr.service.CpolarToolStarter;
import com.zhangheng.bean.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2026/07/06 星期一 14:06
 * @version: 1.0
 * @description:
 */
public class InfoHandler extends AnnotationHandler {
    private final CacheWithOnceLoadHelper<String, Map<String, Object>> queryHelper;

    public InfoHandler(String prefix) {
        super(prefix, AuthResolvers.defaults());
        queryHelper = CacheWithOnceLoadHelper.<String, Map<String, Object>>builder(key -> {
                    Map<String, Object> allCounter = FileModeMain.getCounter();
                    Map<String, Object> data = new HashMap<>();
                    data.put("AllCounter", allCounter);
                    return data;
                })
                .cacheExpireMs(3000)
                .maxRetry(2)
                .cacheMaxSize(1)
                .pool(1, 2, 10)
                .holderMapMaxSize(10)
                .build();
    }

    /**
     * 线程池状态：只读监控信息，公开访问
     */
    @Endpoint(value = "/getThread", auth = AuthPolicy.PUBLIC)
    public void getThread(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        ThreadPoolExecutor threadPool = FileModeMain.getThreadPool();
        int corePoolSize = threadPool.getCorePoolSize();
        int activeCount = threadPool.getActiveCount();
        Map<String, Integer> res = new HashMap<>();
        res.put("corePoolSize", corePoolSize);
        res.put("activeCount", activeCount);
        msg.setData(res);
        msg.setMessage(StrUtil.format("核心线程数: {}， 正在工作的线程数: {},", corePoolSize, activeCount));
    }

    /**
     * 各类计数器汇总（3 秒缓存）
     */
    @Endpoint(value = "/getCount", auth = AuthPolicy.PUBLIC)
    public void getCount(RequestContext ctx) throws Exception {
        ctx.message().setData(queryHelper.get("getCount", 2000));
    }

    /**
     * 面板「浏览器组件」：Playwright/Chromium 运行状态、上下文列表与组件信息
     * 只读接口，普通令牌即可；组件信息按鉴权等级脱敏：
     * 只有本请求携带秘钥令牌（即已通过操作秘钥验证）才返回完整信息，否则版本/路径打码。
     */
    @Endpoint(value = "/browser", match = Endpoint.Match.EXACT, auth = AuthPolicy.NORMAL)
    public void browser(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        // 双保险：鉴权等级和请求自带的秘钥令牌必须同时成立才返回完整信息，缺一即脱敏
        msg.setData(PlaywrightBrowserFactory.statusSnapshot(needDesensitize(ctx)));
        msg.setMessage("浏览器组件状态获取成功");
    }

    /**
     * 面板「内网穿透组件」：cpolar 是否配置/运行、公网映射地址、Git 同步状态
     * 只读接口，普通令牌即可；本地路径类信息按鉴权等级脱敏（需本请求携带秘钥令牌才返回真实值）
     */
    @Endpoint(value = "/cpolar", match = Endpoint.Match.EXACT, auth = AuthPolicy.NORMAL)
    public void cpolar(RequestContext ctx) {
        Message<Object> msg = ctx.message();
        msg.setData(CpolarToolStarter.statusSnapshot(needDesensitize(ctx)));
        msg.setMessage("内网穿透组件状态获取成功");
    }

    /**
     * 是否需要脱敏：鉴权等级未到秘钥级，或请求里根本没有携带秘钥令牌，都按脱敏处理。
     * 只看等级标记不够——若标记因路由/热更新异常缺失或被误置，会直接泄露本地路径。
     */
    private static boolean needDesensitize(RequestContext ctx) {
        return !CheckUtils.isSecretAuthenticated(ctx.exchange())
                || !CheckUtils.hasSecretToken(ctx.exchange());
    }

}
