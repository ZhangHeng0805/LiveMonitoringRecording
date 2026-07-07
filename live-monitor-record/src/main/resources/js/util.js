//发起GET请求
async function getRequest(path, params) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000); // 10秒超时
    try {
        let requestParam = "";
        if (params) {
            requestParam = `?${objectToUrlParams(params)}`;
        }
        path = path.startsWith("/") ? path.substring(1) : path;
        let root = window.MainUrl ? window.MainUrl : "";
        let url = `${root}${path}${requestParam}`;
        const res = await fetch(url, {
            signal: controller.signal,
            method: "GET",
            // 跨域携带凭证（Cookie）
            credentials: "include"
        });
        if (!res.ok) {
            throw new Error(`网络响应错误: ${res.status} ${res.statusText}`);
        }
        const mockData = await res.json();
        console.log(path + "获取的数据:", mockData);
        return mockData;
    } catch (error) {
        let errorMessage = "请求失败";
        if (error.name === "AbortError") {
            errorMessage = "请求超时（10秒）";
        } else if (error.message) {
            errorMessage = error.message;
        }
        console.error(errorMessage, error);
        throw error; // 抛出给调用方处理
    } finally {
        // 无论成功失败，都清除定时器，防止内存残留
        clearTimeout(timeoutId);
    }
}

//发起POST请求
async function postRequest(path, params) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000); // 10秒超时
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

        const res = await fetch(url, {
            signal: controller.signal,
            method: "POST",
            // 跨域携带凭证（Cookie）
            credentials: "include",
            headers: {
                //   "Content-Type": "application/json",
                // Authorization: `Bearer ${localStorage.getItem('token')}`
            },
            body
        });

        if (!res.ok) {
            throw new Error(`网络响应错误: ${res.status} ${res.statusText}`);
        }

        const mockData = await res.json();
        console.log(path + "获取的数据:", mockData);
        return mockData;
    } catch (error) {
        let errorMessage = "请求失败";
        if (error.name === "AbortError") {
            errorMessage = "请求超时（10秒）";
        } else if (error.message) {
            errorMessage = error.message;
        }
        console.error(errorMessage, error);
        throw error; // 抛出给调用方处理
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