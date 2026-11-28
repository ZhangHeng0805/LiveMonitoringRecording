/**
 * 监听回收站弹窗
 * 结构：列表（每行一个房间，点击展开配置详情）→ 恢复监听 / 彻底删除
 */
function roomRecycle() {
    // 已验证过直接拉列表；没验证过才弹一次验证；取消静默退出，不弹任何提示
    requireVerified((key) => {
        getRequest("room-recycle/getList", {
            actionKey: key,
            pageNum: 1,
            pageSize: 100,
        }).then((msg) => {
            if (!msg.success) {
                showActionAfterModal(msg);
            } else {
                showRoomRecycleModel(key, msg.data);
            }
        }).catch(() => {
            // getRequest 已弹出错误提示，这里只兜住异常避免中断
        });
    });
}

// 回收站标题形如“主播名-DouYin-762166543810”，拆成 名称 / 平台 / 房间ID
function parseRecycleTitle(title) {
    const raw = String(title || "").trim();
    const parts = raw.split("-");
    if (parts.length >= 3) {
        return {
            name: parts.slice(0, parts.length - 2).join("-"),
            platform: parts[parts.length - 2],
            id: parts[parts.length - 1],
        };
    }
    return {name: raw, platform: "", id: ""};
}

// 平台代码转中文，未知时原样显示
function platformLabel(code) {
    if (!code) return "未知";
    return (typeof platformName !== "undefined" && platformName[code]) || code;
}

async function fetchRecycleContents(pageNum = 1, pageSize = 100) {
    const rootDom = document.getElementById("roomRecycleList");
    if (!rootDom) return {list: [], total: 0};
    try {
        const res = await getRequest("room-recycle/getList", {
            actionKey: rootDom.dataset.key,
            pageNum: pageNum,
            pageSize: pageSize,
        });
        return res.data || {list: [], total: 0};
    } catch (error) {
        return {list: [], total: 0};
    }
}

function showRoomRecycleModel(key, data) {
    // 打开回收站不再先验证秘钥，这里补一个默认值，避免 data-key 写成 undefined
    if (key === undefined || key === null || key === "") {
        try {
            key = (window.localStorage && window.localStorage.getItem("actionKey")) || "";
        } catch (e) {
            key = "";
        }
    }
    const total = (data && data.total) || 0;
    setModalContent(
        "直播监听回收站",
        `<div class="flex items-center gap-2 pb-3">
            <div class="relative flex-1 min-w-[11rem]">
                <i class="fa fa-search absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 text-xs"></i>
                <input id="recycleSearch" type="search" autocomplete="off" placeholder="搜索名称 / 房间ID / 平台"
                       class="w-full pl-7 pr-3 py-1.5 text-sm bg-white border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary">
            </div>
            <span id="recycleMeta" class="text-xs text-gray-400 whitespace-nowrap">${total ? `共 ${total} 个` : ""}</span>
        </div>
        <div class="rounded-lg border border-gray-200 bg-gray-50/60">
            <div id="roomRecycleList" data-key="${escapeHtml(key)}" data-total="${total}" class="divide-y divide-gray-200"></div>
        </div>`,
        [
            {
                text: "关闭",
                className: "bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 focus:ring-gray-300",
                onClick: closeModal,
            },
        ],
        {maxWidth: "56rem", hint: total ? `共 ${total} 个` : ""}
    );
    // 初始化列表，默认第一页
    const rootDom = document.getElementById("roomRecycleList");
    rootDom.dataset.currentPage = "1";
    rootDom.dataset.pageSize = "100";
    renderRoomRecycleContent(rootDom, data);

    const searchDom = document.getElementById("recycleSearch");
    if (searchDom) {
        const run = debounce(applyRecycleFilter, 160);
        searchDom.addEventListener("input", run);
        searchDom.addEventListener("search", run);
    }
}

/**
 * 回收站本地过滤：只筛已加载的条目，不额外请求后端
 * 关键词按空格拆分，多个词要求全部命中（与首页房间搜索口径一致）
 */
