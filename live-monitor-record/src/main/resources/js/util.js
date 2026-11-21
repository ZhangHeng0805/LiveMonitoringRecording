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
        if (!key) throw new Error("缺少操作秘钥，无法访问接口");
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

/**
 * 带令牌的 fetch：403/401 时自动重新换取令牌并重试一次
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<Response>}
 */
async function authFetch(url, options) {
    options = options || {};
    const exec = async function (token) {
        const headers = Object.assign({}, options.headers || {});
        if (token) headers["Authorization"] = "Bearer " + token;
        return fetch(url, Object.assign({}, options, {headers: headers}));
    };
    let token = null;
    try {
        token = await ensureApiToken(false);
    } catch (e) {
        token = null; // 拿不到令牌就先用 Cookie 会话试一次
    }
    let res = await exec(token);
    if (res.status === 401 || res.status === 403) {
        const fresh = await ensureApiToken(true); // 秘钥缺失/错误会在这里抛出
        res = await exec(fresh);
    }
    return res;
}

/**
 * 公开只读 fetch：不带令牌直接请求（陌生人也能看到页面数据）
 * 若后端关闭了 server.auth.publicRead（返回 403），自动退回带令牌请求，弹出秘钥框
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<Response>}
 */
async function publicFetch(url, options) {
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
async function getRequest(path, params, timeoutMs) {
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
        // 跨站访问带 Bearer 令牌；403 会自动换令牌重试一次
        const res = await authFetch(url, {
            signal: controller.signal,
            method: "GET",
            // 同源时仍可携带 Cookie 会话（跨站浏览器会自动忽略）
            credentials: "include"
        });
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
async function postRequest(path, params, timeoutMs) {
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
        });

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