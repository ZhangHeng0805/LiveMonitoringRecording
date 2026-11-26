const REDIRECT_CONFIG_URL =
    "https://zhangheng0805.github.io/LiveMonitoringRecordingPage/redirect/json/config.json";
const REDIRECT_CACHE_KEY = "lmr.redirect.config";
const REDIRECT_CACHE_TTL = 10 * 60 * 1000; // 10 分钟内复用缓存，避免每次打开页面都同步请求外网

function getMainUrl(fullUrl=window.location.href,isDefault=true) {
    // fullUrl = fullUrl || window.location.href;
    // 初始化默认基础URL
    let mainUrl = "http://localhost:8005/";
    // 处理当前页面URL（如果是http开头）
    if (fullUrl.startsWith("http")) {
        mainUrl = fullUrl.split("?")[0];
    }
    // 处理URL参数中的url（优先级更高）
    const urlParams = new URLSearchParams(window.location.search);

    const rootParam = urlParams.get("root");
    const redirectParam = urlParams.get("redirect");

    if (rootParam) {
        // 解码参数中的URL（支持http开头的明文和base64编码）
        try {
            mainUrl = rootParam.startsWith("http")
                ? decodeURIComponent(rootParam)
                : atob(rootParam);
        } catch (e) {
            console.error("解析 url 参数失败", e);
        }
    } else if (redirectParam || isDefault) {
        try {
            const config = getRedirectConfig(redirectParam || REDIRECT_CONFIG_URL, true);
            if (config && config.redirectUrl) {
                mainUrl = config.redirectUrl;
            }
        } catch (e) {
            console.error(e);
        }
    }

    // 本机访问时无需拉取外网重定向配置（既没意义也会拖慢首屏）
    if (fullUrl.startsWith("http") && !window.xxxr && !isLocalHostName()) {
        try {
            getRedirectConfig(REDIRECT_CONFIG_URL);
        } catch (e) {
            console.error(e);
        }
    }
    // 确保URL以斜杠结尾
    let rootURl = mainUrl.endsWith("/") ? mainUrl : `${mainUrl}/`;
    window.MainUrl = rootURl;
    return rootURl;
}

function isLocalHostName() {
    const host = window.location.hostname;
    return host === "localhost" || host === "127.0.0.1" || host === "::1" || !host;
}

function readConfigCache() {
    try {
        const raw = window.localStorage && window.localStorage.getItem(REDIRECT_CACHE_KEY);
        if (!raw) return null;
        const cache = JSON.parse(raw);
        if (!cache || !cache.time || Date.now() - cache.time > REDIRECT_CACHE_TTL) return null;
        return cache.data || null;
    } catch (e) {
        return null;
    }
}

function writeConfigCache(data) {
    try {
        window.localStorage &&
        window.localStorage.setItem(REDIRECT_CACHE_KEY, JSON.stringify({time: Date.now(), data: data}));
    } catch (e) {
        // localStorage 不可用（隐私模式等）时忽略缓存
    }
}
/**
 * 获取配置
 * @param {*} config_url 配置地址
 * @param {*} sync 是否同步
 * @returns 
 */
function getRedirectConfig(config_url, sync) {
    // 默认地址优先走缓存
    const cached = config_url === REDIRECT_CONFIG_URL ? readConfigCache() : null;
    if (cached) {
        window.xxxr = cached;
        return cached;
    }

    // 显式传入 redirect 参数时必须拿到结果，用同步请求；页面预取则走异步，避免阻塞首屏
    if (sync) {
        const xhr = new XMLHttpRequest();
        xhr.open("GET", config_url, false);
        xhr.send(null);
        if (xhr.status >= 200 && xhr.status < 300) {
            const config = JSON.parse(xhr.responseText);
            window.xxxr = config;
            return config;
        }
        throw new Error("获取getRedirectConfig失败: " + xhr.status);
    }

    const xhr = new XMLHttpRequest();
    xhr.open("GET", config_url + "?_t=" + new Date().getTime(), true); // 异步，不阻塞首屏
    xhr.onload = function () {
        if (xhr.status >= 200 && xhr.status < 300) {
            try {
                const config = JSON.parse(xhr.responseText);
                window.xxxr = config;
                if (config_url === REDIRECT_CONFIG_URL) writeConfigCache(config);
            } catch (e) {
                console.error("解析重定向配置失败", e);
            }
        } else {
            console.warn("获取重定向配置失败", xhr.status);
        }
    };
    xhr.onerror = function () {
        console.warn("获取重定向配置失败：网络异常");
    };
    xhr.send(null);

    // 异步请求拿不到返回值，先返回空配置对象，调用方按 MainUrl 兜底
    return window.xxxr || {};
}
