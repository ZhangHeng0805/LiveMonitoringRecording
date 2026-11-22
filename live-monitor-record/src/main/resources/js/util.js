/**
 * HTML 转义：所有来自接口/平台的数据在拼接进 innerHTML 前必须经过它
 * 覆盖 &、<、>、"、'、`，可同时安全用于文本节点与属性值
 * @param {*} value
 * @returns {string}
 */
function escapeHtml(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
        .replace(/`/g, "&#96;");
}

/**
 * 安全 URL：只放行 http/https/相对路径/data/blob，其它协议（javascript:、file: 等）一律降级
 * @param {*} value
 * @param {string} [fallback="#"]
 * @returns {string} 可直接写入 href/src 的转义后字符串
 */
function safeUrl(value, fallback) {
    const fb = fallback || "#";
    if (!value) return escapeHtml(fb);
    const url = String(value).trim();
    if (/^(https?:)?\/\//i.test(url) ||
        /^(\.|\.\.)?\//.test(url) ||
        url.startsWith("data:image/") ||
        url.startsWith("blob:")) {
        return escapeHtml(url);
    }
    return escapeHtml(fb);
}

/* ==========================================================================
 * 接口令牌（无状态鉴权）
 * 页面托管在 GitHub Pages、后端经 cpolar 暴露时属于跨站访问，浏览器会拦截
 * 第三方 Cookie，因此鉴权改为「操作秘钥换令牌 + Authorization: Bearer」：
 *   1. 首次/令牌过期 → action/auth?actionKey=xxx 换取 JWT 令牌（localStorage 缓存）
 *   2. 之后所有接口带 Authorization: Bearer <token>
 *   3. 同源直连后端时仍兼容 Cookie 会话，服务端两种都认
 * ========================================================================== */
const API_TOKEN_KEY = "lmr.api.token";
const API_TOKEN_EXP_KEY = "lmr.api.tokenExp";
const API_TOKEN_MIN_LEFT = 5 * 60 * 1000; // 剩余不足 5 分钟即提前续期

function readApiToken() {
    try {
        return {
            token: window.localStorage.getItem(API_TOKEN_KEY),
            exp: Number(window.localStorage.getItem(API_TOKEN_EXP_KEY) || 0),
        };
    } catch (e) {
        return {token: null, exp: 0};
    }
}

function saveApiToken(token, expireSec) {
    try {
        window.localStorage.setItem(API_TOKEN_KEY, token);
        window.localStorage.setItem(API_TOKEN_EXP_KEY, String(Date.now() + expireSec * 1000));
    } catch (e) {
        // 隐私模式下 localStorage 不可用，退化为每次现取
    }
}

function clearApiToken() {
    try {
        window.localStorage.removeItem(API_TOKEN_KEY);
        window.localStorage.removeItem(API_TOKEN_EXP_KEY);
    } catch (e) {
    }
}

function isApiTokenValid() {
    const t = readApiToken();
    return !!t.token && t.exp - Date.now() > API_TOKEN_MIN_LEFT;
}

let apiTokenTask = null;

/**
 * 确保拿到可用的接口令牌
 * @param {boolean} [force] 强制重新换取（令牌被拒时使用）
 * @returns {Promise<string>}
 */
function ensureApiToken(force) {
    if (!force && isApiTokenValid()) return Promise.resolve(readApiToken().token);
    if (apiTokenTask) return apiTokenTask;
    apiTokenTask = (async function () {
        let key = null;
        try {
            key = window.localStorage.getItem("actionKey");
        } catch (e) {
        }
        if (!key && typeof window.promptActionKey === "function") {
            key = await window.promptActionKey();
        }
        if (!key) {
            // 用户关掉验证框：抛“已取消”，让调用方中止本次操作，而不是静默退回 Cookie 继续请求
            const err = new Error("已取消操作秘钥验证");
            err.cancelled = true;
            throw err;
        }
        const url = (window.MainUrl || "") + "action/auth?actionKey=" + encodeURIComponent(key);
        const res = await fetch(url, {credentials: "omit"});
        if (!res.ok) throw new Error("获取接口令牌失败：" + res.status);
        const msg = await res.json();
        if (!msg || msg.code !== 0 || !msg.data || !msg.data.token) {
            throw new Error((msg && msg.message) || "操作秘钥错误");
        }
        saveApiToken(msg.data.token, msg.data.expireSec || 3600);
        return msg.data.token;
    })();
    return apiTokenTask.finally(function () {
        apiTokenTask = null;
    });
}

/* ==========================================================================
 * 接口鉴权规则表（前端可配置）
 * --------------------------------------------------------------------------
 * 每条规则 = {match: "接口路径前缀", mode: "public" | "lazy" | "required"}
 *   public   ：公开接口，完全不带头、不弹框（如换令牌、埋点、只读统计）
 *   lazy     ：懒式（默认）。有令牌就带；被 401/403 才换令牌/弹框 —— 后端说了算
 *   required ：强制校验。发请求前没有可用令牌就先弹框/静默换令牌，再发
 * 匹配规则：先比路径前缀，多条命中时取最长前缀；都没命中用 API_AUTH_DEFAULT_MODE
 *
 * 运行期覆盖（不改代码即可调整）：
 *   localStorage.setItem("lmr.api.authRules", '[{"match":"action/","mode":"required"}]')
 *   或 {"action/":"required","api/videoParsing":"public"} 这种 map 写法
 *   覆盖项优先级高于内置表；mode 写 "auto" 表示撤销该条覆盖
 * 控制台调试：LmrAuthRules.list() / .set("action/monitor","required") / .resolve(url)
 * ========================================================================== */
const API_AUTH_MODE = {PUBLIC: "public", LAZY: "lazy", REQUIRED: "required"};
const API_AUTH_RULES_KEY = "lmr.api.authRules";
const API_AUTH_DEFAULT_MODE = API_AUTH_MODE.LAZY;

/**
 * 内置规则：只保留“绝对不能走鉴权弹框”的接口，
 * 业务接口默认 lazy（例如房间刷新由服务端 403 决定要不要弹框）。
 * 想强制的接口用 localStorage 覆盖，或直接在这里加 {match, mode:"required"}。
 */
const API_AUTH_RULES = [
    {match: "action/auth", mode: API_AUTH_MODE.PUBLIC},      // 换令牌本身若再要令牌会死循环
    {match: "client-info/", mode: API_AUTH_MODE.PUBLIC},     // 前端埋点
    {match: "info/", mode: API_AUTH_MODE.PUBLIC},            // 线程/计数只读
];

function normalizeApiAuthMode(mode) {
    if (!mode) return null;
    const m = String(mode).trim().toLowerCase();
    if (m === "public" || m === "lazy" || m === "required") return m;
    return null;
}

/**
 * 规范化接口路径：去掉 MainUrl 前缀、协议域名、query、hash、前导斜杠
 * @param {string} url
 * @returns {string} 形如 "action/monitor"
 */
function normalizeApiPath(url) {
    let u = String(url === null || url === undefined ? "" : url);
    const root = window.MainUrl || "";
    if (root && u.indexOf(root) === 0) {
        u = u.substring(root.length);
    }
    if (/^https?:\/\//i.test(u)) {
        try {
            u = new URL(u).pathname;
        } catch (e) {
            // 非法 URL 保持原样，交给后续前缀匹配
        }
    }
    u = u.split("#")[0].split("?")[0];
    return u.replace(/^\/+/, "");
}

/**
 * 规则里的前缀也去掉首尾斜杠：写 "fileRes/" 和 "fileRes" 等价，
 * 避免 URL 不带尾斜杠时匹配不上
 */
function normalizeApiRuleMatch(match) {
    return normalizeApiPath(match).replace(/\/+$/, "");
}

/**
 * 读取运行期覆盖规则（localStorage）
 * @returns {Array<{match:string, mode:string}>}
 */
function readApiAuthRuleOverrides() {
    try {
        const raw = window.localStorage.getItem(API_AUTH_RULES_KEY);
        if (!raw) return [];
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
            return parsed
                .filter(r => r && r.match)
                .map(r => ({match: String(r.match), mode: String(r.mode || "")}));
        }
        if (parsed && typeof parsed === "object") {
            return Object.keys(parsed).map(k => ({match: k, mode: String(parsed[k])}));
        }
    } catch (e) {
        console.warn("[authRules] 覆盖规则解析失败，已忽略：", e);
    }
    return [];
}

/**
 * 取生效规则表：覆盖项在前（优先），内置表在后
 */
function listApiAuthRules() {
    return readApiAuthRuleOverrides().concat(API_AUTH_RULES);
}

/**
 * 按 URL 解析鉴权模式
 * @param {string} url 请求地址（可带 MainUrl 前缀）
 * @param {string} [explicitMode] 调用处显式指定，优先级最高
 * @returns {string} public | lazy | required
 */
function resolveApiAuthMode(url, explicitMode) {
    const explicit = normalizeApiAuthMode(explicitMode);
    if (explicit) return explicit;
    const path = normalizeApiPath(url).replace(/\/+$/, "");
    let best = null;
    listApiAuthRules().forEach(function (rule) {
        const match = normalizeApiRuleMatch(rule.match);
        if (!match) return;
        // 按“路径段”匹配：action 不会误伤 actionXxx；action 命中 action 本身和 action/xxx
        const hit = path === match || path.startsWith(match + "/");
        if (!hit) return;
        if (!best || match.length > best.match.length) {
            best = {match: match, mode: normalizeApiAuthMode(rule.mode)};
        }
    });
    return best && best.mode ? best.mode : API_AUTH_DEFAULT_MODE;
}

/**
 * 写一条覆盖规则（mode 传 "auto" 表示撤销覆盖，回到内置表）
 * @param {string} match 路径前缀
 * @param {string} mode public | lazy | required | auto
 */
function setApiAuthRule(match, mode) {
    const m = String(match || "").trim();
    if (!m) throw new Error("match 不能为空");
    const overrides = readApiAuthRuleOverrides().filter(r => normalizeApiRuleMatch(r.match) !== normalizeApiRuleMatch(m));
    if (normalizeApiAuthMode(mode)) {
        overrides.push({match: m, mode: normalizeApiAuthMode(mode)});
    }
    try {
        window.localStorage.setItem(API_AUTH_RULES_KEY, JSON.stringify(overrides));
    } catch (e) {
        throw new Error("localStorage 不可用，无法保存鉴权规则");
    }
    return listApiAuthRules();
}

// 控制台/其它脚本可用的入口
window.LmrAuthRules = {
    modes: API_AUTH_MODE,
    list: listApiAuthRules,
    resolve: resolveApiAuthMode,
    set: setApiAuthRule,
    remove: function (match) {
        return setApiAuthRule(match, "auto");
    },
    reset: function () {
        try {
            window.localStorage.removeItem(API_AUTH_RULES_KEY);
        } catch (e) {
        }
        return listApiAuthRules();
    }
};

/**
 * 带令牌的 fetch，按接口规则决定行为：
 *   public  ：直接匿名请求，不带头、永不弹框
 *   required：先保证有令牌再发（没令牌→静默换/弹框），令牌被拒→清掉重新换一次
 *   lazy    ：有令牌就带；被 401/403 → 去头重试一次（避免失效令牌把公开接口打成 403）；
 *             匿名也 401/403 → 才换令牌（有 actionKey 静默换，没有才弹秘钥框）
 * 用户在任何一次验证框点 ✕/取消 → 抛 cancelled，调用方中止操作，不再回退请求。
 * @param {string} url
 * @param {RequestInit} [options]
 * @param {string} [authMode] 显式指定 public | lazy | required，省略则按规则表匹配
 * @returns {Promise<Response>}
 */
async function authFetch(url, options, authMode) {
    options = options || {};
    const mode = resolveApiAuthMode(url, authMode);
    const exec = async function (token) {
        const headers = Object.assign({}, options.headers || {});
        if (token) headers["Authorization"] = "Bearer " + token;
        return fetch(url, Object.assign({}, options, {headers: headers}));
    };
    const needAuth = function (res) {
        return res.status === 401 || res.status === 403;
    };

    // 1) 公开接口：不带头、不走鉴权流程
    if (mode === API_AUTH_MODE.PUBLIC) {
        return exec(null);
    }

    // 2) 强制校验：先拿到令牌再发请求
    if (mode === API_AUTH_MODE.REQUIRED) {
        let token = isApiTokenValid() ? readApiToken().token : null;
        // 没有可用令牌：有 actionKey 就静默换，没有才弹框（取消则抛 cancelled）
        if (!token) token = await ensureApiToken(false);
        let res = await exec(token);
        if (!needAuth(res)) return res;
        // 令牌被判失效：清掉重新换取后重试一次，仍失败就把结果交回调用方
        clearApiToken();
        const fresh = await ensureApiToken(true);
        return exec(fresh);
    }

    // 3) 懒式（默认）：后端 401/403 才升级为鉴权
    const useToken = isApiTokenValid() ? readApiToken().token : null;
    let res = await exec(useToken);
    if (!needAuth(res)) return res;

    // 第一轮失败：令牌可能失效，清掉后按匿名/Cookie 再试一次
    if (useToken) {
        clearApiToken();
        res = await exec(null);
        if (!needAuth(res)) return res;
    }

    // 匿名也没权限：这才说明服务端确实要求校验
    clearApiToken();
    const fresh = await ensureApiToken(true);
    return exec(fresh);
}

/**
 * 公开只读 fetch：不带令牌直接请求（陌生人也能看到页面数据）
 * 若后端关闭了 server.auth.publicRead（返回 403），自动退回带令牌请求，弹出秘钥框
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<Response>}
 */
async function publicFetch(url, options) {
    // 规则表里明确标 public 的接口：就是纯匿名，不升级鉴权
    if (resolveApiAuthMode(url) === API_AUTH_MODE.PUBLIC) {
        return fetch(url, options || {});
    }
    const res = await fetch(url, options || {});
    if (res.status === 401 || res.status === 403) {
        return authFetch(url, options);
    }
    return res;
}

// 无法带请求头的地址（<a>/<img>/<video>）用查询参数携带令牌
function withApiToken(url) {
    const t = readApiToken();
    if (!t.token) return url;
    return url + (url.indexOf("?") > -1 ? "&" : "?") + "token=" + encodeURIComponent(t.token);
}

// 单页（如抖音作品解析）没有加载 RoomAction.js 时的兜底秘钥输入；
// 首页会由 RoomAction.js 覆盖为统一的「操作验证」弹窗
if (typeof window.promptActionKey !== "function") {
    window.promptActionKey = function () {
        return new Promise(function (resolve) {
            const value = window.prompt("请输入操作秘钥（设备标识 ID）");
            resolve(value && value.trim() ? value.trim() : null);
        });
    };
}

/**
 * 安全格式化时间：无效/空值返回“未知”，避免出现 Invalid Date
 * @param {number|string|Date} value
 * @returns {string}
 */
function formatDateSafe(value) {
    if (value === null || value === undefined || value === "" || value === 0) {
        return "未知";
    }
    const date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) return "未知";
    return formatDate(date);
}

//发起GET请求
/**
 * @param {string} path 接口路径（如 "action/monitor"）
 * @param {Object} [params] URL 参数
 * @param {number} [timeoutMs] 超时毫秒，默认 10000
 * @param {string} [authMode] 鉴权模式 public | lazy | required，省略则按接口规则表匹配
 */
async function getRequest(path, params, timeoutMs, authMode) {
    const limit = typeof timeoutMs === "number" && timeoutMs > 0 ? timeoutMs : 10000;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), limit); // 默认 10 秒，可按接口放宽
    try {
        let requestParam = "";
        if (params) {
            requestParam = `?${objectToUrlParams(params)}`;
        }
        path = path.startsWith("/") ? path.substring(1) : path;
        let root = window.MainUrl ? window.MainUrl : "";
        let url = `${root}${path}${requestParam}`;
        // 跨站访问带 Bearer 令牌；鉴权模式按接口规则表决定（也可由 authMode 显式指定）
        const res = await authFetch(url, {
            signal: controller.signal,
            method: "GET",
            // 同源时仍可携带 Cookie 会话（跨站浏览器会自动忽略）
            credentials: "include"
        }, authMode);
        if (res.status === 401 || res.status === 403) {
            throw new Error("鉴权失败，请重新输入操作秘钥");
        }
        if (!res.ok) {
            throw new Error(`网络响应错误: ${res.status} ${res.statusText}`);
        }
        const mockData = await res.json();
        console.log(path + "获取的数据:", mockData);
        return mockData;
    } catch (error) {
        // 用户在「操作验证」弹窗里点了 ✕/取消：明确告知本次操作没执行，不再发请求
        if (error && error.cancelled) {
            console.warn("[request] 已取消操作秘钥验证，本次请求未发送");
            alert("已取消操作秘钥验证，本次操作未执行");
            throw error;
        }
        let errorMessage = "请求失败";
        let timeoutError = null;
        if (error.name === "AbortError") {
            // 浏览器对 abort 的默认 message 是「signal is aborted without reason」，这里换成可读文案
            errorMessage = `请求超时（${Math.round(limit / 1000)} 秒），请检查后端服务是否可达`;
            timeoutError = new Error(errorMessage);
            timeoutError.name = "TimeoutError";
            timeoutError.timeout = true;
        } else if (error.message) {
            errorMessage = error.message;
        }
        console.error(errorMessage, error);
        alert("请求失败 - " + errorMessage);
        throw timeoutError || error; // 抛出给调用方处理
    } finally {
        // 无论成功失败，都清除定时器，防止内存残留
        clearTimeout(timeoutId);
    }
}

//发起POST请求
/**
 * @param {string} path 接口路径（如 "action/record"）
 * @param {Object} [params] JSON 请求体
 * @param {number} [timeoutMs] 超时毫秒，默认 10000
 * @param {string} [authMode] 鉴权模式 public | lazy | required，省略则按接口规则表匹配
 */
async function postRequest(path, params, timeoutMs, authMode) {
    const limit = typeof timeoutMs === "number" && timeoutMs > 0 ? timeoutMs : 10000;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), limit); // 默认 10 秒，可按接口放宽
    try {
        // 处理接口路径
        path = path.startsWith("/") ? path.slice(1) : path;
        const root = window.MainUrl ?? "";
        const url = `${root}${path}`;

        // 只序列化一次参数
        let body;
        if (params !== undefined && params !== null) {
            body = JSON.stringify(params);
        }

        const res = await authFetch(url, {
            signal: controller.signal,
            method: "POST",
            // 同源时仍可携带 Cookie 会话（跨站浏览器会自动忽略）
            credentials: "include",
            headers: {
                "Content-Type": "application/json"
            },
            body
        }, authMode);

        if (res.status === 401 || res.status === 403) {
            throw new Error("鉴权失败，请重新输入操作秘钥");
        }
        if (!res.ok) {
            throw new Error(`网络响应错误: ${res.status} ${res.statusText}`);
        }

        const mockData = await res.json();
        console.log(path + "获取的数据:", mockData);
        return mockData;
    } catch (error) {
        // 用户在「操作验证」弹窗里点了 ✕/取消：明确告知本次操作没执行，不再发请求
        if (error && error.cancelled) {
            console.warn("[request] 已取消操作秘钥验证，本次请求未发送");
            alert("已取消操作秘钥验证，本次操作未执行");
            throw error;
        }
        let errorMessage = "请求失败";
        let timeoutError = null;
        if (error.name === "AbortError") {
            // 浏览器对 abort 的默认 message 是「signal is aborted without reason」，这里换成可读文案
            errorMessage = `请求超时（${Math.round(limit / 1000)} 秒），请检查后端服务是否可达`;
            timeoutError = new Error(errorMessage);
            timeoutError.name = "TimeoutError";
            timeoutError.timeout = true;
        } else if (error.message) {
            errorMessage = error.message;
        }
        console.error(errorMessage, error);
        alert("请求失败 - " + errorMessage);
        throw timeoutError || error; // 抛出给调用方处理
    } finally {
        // 无论成功失败，都清除定时器，防止内存残留
        clearTimeout(timeoutId);
    }
}

//将对象转为请求参数
function objectToUrlParams(obj) {
    if (obj === null || typeof obj !== "object") {
        return "";
    }

    const searchParams = new URLSearchParams();
    for (const key in obj) {
        if (!obj.hasOwnProperty(key)) continue;

        const value = obj[key];
        if (value === undefined || value === null) continue;

        // 直接 append 键值对，内部会自动编码
        searchParams.append(key, value);
    }

    // 转换为查询字符串（已包含编码）
    return searchParams.toString();
}

// 数字格式化（如10000 -> 10,000或10万）
function formatNumber(num) {
    if (!num && num !== 0) return "0";
    // 处理字符串类型的数字
    if (typeof num === "string") {
        if (num.indexOf("万") > 0) {
            return num;
        }
        num = parseInt(num);
        if (isNaN(num)) return num;
    }

    if (num >= 1000000) {
        return (num / 1000000).toFixed(1) + "百万";
    } else if (num >= 10000) {
        return (num / 10000).toFixed(1) + "万";
    } else if (num >= 1000) {
        return num.toLocaleString();
    }
    return num;
}

// 数字变化动画
function updateCounter(elementId, targetValue) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const startValue = parseInt(element.textContent) || 0;
    const duration = 500; // 动画持续时间（毫秒）
    const frameDuration = 16; // 每帧持续时间（约16ms = 60fps）
    const totalFrames = Math.round(duration / frameDuration);
    let frame = 0;

    const counter = setInterval(() => {
        frame++;
        const progress = frame / totalFrames;
        const currentValue = Math.round(
            startValue + (targetValue - startValue) * progress
        );

        element.textContent = currentValue;

        if (frame === totalFrames) {
            clearInterval(counter);
            element.textContent = targetValue; // 确保最终值正确
        }
    }, frameDuration);
}

// 提取日期格式化函数
function formatDate(date) {
    return date
        .toLocaleString("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
        })
        .replace(/\//g, "-");
}

// 格式化文件大小
function formatFileSize(bytes) {
    if (isNaN(bytes)) return bytes;
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

/**
 * 防抖函数
 * 频繁触发时，延迟执行目标函数；支持立即执行模式，适用于搜索输入、按钮防重复点击等场景
 * @param {Function} func - 需要防抖处理的目标函数
 * @param {number} wait - 防抖等待延迟时间，单位：毫秒
 * @param {boolean} [immediate=false] - 是否立即执行
 *        false：延迟执行，停止触发后等待 wait ms 再执行函数（搜索输入推荐）
 *        true：首次触发立即执行，后续 wait ms 内重复触发全部拦截（提交按钮防重复点击推荐）
 * @returns {Function} 包装后的防抖函数，自带 cancel 取消方法
 */
function debounce(func, wait, immediate) {
    // 闭包存储定时器标识，持久保存不会随每次调用销毁
    let timer;
    // 保存原函数执行返回值（immediate模式下可获取返回结果）
    let result;

    /**
     * 防抖包装执行函数
     * @returns {*} 原函数执行返回值（仅immediate模式同步执行时有效）
     */
    const debounced = function () {
        // 保存事件上下文this（DOM绑定事件时指向触发元素）
        const context = this;
        // 保存调用时传入的全部参数（如event事件对象）
        const args = arguments;

        // 核心防抖逻辑：存在未完成定时器则清空，重新计时
        if (timer) clearTimeout(timer);

        // 立即执行模式
        if (immediate) {
            // timer为空代表无冷却计时，判定为首次触发，可以执行函数
            const callNow = !timer;
            // 创建冷却定时器，等待wait毫秒后重置timer标识，解除冷却
            timer = setTimeout(() => {
                timer = null;
            }, wait);
            // 满足首次触发条件，同步执行原函数并缓存返回值
            if (callNow) result = func.apply(context, args);
        } else {
            // 延迟执行模式：停止触发wait毫秒后再执行原函数
            timer = setTimeout(() => {
                result = func.apply(context, args);
            }, wait);
        }
        // 返回原函数执行结果
        return result;
    };

    /**
     * 取消防抖，清空定时器、重置状态
     * 使用场景：组件销毁、弹窗关闭、页面跳转，防止定时器延迟执行产生异常逻辑
     */
    debounced.cancel = function () {
        clearTimeout(timer);
        timer = null;
    };

    return debounced;
}

/**
 * 复制文本到剪贴板
 * @param {string} text
 * @returns {Promise<boolean>}
 */
async function copyToClipboard(text) {
    if (!text) return false;
    try {
        await navigator.clipboard.writeText(text);
        console.log("已复制：", text);
        return true;
    } catch (err) {
        console.error("复制失败", err);
        // 降级方案，兼容老浏览器
        const input = document.createElement('input');
        input.value = text;
        document.body.appendChild(input);
        input.select();
        document.execCommand('copy');
        document.body.removeChild(input);
        return true;
    }
}