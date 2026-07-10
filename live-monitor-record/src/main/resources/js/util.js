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
        alert("请求失败 - "+errorMessage);
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
        alert("请求失败 - "+errorMessage);
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