/**
 * 访问来源采集（client-info）
 * ---------------------------------------------------------------------------
 * 职责只有三件：
 *   1. 采集当前访问者的浏览器 / 设备 / 来源信息（collectClientInfo）；
 *   2. 用 sendBeacon 异步上报到 {MainUrl}client-info（不阻塞页面卸载）；
 *   3. 顺带向 {MainUrl}client-info/index 续期 _sid / _cid / _token 会话 Cookie。
 *
 * 上报字段名（cid / dt / dl / dr / df / dw / os / osv / bs / bsv / ul / sr / vp / sd
 * / je / app / br / r）与 ClientHandler.toJsonLine 一一对应，改名会直接导致日志丢字段。
 * 对外仍保留 refreshClient / sendClient / client_result / SendData 等旧名字，避免老页面失效。
 */
(function (window, document) {
    'use strict';

    /* =====================================================================
     * 一、UA 常量（只读一次，后续判定全部基于它）
     * ===================================================================== */
    const UA = window.navigator.userAgent || '';
    const UAL = UA.toLowerCase();

    const isMobile = /mobile|android|iphone|ipad|ipod/.test(UAL);
    // PC 判定只用于「设备名兜底」，移动端优先
    const isPc = !isMobile && /windows nt|macintosh|cros|linux x86/.test(UAL);

    /* =====================================================================
     * 二、操作系统：<家族, 版本>（替代原来的 osName + getOS 双套逻辑）
     * ===================================================================== */
    const WINDOWS_VERSION = {
        '10.0': '10', '6.3': '8.1', '6.2': '8', '6.1': '7', '6.0': 'Vista', '5.1': 'XP'
    };

    function parseOS() {
        let m;

        if ((m = UA.match(/Windows NT (\d+\.\d+)/i))) {
            return {family: 'Windows', version: WINDOWS_VERSION[m[1]] || m[1]};
        }
        if ((m = UA.match(/Android\s?([\d.]+)/i))) {
            return {family: 'Android', version: m[1]};
        }
        // iPhone / iPad / iPod：统一归到 iOS，版本取 "OS 17_0"
        if (/iphone|ipad|ipod/i.test(UA)) {
            m = UA.match(/\bOS\s([\d_]+)/i);
            return {family: 'iOS', version: m ? m[1].replace(/_/g, '.') : ''};
        }
        if (/CrOS/i.test(UA)) {
            return {family: 'Chrome OS', version: ''};
        }
        if ((m = UA.match(/Mac OS X (\d+[_.]\d+[_.]?\d*)/i))) {
            return {family: 'MacOS', version: m[1].replace(/_/g, '.')};
        }
        if (/Linux/i.test(UA)) {
            return {family: 'Linux', version: ''};
        }
        if (/web0s|webos/i.test(UA)) {
            return {family: 'WebOS', version: ''};
        }
        return {family: '', version: ''};
    }

    /* =====================================================================
     * 三、浏览器：按「先具体后通用」顺序判定，避免 Chrome 吞掉 Edge/Opera
     * ===================================================================== */
    function parseBrowser() {
        const rules = [
            [/\bEdg(?:e|A|iOS)?\/(\d+)/i, 'Edge'],
            [/OPR\/(\d+)/i, 'Opera'],
            [/Opera Mini/i, 'Opera Mini'],
            [/\bOpera[/ ](\d+)/i, 'Opera'],
            [/Firefox\/(\d+)/i, 'Firefox'],
            [/CriOS\/(\d+)/i, 'Chrome'],
            // HeadlessChrome/xxx 也要认得出来，否则会一路落到 Safari 分支
            [/(?:Headless)?Chrome\/(\d+)/i, 'Chrome'],
            // 真 Safari 一定带 Version/x.x，不能只凭 Safari/537.36（Chrome 也有）
            [/Version\/(\d+)[\d.]*.*Safari/i, 'Safari'],
            [/MSIE\s(\d+)/i, 'IE'],
            [/\bTrident\//i, 'IE']
        ];
        for (const [re, name] of rules) {
            const m = UA.match(re);
            if (m) return {name: name, version: m[1] ? parseInt(m[1], 10) : ''};
        }
        return {name: '', version: ''};
    }

    /* =====================================================================
     * 四、爬虫 / 搜索引擎
     * ===================================================================== */
    function detectSpider() {
        const m = UA.match(
            /baiduspider|bingbot|googlebot|sosospider|360spider|yisouspider|sogou|msnbot|yahoo|bot|robot|spider|crawler|crawling|curl/i
        );
        return m ? m[0] : '';
    }

    // 搜索引擎识别表：host 判定 + 关键词参数正则
    const SEARCH_ENGINES = [
        {name: '百度', host: h => /(^|\.)baidu\./i.test(h), kw: /[?&](?:wd|word)=([^&]+)/},
        {name: '必应', host: h => /(^|\.)bing\./i.test(h), kw: /[?&]q=([^&]+)/},
        {name: '谷歌', host: h => /(^|\.)google\./i.test(h), kw: /[?&]q=([^&]+)/},
        {name: '搜狗', host: h => /(^|\.)sogou\./i.test(h), kw: /[?&](?:query|keyword)=([^&]+)/},
        {name: '360', host: h => /(^|\.)so\.com$/i.test(h), kw: /[?&]q=([^&]+)/}
    ];

    function safeDecode(value) {
        try {
            return decodeURIComponent(value.replace(/\+/g, ' '));
        } catch (e) {
            return value;
        }
    }

    /**
     * 解析来源页。
     * 同站来源直接视为「直接访问」（返回空），外站返回 host + 搜索引擎名 + 搜索词。
     */
    function getReferrer() {
        const ref = document.referrer || '';
        if (!ref) return {rurl: '', se: '', kw: ''};

        let host, pathname;
        try {
            const u = new URL(ref, window.location.href);
            host = u.hostname;
            pathname = u.pathname;
            if (host === window.location.hostname) return {rurl: '', se: '', kw: ''};
        } catch (e) {
            return {rurl: ref, se: '', kw: ''};
        }

        for (const engine of SEARCH_ENGINES) {
            if (!engine.host(host)) continue;
            // 关键词必须从完整来源地址里取，不能用 host（原实现的正则永远匹配不到）
            const m = ref.match(engine.kw);
            return {rurl: host, se: engine.name, kw: m ? safeDecode(m[1]) : ''};
        }

        // 非搜索来源：过长的 URL 只留 host + path，避免日志里出现大段带参地址
        const short = ref.length > 100 ? host + pathname : ref;
        return {rurl: short, se: '', kw: ''};
    }

    /* =====================================================================
     * 五、设备品牌 / 宿主 App
     * ===================================================================== */
    const BRAND_RULES = [
        [/\(.*Android.*HONOR.*\)/i, '荣耀'],
        [/\(.*Android.*(?:HUAWEI|HONOR).*\)/i, '华为'],
        [/\(.*Android.*(?:MI|Mi|Redmi).*\)/, '小米'],
        [/\(.*Android.*OPPO.*\)/, 'OPPO'],
        [/\(.*Android.*vivo.*\)/i, 'vivo'],
        [/\(.*Android.*(?:SAMSUNG|SM-).*\)/, '三星'],
        [/oneplus/i, '一加'],
        [/meizu/i, '魅族'],
        [/sony/i, '索尼']
    ];

    function detectBrand() {
        if (/iphone|ipad|ipod/i.test(UA)) return '苹果';
        for (const [re, name] of BRAND_RULES) {
            if (re.test(UA)) return name;
        }
        return isPc ? 'PC端' : '';
    }

    function detectApp() {
        if (/AlipayClient/i.test(UA)) return 'Alipay';
        if (isMobile && /miniprogram/i.test(UAL) || window.__wxjs_environment === 'miniprogram') return 'WxMini';
        if (/WindowsWechat/i.test(UA)) return 'WinWechat';
        if (/macWechat|wechat.*mac os/i.test(UA)) return 'MacWechat';
        if (/MicroMessenger/.test(UA)) return 'Wechat';
        if (/QQ\//.test(UA)) return 'QQ';
        if (/weibo/i.test(UAL)) return 'Weibo';
        if (/UCBrowser|UC\//i.test(UA)) return 'UC';
        if (/baiduboxapp/i.test(UAL)) return 'Baidu';
        return '';
    }

    /* =====================================================================
     * 六、Cookie / 标识
     * ===================================================================== */
    const CID_COOKIE = '_cid';
    const CID_MAX_AGE_HOURS = 24 * 30; // 必须覆盖服务端会话，否则 _cid 换新会让旧 token 持续 403

    /** 写 Cookie（hours 为小时，默认 2 小时） */
    function setCookie(key, value, hours) {
        const maxAge = Math.round((hours == null ? 2 : hours) * 3600);
        document.cookie =
            encodeURIComponent(key) + '=' + encodeURIComponent(value == null ? '' : value) +
            ';path=/;max-age=' + maxAge + ';SameSite=Lax';
    }

    function getCookie(key) {
        const target = encodeURIComponent(key) + '=';
        for (const item of document.cookie.split('; ')) {
            if (item.indexOf(target) === 0) {
                return decodeURIComponent(item.slice(target.length));
            }
        }
        return null;
    }

    /** 访问者唯一标识：优先复用 Cookie，没有则生成 10 位数字并落 Cookie
     *  额外做一层内存缓存：Cookie 被禁用（隐私模式 / file:// 预览）时，
     *  同一页面内多次取用仍然是同一个 cid，不会一次访问上报出多个新用户。
     */
    let cidCache = null;

    function getCid() {
        if (cidCache) return cidCache;
        const cached = getCookie(CID_COOKIE);
        cidCache = cached || String(Math.floor(Math.random() * 1e10)).padStart(10, '0');
        if (!cached) setCookie(CID_COOKIE, cidCache, CID_MAX_AGE_HOURS);
        return cidCache;
    }

    /* =====================================================================
     * 七、传输工具
     * ===================================================================== */
    /** 对象转 query string（0 / false / 空串都要保留，只有 null/undefined 跳过） */
    function serialize(obj) {
        const parts = [];
        for (const key in obj) {
            if (!Object.prototype.hasOwnProperty.call(obj, key)) continue;
            const value = obj[key];
            if (value == null) continue;
            parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
        }
        return parts.join('&');
    }

    /** GET 上报（beacon 不可用时的兜底），异步不阻塞页面 */
    function sendXhr(url, param) {
        const target = param ? url + '?' + serialize(param) : url;
        try {
            fetch(target, {method: 'GET', credentials: 'include', keepalive: true}).catch(function () {
            });
            return true;
        } catch (e) {
            return false;
        }
    }

    const baseUrl = () => window.MainUrl || '';

    /* =====================================================================
     * 八、采集
     * ===================================================================== */
    function viewportSize() {
        const docEl = document.documentElement;
        const body = document.body;
        let width = 0;
        let height = 0;
        if (docEl && docEl.clientWidth && docEl.clientHeight &&
            (document.compatMode === 'CSS1Compat' || !body || !body.clientWidth)) {
            width = docEl.clientWidth;
            height = docEl.clientHeight;
        } else if (body) {
            width = body.clientWidth;
            height = body.clientHeight;
        }
        return width > 0 && height > 0 ? width + 'x' + height : '';
    }

    /** 采集上报主体字段，字段名不可改（服务端按名取值） */
    function collectClientInfo() {
        const os = parseOS();
        const browser = parseBrowser();
        const ref = getReferrer();
        const nav = window.navigator;
        const screen = window.screen || {};

        return {
            cid: getCid(),                          // Cookie ID
            dt: document.title || '',               // 页面标题
            dl: window.location.href,               // 页面地址
            dr: ref.rurl,                           // 来源页
            df: ref.se + detectSpider(),            // 搜索引擎 + 爬虫标识
            dw: ref.kw,                             // 搜索关键词
            os: os.family,                          // 操作系统
            osv: os.version,                        // 操作系统版本
            bs: browser.name,                       // 浏览器
            bsv: browser.version,                   // 浏览器版本（主版本号）
            ul: String(nav.language || nav.browserLanguage || '').toLowerCase(),
            sr: screen.width + 'x' + screen.height, // 屏幕分辨率
            vp: viewportSize(),                     // 可视区域
            sd: (screen.colorDepth || '') + '-bit', // 色深
            je: nav.javaEnabled ? 1 : 0,
            app: detectApp(),                       // 宿主 App
            br: detectBrand(),                      // 设备品牌
            r: Date.now()                           // 采集时间戳
        };
    }

    // 旧名字保留（外部可能仍在调用）
    const client_result = collectClientInfo;

    /* =====================================================================
     * 九、公网 IP + 归属地
     *   - 本机访问、内网地址：不查外网接口，直接标记 private
     *   - 结果做 10 分钟临时缓存（内存 + sessionStorage），刷新页面不再重复查询
     *   - 3 秒超时，超时不阻断埋点上报
     * ===================================================================== */
    const IP_CACHE_KEY = 'lmr.client.ip';
    const IP_CACHE_TTL = 10 * 60 * 1000; // 10 分钟

    function isLocalHost() {
        const host = window.location.hostname;
        return !host || host === 'localhost' || host === '127.0.0.1' || host === '::1';
    }

    /**
     * 判断是否内网 / 保留地址（IPv4 私有段、CGNAT、链路本地、IPv6 ULA 与回环）。
     * 内网 IP 交给第三方查询也不会返回归属地，没必要发请求。
     */
    function isPrivateIP(ip) {
        if (!ip) return false;
        let v = String(ip).trim().toLowerCase();
        // ::ffff:192.168.1.5 这类 IPv4 映射地址，取后半段再判断
        const mapped = v.match(/^::ffff:([\d.]+)$/);
        if (mapped) v = mapped[1];

        if (/^(\d{1,3}\.){3}\d{1,3}$/.test(v)) {
            const p = v.split('.').map(Number);
            if (p.some(function (n) {
                return n > 255;
            })) return false;
            const [a, b] = p;
            if (a === 10 || a === 127 || a === 0) return true;              // 10/8、回环、0/8
            if (a === 172 && b >= 16 && b <= 31) return true;               // 172.16/12
            if (a === 192 && b === 168) return true;                        // 192.168/16
            if (a === 169 && b === 254) return true;                        // 169.254/16 链路本地
            if (a === 100 && b >= 64 && b <= 127) return true;              // 100.64/10 运营商 NAT
            return false;
        }

        if (v.indexOf(':') !== -1) {
            if (v === '::1' || v === '::') return true;                     // 回环 / 未指定
            if (/^fe[89ab][0-9a-f]?:/.test(v)) return true;                 // fe80::/10 链路本地
            if (/^f[cd][0-9a-f]{0,2}:/.test(v)) return true;                // fc00::/7 唯一本地地址
            return false;
        }
        return false;
    }

    // 内存缓存优先（隐私模式下 sessionStorage 可能不可用）
    let ipCache = null;   // {t, data}
    let ipInflight = null; // 并发调用复用同一个请求

    function readIPCache() {
        if (ipCache && Date.now() - ipCache.t < IP_CACHE_TTL) return ipCache.data;
        try {
            const raw = window.sessionStorage && window.sessionStorage.getItem(IP_CACHE_KEY);
            if (!raw) return null;
            const cache = JSON.parse(raw);
            if (!cache || !cache.data || !cache.t || Date.now() - cache.t >= IP_CACHE_TTL) return null;
            ipCache = cache;
            return cache.data;
        } catch (e) {
            return null;
        }
    }

    function writeIPCache(data) {
        ipCache = {t: Date.now(), data: data};
        try {
            window.sessionStorage &&
            window.sessionStorage.setItem(IP_CACHE_KEY, JSON.stringify({t: ipCache.t, data: data}));
        } catch (e) {
            // sessionStorage 不可用时只用内存缓存
        }
    }

    /** 归一化接口返回：抽出 ip，内网地址剔除归属地字段 */
    function normalizeIPInfo(raw) {
        const data = raw || {};
        const ip = String(data.ip || '');
        if (isPrivateIP(ip)) {
            return {ip: ip, private: true};
        }
        return {
            ip: ip,
            private: false,
            country: data.country || '',
            province: data.province || '',
            city: data.city || '',
            district: data.district || '',
            isp: data.isp || ''
        };
    }

    async function queryIPInfo() {
        const ticket = (window.xxxr && window.xxxr.ip && window.xxxr.ip.ticket) || '';
        const controller = new AbortController();
        const timer = setTimeout(function () {
            controller.abort();
        }, 3000);
        try {
            const res = await fetch(
                'https://my.ip.cn/json/?ticket=' + encodeURIComponent(ticket),
                {signal: controller.signal, credentials: 'omit', mode: 'cors'}
            );
            if (!res.ok) throw new Error('获取归属地失败: HTTP ' + res.status);
            const json = await res.json();
            const data = normalizeIPInfo(json && json.data);
            writeIPCache(data);
            return {data: data};
        } finally {
            clearTimeout(timer);
        }
    }

    /** 清空 IP 缓存（调试或换网络后强制重查用） */
    function clearIPCache() {
        ipCache = null;
        try {
            window.sessionStorage && window.sessionStorage.removeItem(IP_CACHE_KEY);
        } catch (e) {
            // 忽略
        }
    }

    /**
     * 取访问者 IP 与归属地。
     * @param {{force?: boolean}} [options] force=true 时跳过缓存强制重查
     */
    function getClientIP(options) {
        const force = !!(options && options.force);
        if (isLocalHost()) {
            return Promise.resolve({data: {ip: '127.0.0.1', private: true}});
        }
        if (!force) {
            const cached = readIPCache();
            if (cached) return Promise.resolve({data: cached});
            if (ipInflight) return ipInflight;
        }
        const task = queryIPInfo();
        ipInflight = task;
        return task.finally(function () {
            ipInflight = null;
        });
    }

    /* =====================================================================
     * 十、会话续期：{MainUrl}client-info/index 下发 _sid/_cid/_token
     *   - 进行中的调用直接复用（refreshing）
     *   - 刚成功过的调用在 REFRESH_MIN_INTERVAL 内不再重复发请求：
     *     埋点上报（SendData）和业务页面（index.html）在首屏会各调一次，
     *     只隔几毫秒，没有这个时间窗就会真的发两次
     * ===================================================================== */
    const REFRESH_MIN_INTERVAL = 5000; // 5 秒
    let refreshing = null;
    let lastRefreshAt = 0;
    let lastRefreshResult = null;

    /**
     * @param {{force?: boolean}} [options] force=true 时忽略时间窗强制续期
     */
    function refreshClient(options) {
        const force = !!(options && options.force);
        if (refreshing) return refreshing;
        if (!force && lastRefreshAt && Date.now() - lastRefreshAt < REFRESH_MIN_INTERVAL) {
            return Promise.resolve(lastRefreshResult);
        }
        const cid = encodeURIComponent(getCid());
        const url = baseUrl() + 'client-info/index?cid=' + cid +
            '&mainUrl=' + encodeURIComponent(baseUrl());
        refreshing = fetch(url, {method: 'GET', credentials: 'include', cache: 'no-store'})
            .then(function (res) {
                lastRefreshResult = res;
                lastRefreshAt = Date.now(); // 只在成功时开启时间窗，失败允许立即重试
                return res;
            })
            .catch(function (e) {
                console.warn('[client-info] 会话续期失败', e);
                return null;
            })
            .finally(function () {
                refreshing = null;
            });
        return refreshing;
    }

    /* =====================================================================
     * 十一、上报
     * ===================================================================== */
    function SendData(payload) {
        const url = baseUrl() + 'client-info';
        const body = JSON.stringify(payload);
        let sent = false;

        if (navigator.sendBeacon) {
            try {
                sent = navigator.sendBeacon(url, body);
            } catch (e) {
                sent = false;
            }
        }
        if (!sent) {
            // beacon 不可用或超出配额时退化为 keepalive 请求
            try {
                fetch(url, {
                    method: 'POST',
                    body: body,
                    headers: {'Content-Type': 'text/plain;charset=UTF-8'},
                    credentials: 'include',
                    keepalive: true
                }).catch(function () {
                });
            } catch (e) {
                console.warn('[client-info] 埋点上报失败', e);
            }
        }
        refreshClient();
    }

    /**
     * 采集 + 上报。
     * IP 探测失败不再中断整条记录（旧实现 abort 时直接 return，整次访问不会入库）。
     */
    async function sendClient() {
        let ip = null;
        try {
            const res = await getClientIP();
            ip = res && res.data ? res.data : null;
        } catch (e) {
            if (e && e.name !== 'AbortError') console.warn('[client-info] 获取 IP 归属地失败', e);
        }

        let client = null;
        try {
            client = collectClientInfo();
        } catch (e) {
            console.warn('[client-info] 采集失败', e);
        }

        if (client) SendData({ip: ip, client: client});
    }

    /* =====================================================================
     * 十二、导出（新代码用命名空间，旧代码用旧名字）
     * ===================================================================== */
    window.ClientInfo = {
        refreshClient: refreshClient,
        sendClient: sendClient,
        collect: collectClientInfo,
        getCid: getCid,
        getClientIP: getClientIP,
        clearIPCache: clearIPCache,
        isPrivateIP: isPrivateIP,
        getReferrer: getReferrer,
        parseOS: parseOS,
        parseBrowser: parseBrowser,
        setCookie: setCookie,
        getCookie: getCookie
    };

    // 兼容历史调用点（index.html 里有裸的 refreshClient()）
    window.refreshClient = refreshClient;
    window.sendClient = sendClient;
    window.SendData = SendData;
    window.client_result = client_result;
    window.getCid = getCid;
    window.setCookie = setCookie;
    window.getCookie = getCookie;
    window.serialize = serialize;
    window.sendXhr = sendXhr;
    window.isLocalHost = isLocalHost;
    window.getClientIP = getClientIP;

    window.addEventListener('load', sendClient);
})(window, document);