function applyRecycleFilter() {
    const rootDom = document.getElementById("roomRecycleList");
    const searchDom = document.getElementById("recycleSearch");
    if (!rootDom || !searchDom) return;

    const tokens = String(searchDom.value || "")
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean);
    const matcher = typeof fuzzyMatch === "function"
        ? fuzzyMatch
        : (text, q) => text.indexOf(q) > -1;

    let hits = 0;
    rootDom.querySelectorAll(".recycle-item").forEach((card) => {
        const text = (card.dataset.search || "").toLowerCase();
        const hit = tokens.length === 0 || tokens.every((t) => matcher(text, t));
        card.style.display = hit ? "" : "none";
        if (hit) hits++;
    });

    // 搜索时隐藏「加载更多」：后端分页和本地过滤会打架
    const moreBtn = rootDom.querySelector(".load-more-btn");
    if (moreBtn) moreBtn.style.display = tokens.length ? "none" : "";

    // 空结果占位
    let emptyDom = rootDom.querySelector(".recycle-empty");
    if (tokens.length && hits === 0) {
        if (!emptyDom) {
            emptyDom = document.createElement("div");
            emptyDom.className = "recycle-empty px-4 py-8 text-center text-gray-400 text-sm";
            emptyDom.innerHTML = `<i class="fa fa-search text-xl block mb-2"></i>没有匹配的监听记录，换个关键词试试`;
            rootDom.appendChild(emptyDom);
        }
    } else if (emptyDom) {
        emptyDom.remove();
    }

    const meta = document.getElementById("recycleMeta");
    if (meta) {
        meta.textContent = tokens.length
            ? `命中 ${hits} 个`
            : (rootDom.dataset.total && Number(rootDom.dataset.total) > 0
                ? `共 ${rootDom.dataset.total} 个`
                : "");
    }
}

async function renderRoomRecycleContent(rootDom, data, isAppend = false) {
    let currentPage = parseInt(rootDom.dataset.currentPage || "1");
    const pageSize = parseInt(rootDom.dataset.pageSize || "100");

    if (!isAppend) {
        rootDom.innerHTML = `<div class="px-4 py-6 text-center text-gray-500 text-sm"><i class="fa fa-spinner fa-spin mr-1"></i>加载中...</div>`;
        currentPage = 1;
        rootDom.dataset.currentPage = currentPage;
    }

    if (!data) {
        data = await fetchRecycleContents(currentPage, pageSize);
    }
    const list = data.list || [];
    const total = data.total || 0;
    const totalPage = Math.ceil(total / pageSize);
    if (!isAppend) rootDom.dataset.total = String(total);

    if (!isAppend) {
        rootDom.innerHTML = "";
        if (list.length === 0) {
            rootDom.innerHTML = `<div class="px-4 py-10 text-center text-gray-400 text-sm">
                <i class="fa fa-inbox text-2xl block mb-2"></i>回收站是空的，删除的监听会出现在这里
            </div>`;
            return;
        }
    }

    // 先移除旧的“加载更多”，再追加新内容
    const oldBtn = rootDom.querySelector(".load-more-btn");
    if (oldBtn) oldBtn.remove();

    list.forEach((item) => {
        const info = parseRecycleTitle(item.title);
        const card = document.createElement("div");
        card.className = "recycle-item";
        // 搜索用的全量文本：名称 / 房间ID / 平台（含中文名）/ 标题 / 路径
        card.dataset.search = [
            info.name,
            info.id,
            info.platform,
            platformLabel(info.platform),
            item.title,
            item.path,
        ].filter(Boolean).join(" ").toLowerCase();
        card.innerHTML = `
            <button type="button"
                    class="recycle-toggle w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-white transition-colors">
                <i class="fa fa-chevron-right text-[10px] text-gray-400 transition-transform duration-200 w-3"></i>
                <span class="w-7 h-7 rounded-md bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
                    <i class="fa fa-tv text-xs"></i>
                </span>
                <span class="min-w-0 flex-1">
                    <span class="block text-sm font-medium text-gray-800 truncate">${escapeHtml(info.name || item.title)}</span>
                    <span class="block text-xs text-gray-400 truncate">${info.id ? "房间ID: " + escapeHtml(info.id) : escapeHtml(item.path)}</span>
                </span>
                <span class="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 flex-shrink-0">
                    ${escapeHtml(platformLabel(info.platform))}
                </span>
            </button>
            <div class="recycle-child-wrap hidden px-4 pb-4 bg-gray-50 border-t border-gray-200"
                 data-path="${escapeHtml(item.path)}"></div>`;
        rootDom.appendChild(card);
    });

    if (currentPage < totalPage) {
        const moreBtn = document.createElement("div");
        moreBtn.className = "load-more-btn py-3 text-center";
        moreBtn.innerHTML = `<button type="button" class="text-sm text-primary hover:underline">加载更多</button>`;
        moreBtn.onclick = async function () {
            let p = parseInt(rootDom.dataset.currentPage || "1");
            rootDom.dataset.currentPage = ++p;
            await renderRoomRecycleContent(rootDom, null, true);
        };
        rootDom.appendChild(moreBtn);
    }

    bindRecycleToggle(rootDom);
    // 恢复/删除/加载更多后重渲染，关键词继续生效
    if (document.getElementById("recycleSearch")) applyRecycleFilter();
}

// 展开/收起详情
function bindRecycleToggle(rootDom) {
    rootDom.querySelectorAll(".recycle-toggle").forEach((toggle) => {
        if (toggle.dataset.bound) return;
        toggle.dataset.bound = "1";
        toggle.onclick = async function () {
            const wrapDom = this.nextElementSibling;
            const arrow = this.querySelector(".fa-chevron-right");
            arrow.classList.toggle("rotate-90");
            this.classList.toggle("bg-white");

            if (!wrapDom.classList.contains("hidden")) {
                wrapDom.classList.add("hidden");
                return;
            }
            wrapDom.classList.remove("hidden");
            if (wrapDom.innerHTML === "") {
                await renderRecycleDetail(wrapDom);
            }
        };
    });
}

// 详情面板：字段卡片 + 底部操作
async function renderRecycleDetail(wrapDom) {
    const path = wrapDom.dataset.path;
    const rootDom = document.getElementById("roomRecycleList");
    const key = rootDom.dataset.key;

    wrapDom.innerHTML = `<div class="py-4 text-center text-gray-500 text-sm"><i class="fa fa-spinner fa-spin mr-1"></i>加载房间配置...</div>`;

    let res;
    try {
        res = await getRequest("room-recycle/getDetails", {path: path, actionKey: key});
    } catch (error) {
        wrapDom.innerHTML = `<p class="py-3 text-sm text-red-600">读取失败，请重试</p>`;
        return;
    }
    if (!res || !res.data) {
        wrapDom.innerHTML = `<p class="py-3 text-sm text-red-600">${escapeHtml((res && res.message) || "未获取到房间信息")}</p>`;
        return;
    }

    const room = res.data;
    const setting = room.setting || {};
    const field = (label, control, spanFull) => `
        <div class="${spanFull ? "sm:col-span-2" : ""}">
            <div class="text-xs text-gray-500 mb-1">${label}</div>
            ${control}
        </div>`;
    const inputCls = "w-full px-2.5 py-1.5 text-sm bg-white border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary";
    const check = (cls, checked, text) => `
        <label class="flex items-center gap-2 cursor-pointer select-none h-[34px]">
            <input type="checkbox" class="${cls} h-4 w-4 text-primary focus:ring-primary" ${checked ? "checked" : ""}>
            <span class="text-sm text-gray-700">${text}</span>
        </label>`;

    wrapDom.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-4">
            ${field("房间 ID", `<div class="px-2.5 py-1.5 text-sm bg-white border border-gray-200 rounded-md text-gray-600 truncate" title="${escapeHtml(room.id)}">${escapeHtml(room.id)}</div>`)}
            ${field("平台", `<div class="px-2.5 py-1.5 text-sm bg-white border border-gray-200 rounded-md text-gray-600">${escapeHtml(platformLabel(room.platform))}</div>`)}
            ${field("名称", `<input type="text" class="room-name ${inputCls}" value="${escapeHtml(room.name || "")}" placeholder="未命名">`)}
            ${field("保存路径", `<input type="text" class="savePath ${inputCls}" value="" title="${escapeHtml(room.savePath || "")}" placeholder="${escapeHtml(room.savePath || "未设置，默认存到程序目录")}">`)}
            ${field("刷新频率（秒）", `<input type="number" min="10" class="delayIntervalSec ${inputCls}" value="${escapeHtml(setting.delayIntervalSec ?? "")}" placeholder="沿用全局配置">`)}
            ${field("录制画质", (() => {
                const rq = ({ HIGH: "HIGH", MIDDLE: "MIDDLE", LOW: "LOW", "最高": "HIGH", "中等": "MIDDLE", "最低": "LOW" })[setting.recordQuality] || "HIGH";
                return `<select class="recordQuality ${inputCls}">
                <option value="HIGH" ${rq === "HIGH" ? "selected" : ""}>最高画质</option>
                <option value="MIDDLE" ${rq === "MIDDLE" ? "selected" : ""}>中等画质</option>
                <option value="LOW" ${rq === "LOW" ? "selected" : ""}>最低画质</option>
            </select>`;
            })())}
            ${field("启用状态", `<div class="px-2.5 py-1.5 text-sm bg-light border border-gray-200 rounded-md text-gray-600 flex items-center gap-2" title="恢复后由程序自动置为启用，无需手动设置"><span>${room.isEnable === false ? "已停用（恢复后自动启用）" : "已启用"}</span><span class="text-xs text-gray-400">由程序自动控制</span></div>`)}
            ${field("自动录制", check("isAutoRecord", room.isAutoRecord, "开播后自动录制"))}
            ${field("转换 FLV 为 MP4", check("convertFlvToMp4", setting.convertFlvToMp4, "录制结束自动转码"))}
            ${field("弹幕记录", check("openSubtitle", setting.openSubtitle, "记录弹幕内容"))}
            ${field("循环监听", check("isLoop", setting.isLoop, "直播结束后继续监听"))}
            ${field("息知通知", `<input type="text" class="xiZhiUrl ${inputCls}" value="" title="${escapeHtml(setting.xiZhiUrl || "")}" placeholder="${escapeHtml(setting.xiZhiUrl || "未设置，留空表示不通知")}">`)}
            ${field("Cookie", `<input type="text" class="cookie ${inputCls}" value="" title="${escapeHtml(setting.cookie || "")}" placeholder="${escapeHtml(setting.cookie || "未设置，支持 file:cookie/xxx.txt 引用")}">`, true)}
        </div>
        <div class="flex items-center justify-end gap-2 pt-4 mt-1 border-t border-gray-200">
            <button type="button" class="deleteRoom-btn inline-flex items-center px-3 py-1.5 text-sm text-red-600 bg-white border border-gray-300 rounded-md hover:bg-red-50 transition-colors">
                <i class="fa fa-trash mr-1"></i>彻底删除
            </button>
            <button type="button" class="recoverRoom-btn inline-flex items-center px-3 py-1.5 text-sm text-white bg-success rounded-md hover:brightness-95 transition-all">
                <i class="fa fa-repeat mr-1"></i>恢复监听
            </button>
        </div>`;

    // 彻底删除
    wrapDom.querySelector("button.deleteRoom-btn").addEventListener("click", function () {
        deleteRoom(path);
    });

    // 恢复监听：把面板上的配置写回 room 对象再提交
    wrapDom.querySelector("button.recoverRoom-btn").addEventListener("click", async function () {
        const delay = wrapDom.querySelector("input.delayIntervalSec").value.trim();
        if (delay && (isNaN(delay) || Number(delay) < 10)) {
            notifyDialog("刷新频率必须是大于等于10的数字", {title:"填写有误", tone:"warn"});
            return;
        }
        room.setting = room.setting || {};
        if (delay) room.setting.delayIntervalSec = Number(delay);
        room.name = wrapDom.querySelector("input.room-name").value.trim();
        const savePath = wrapDom.querySelector("input.savePath").value.trim();
        // savePath 留空按 null 提交，merge 时沿用原来配置
        room.savePath = savePath.length > 0 ? savePath : null;
        // isEnable 由后端恢复时统一置为 true，这里不再提交
        room.setting.convertFlvToMp4 = wrapDom.querySelector("input.convertFlvToMp4").checked;
        room.setting.openSubtitle = wrapDom.querySelector("input.openSubtitle").checked;
        room.setting.isLoop = wrapDom.querySelector("input.isLoop").checked;
        room.setting.recordQuality = wrapDom.querySelector("select.recordQuality").value;
        room.isAutoRecord = wrapDom.querySelector("input.isAutoRecord").checked;
        const xiZhiUrl = wrapDom.querySelector("input.xiZhiUrl").value.trim();
        room.setting.xiZhiUrl = xiZhiUrl.length > 0 ? xiZhiUrl : null;
        const cookie = wrapDom.querySelector("input.cookie").value.trim();
        room.setting.cookie = cookie.length > 0 ? cookie : null;
        recoverRoom(path, room);
    });
}

async function recoverRoom(path, data) {
    const rootDom = document.getElementById("roomRecycleList");
    const key = rootDom.dataset.key;
    if (!(await confirmDialog("恢复后该房间会重新加入监听列表并自动启用，确定恢复吗？", {title:"恢复监听"}))) return;
    postRequest(`room-recycle/recover?actionKey=${encodeURIComponent(key)}&path=${encodeURIComponent(path)}`, data)
        .then(msg => {
            if (msg.success) {
                rootDom.dataset.currentPage = "1";
                renderRoomRecycleContent(rootDom, null, false);
                handleManualRefresh();
            } else {
                notifyDialog(msg.message, {title:"恢复失败", tone:"danger"});
            }
        })
        .catch(() => {
        });
}

async function deleteRoom(path) {
    const rootDom = document.getElementById("roomRecycleList");
    const key = rootDom.dataset.key;
    if (!(await confirmDialog("彻底删除后不可恢复，确定删除这条监听吗？", {title:"彻底删除", tone:"danger", danger:true, okText:"彻底删除"}))) return;
    getRequest("room-recycle/delete", {actionKey: key, path: path})
        .then(msg => {
            if (msg.success) {
                rootDom.dataset.currentPage = "1";
                renderRoomRecycleContent(rootDom, null, false);
            } else {
                notifyDialog(msg.message, {title:"删除失败", tone:"danger"});
            }
        })
        .catch(() => {
        });
}
