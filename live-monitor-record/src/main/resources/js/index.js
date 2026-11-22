/**
 * 房间卡片渲染与操作分发
 * 约定：所有接口数据先 escapeHtml 再拼串；按钮统一走 data-act + 事件委托，
 * 避免在 onclick 里直接插值房间 key 造成属性逃逸。
 * 样式体系：卡片使用 lml- 前缀的类，样式定义在 index.html 的 <style> 中。
 */

// 当前一次渲染的数据快照，供委托事件按 key 反查房间对象
const roomCache = new Map();

// 平台 chip 配色（抖音靛 / B站粉 / 快手橙）
const PLATFORM_CHIP_CLASS = {
    DouYin: "is-douyin",
    Bili: "is-bili",
    KuaiShou: "is-ks",
};

// 卡片检索用的文本指纹：昵称、房间号、平台、标识、标题
function buildSearchText(item) {
    const room = item.room || {};
    return [
        room.nickname || "",
        room.id || "",
        platformName[room.platform] || "",
        room.platform || "",
        item.key || "",
        room.title || "",
    ].join(" ").toLowerCase();
}

// 记录信息形如 "时长 / 大小 / 状态 / 码率"，按下标安全取值
function recordField(msg, index) {
    if (!msg || typeof msg !== "string") return "-";
    const arr = msg.split(" / ");
    return arr[index] !== undefined && arr[index] !== "" ? arr[index] : "-";
}

//创建直播间卡片头部
function createRoomHeadElement(item) {
    const room = item.room || {};
    const living = !!room.living;
    const key = escapeHtml(item.key);
    const delay = room.setting && room.setting.delayIntervalSec;
    return `<!-- 卡片头部 -->
                <div class="lml-card-head">
                    <div class="lml-author">
                        ${room.avatar
                            ? `<img src="${safeUrl(room.avatar, "img/favicon.ico")}" alt="${escapeHtml(room.nickname || "未知主播")}" class="lml-avatar" loading="lazy" referrerpolicy="no-referrer">`
                            : `<div class="lml-avatar"><i class="fa fa-user"></i></div>`}
                        <div class="lml-author-meta">
                            <h3 class="lml-nickname" title="${escapeHtml(room.nickname || "未知主播")}">${escapeHtml(room.nickname || "未知主播")}</h3>
                            <div class="lml-chips">
                                <span class="lml-chip is-platform ${PLATFORM_CHIP_CLASS[room.platform] || ""}">${escapeHtml(platformName[room.platform] || "未知平台")}</span>
                                <span class="lml-chip">房间ID ${escapeHtml(room.id || "未知")}</span>
                                <span class="lml-chip">更新于 ${formatDateSafe(room.updateTime)} · 频率 ${delay ? escapeHtml(delay) + "s" : "未知"}</span>
                            </div>
                        </div>
                    </div>
                    <span class="lml-badge ${living ? "is-live" : "is-offline"}">
                        <i class="fa fa-circle"></i>${living ? "直播中" : "未直播"}
                        <button type="button" class="lml-mini-refresh" data-act="refresh" data-key="${key}" title="刷新该房间">
                            <i class="fa fa-refresh"></i>
                        </button>
                    </span>
                </div>`;
}

// 视频信息项（直播中卡片的信息网格）
function infoItem(icon, text, extraClass) {
    return `<div class="lml-info-item">
                <i class="fa ${icon}${extraClass ? " " + extraClass : ""}"></i>
                <span>${text}</span>
            </div>`;
}

// 创建直播中房间元素
function createLivingRoomElement(item, index) {
    const room = item.room || {};
    const recorder = item.recorder || {};
    const key = escapeHtml(item.key);

    // 创建元素
    const div = document.createElement("div");
    const isRecord = !!recorder.isRecord;
    div.className = "lml-card is-live" + (isRecord ? " is-recording" : "");
    div.dataset.key = item.key;
    div.dataset.search = buildSearchText(item);
    div.style.animationDelay = `${Math.min(index, 10) * 0.04}s`;

    // 平台特定信息网格
    let infoGridItems = infoItem("fa-calendar", `开始时间: ${formatDateSafe(room.startTime)}`, "is-accent");
    // 哔哩哔哩直播间数据
    if (room.platform === "Bili") {
        infoGridItems += createLivingBiliInfoGridItems(room);
    }
    // 抖音直播间数据
    else if (room.platform === "DouYin") {
        infoGridItems += createLivingDouYinInfoGridItems(room);
    }

    // 确定当前显示的观众数（用于封面显示）
    let displayViewers = 0;
    //纯享播放按钮
    let platVideoButton = getPlayVideoSelectButton("纯享播放", room.streams);
    if (room.platform === "Bili") {
        displayViewers = room.viewers || 0;
        platVideoButton = ""; //B站限制
    } else if (room.platform === "DouYin") {
        displayViewers = room.userCountStr || 0;
    }
    div.innerHTML = `${createRoomHeadElement(item)}
                    <div class="lml-card-body">
                        <!-- 直播封面与信息 -->
                        <div class="lml-main" title="${escapeHtml(room.statistics || "")}">
                            <div class="lml-cover">
                                ${room.cover
                                    ? `<img src="${safeUrl(room.cover)}" alt="直播封面" loading="lazy" referrerpolicy="no-referrer">`
                                    : `<div class="lml-cover-empty"><i class="fa fa-television"></i></div>`}
                                <div class="viewer-count">${formatNumber(displayViewers)} 观众</div>
                            </div>

                            <div class="lml-detail">
                                <h4 class="lml-room-title" title="${escapeHtml(room.title || "无标题")}">${escapeHtml(room.title || "无标题")}</h4>
                                <div class="lml-info-grid">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>

                        <!-- 录制信息 -->
                        <div class="lml-record ${isRecord ? "is-recording" : ""}">
                            <div class="lml-record-head">
                                <span class="lml-record-name">
                                    <i class="fa fa-film"></i>录制状态: ${isRecord ? escapeHtml(recorder.definition || "-") : "未开启录制"}
                                </span>
                                <span class="lml-badge ${isRecord ? "is-recording" : "is-offline"}">
                                    <i class="fa fa-circle"></i>${isRecord ? "正在录制" : "未录制"}
                                </span>
                            </div>
                            ${isRecord ? `<div class="lml-record-grid">
                                ${infoItem("fa-clock-o", `<span class="record-time">${escapeHtml(recordField(recorder.msg, 0))}</span>`)}
                                ${infoItem("fa-file-video-o", escapeHtml(recordField(recorder.msg, 1)))}
                                ${infoItem("fa-film", escapeHtml(recordField(recorder.msg, 2)))}
                                ${infoItem("fa-signal", escapeHtml(recordField(recorder.msg, 3)))}
                            </div>

                            <div class="lml-path" title="${escapeHtml(recorder.path || "无路径信息")}">
                                <i class="fa fa-folder-open"></i>
                                <span>保存路径: ${escapeHtml(recorder.path || "无路径信息")}</span>
                            </div>` : ""}
                        </div>
                        <!-- 操作按钮：常用动作带文字，低频动作收成图标 -->
                        <div class="lml-actions">
                            <a href="${safeUrl(room.roomUrl)}" target="_blank" rel="noopener noreferrer" class="lml-act is-primary">
                                <i class="fa fa-external-link"></i><span>进入直播间</span>
                            </a>
                            ${platVideoButton}
                            <button type="button" data-act="files" data-key="${key}" class="lml-act">
                                <i class="fa fa-folder is-folder"></i><span>直播记录</span>
                            </button>
                            <span class="lml-actions-spacer"></span>
                            <button type="button" data-act="record" data-key="${key}" data-flag="${!isRecord}"
                                    class="lml-act ${isRecord ? "is-off" : "is-on"}">
                                <i class="fa ${isRecord ? "fa-stop" : "fa-video-camera"}"></i>
                                <span>${isRecord ? "停止录制" : "开始录制"}</span>
                            </button>
                            <button type="button" data-act="monitor" data-key="${key}" data-flag="false"
                                    class="lml-act is-icon is-danger" title="停止监控">
                                <i class="fa fa-stop-circle"></i>
                            </button>
                            <button type="button" data-act="setting" data-key="${key}"
                                    class="lml-act is-icon" title="房间设置">
                                <i class="fa fa-cog"></i>
                            </button>
                        </div>
                    </div>`;
    return div;
}

// 创建未直播房间元素
function createNotLivingRoomElement(item, index) {
    const room = item.room || {};
    const running = !!item.isRunning;
    const key = escapeHtml(item.key);
    const noticeOn = !!(room.setting && room.setting.isNotice);
    // 创建元素
    const div = document.createElement("div");
    div.className = "lml-card is-offline";
    div.dataset.key = item.key;
    div.dataset.search = buildSearchText(item);
    div.style.animationDelay = `${Math.min(index, 10) * 0.04}s`;

    // 平台特定信息网格
    let infoGridItems = `<div class="lml-info-item">
                            <i class="fa fa-key"></i>
                            <span>标识: ${escapeHtml(item.key || "未知")}</span>
                        </div>
                        <div class="lml-info-item" title="${running ? formatDateSafe(item.startTime) : formatDateSafe(item.endTime)}">
                            <i class="fa fa-refresh"></i>
                            <span>监控状态:<span class="lml-flagnum ${running ? "is-on" : "is-off"}">${running ? "运行中" : "已停止"}</span></span>
                        </div>
                        <div class="lml-info-item">
                            <i class="fa fa-bell"></i>
                            <span>开播提醒:<span class="lml-flagnum ${noticeOn ? "is-on" : "is-off"}">${noticeOn ? "已开启" : "未开启"}</span></span>
                        </div>`;
    if (room.platform === "Bili") {
        // 哔哩哔哩房间显示粉丝数
        infoGridItems = `${infoGridItems}
                        ${infoItem("fa-users", `粉丝: ${escapeHtml(room.followers || 0)}`)}`;
    }

    div.innerHTML = `${createRoomHeadElement(item)}
                    <!-- 卡片内容 -->
                    <div class="lml-card-body">
                        <!-- 直播封面与信息 -->
                        <div class="lml-main">
                            <div class="lml-cover">
                                ${room.cover
                                    ? `<img src="${safeUrl(room.cover)}" alt="直播封面" loading="lazy" referrerpolicy="no-referrer">`
                                    : `<div class="lml-cover-empty"><i class="fa fa-television"></i></div>`}
                            </div>

                            <div class="lml-detail">
                                <h4 class="lml-room-title is-offline">当前未开启直播</h4>
                                <div class="lml-info-grid">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>
                        <!-- 操作按钮 -->
                        <div class="lml-actions">
                            <a href="${safeUrl(room.roomUrl)}" target="_blank" rel="noopener noreferrer" class="lml-act">
                                <i class="fa fa-external-link"></i><span>访问直播间</span>
                            </a>
                            <button type="button" data-act="files" data-key="${key}" class="lml-act">
                                <i class="fa fa-folder is-folder"></i><span>直播记录</span>
                            </button>
                            <span class="lml-actions-spacer"></span>
                            <button type="button" data-act="monitor" data-key="${key}" data-flag="${!running}"
                                    class="lml-act ${running ? "is-off" : "is-on"}">
                                <i class="fa ${running ? "fa-stop" : "fa-play"}"></i>
                                <span>${running ? "停止" : "开启"}监控</span>
                            </button>
                            <button type="button" data-act="setting" data-key="${key}"
                                    class="lml-act is-icon" title="房间设置">
                                <i class="fa fa-cog"></i>
                            </button>
                            <button type="button" data-act="del" data-key="${key}"
                                    class="lml-act is-icon is-danger" title="删除监听">
                                <i class="fa fa-trash-o"></i>
                            </button>
                        </div>
                    </div>`;
    return div;
}

//纯享播放下拉选择按钮
function getPlayVideoSelectButton(title, options) {
    if (!options || typeof options !== "object" || Object.keys(options).length === 0) {
        return "";
    }
    let html = `<div class="lml-drop">
                        <button type="button" class="lml-drop-toggle">
                            ${escapeHtml(title)}
                            <i class="fa fa-chevron-down"></i>
                        </button>
                        <div class="lml-drop-menu">`;
    Object.keys(options).forEach((key) => {
        const url = options[key];
        if (!url) return;
        // FLVPlayer 优先按 base64 解析，非 ASCII 或编码失败时退回明文地址
        let param = url;
        try {
            param = btoa(url);
        } catch (e) {
            param = url;
        }
        html += `<a href="https://zhangheng0805.github.io/FLVPlayer/?url=${encodeURIComponent(param)}" class="lml-drop-item" target="_blank" rel="noopener noreferrer" title="${escapeHtml(url)}">
                    <i class="fa fa-video-camera"></i><span>${escapeHtml(key)}</span>
                </a>`;
    });
    html += `</div></div>`;
    return html;
}

/**
 * 卡片按钮统一事件委托：按钮只需带 data-act（可选 data-key/data-flag）
 * 房间对象从 roomCache 取，避免把 key 拼进 onclick 属性
 */
function initRoomCardActions() {
    document.addEventListener("click", function (event) {
        const btn = event.target.closest("[data-act]");
        if (!btn) return;
        const act = btn.dataset.act;
        const key = btn.dataset.key;
        const item = roomCache.get(key);
        switch (act) {
            case "refresh":
                actionRefresh(key);
                break;
            case "monitor":
                actionMonitor(key, btn.dataset.flag === "true");
                break;
            case "record":
                actionRecord(key, btn.dataset.flag === "true");
                break;
            case "del":
                delRoom(key);
                break;
            case "files":
                showFileSelectModal(key);
                break;
            case "setting":
                if (item) actionSetting(key, item.room);
                break;
        }
    });
}

// 记录本次渲染的房间，供事件委托查询
function cacheRooms(list) {
    roomCache.clear();
    (list || []).forEach((item) => roomCache.set(item.key, item));
}

/* ==========================================================================
 * 直播间模糊检索
 * - 子串命中优先；未命中时按「字符顺序包含」做子序列匹配（fzf 式）
 * - 空格分隔多个关键词时要求全部命中
 * - 只做 DOM 显隐，不重新请求、不重建卡片，定时刷新后自动保持过滤状态
 * ========================================================================== */

let roomFilterQuery = "";

// 单个关键词与单条文本的模糊匹配
function fuzzyMatch(text, query) {
    if (!query) return true;
    if (!text) return false;
    if (text.indexOf(query) > -1) return true;
    let qi = 0;
    for (let i = 0; i < text.length && qi < query.length; i++) {
        if (text[i] === query[qi]) qi++;
    }
    return qi === query.length;
}

function matchSearchText(text, tokens) {
    if (!tokens.length) return true;
    return tokens.every((t) => fuzzyMatch(text, t));
}

function setRoomFilter(query) {
    roomFilterQuery = query || "";
    applyRoomFilter();
}

// 按当前关键词过滤两个房间列表，并更新分区计数
function applyRoomFilter() {
    const tokens = String(roomFilterQuery || "")
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean);

    [
        { container: "living-rooms-container", count: "living-count" },
        { container: "not-living-rooms-container", count: "not-living-count" },
    ].forEach(function (section) {
        const box = document.getElementById(section.container);
        const countEl = document.getElementById(section.count);
        if (!box) return;

        const cards = Array.from(box.querySelectorAll(".lml-card"));
        let total = 0;
        let hit = 0;
        cards.forEach(function (card) {
            total++;
            const matched = matchSearchText(card.dataset.search || "", tokens);
            card.classList.toggle("is-hidden", !matched);
            if (matched) hit++;
        });

        if (countEl) {
            countEl.textContent = tokens.length
                ? `命中 ${hit} / ${total} 个房间`
                : `${total} 个房间`;
        }

        // 有数据但全部被过滤掉时给个提示（结构里的常驻空态不参与）
        let emptyTip = box.querySelector(".lml-filter-empty");
        // 注意：过滤提示自身也带 lml-empty 类，判断常驻空态时必须把它排除，
        // 否则一旦创建过提示，下一轮就会被当成“页面本身暂无房间”而提前 return，提示永远清不掉。
        const isEmptyState = box.querySelector(".lml-empty:not(.lml-filter-empty)");
        if (isEmptyState) return; // 页面本身已经显示“暂无房间”，无需再加一层

        if (tokens.length && total > 0 && hit === 0) {
            if (!emptyTip) {
                emptyTip = document.createElement("div");
                emptyTip.className = "lml-empty lml-filter-empty";
                box.appendChild(emptyTip);
            }
            emptyTip.textContent = `没有匹配「${roomFilterQuery.trim()}」的直播间`;
            emptyTip.classList.remove("is-hidden");
        } else if (emptyTip) {
            emptyTip.classList.add("is-hidden");
        }
    });
}

// 绑定搜索框：输入即过滤，✕ 清空，Esc 清空
function initRoomSearch() {
    const input = document.getElementById("room-search");
    const wrap = document.getElementById("room-search-wrap");
    const clearBtn = document.getElementById("room-search-clear");
    if (!input) return;

    let timer = null;
    input.addEventListener("input", function () {
        const value = input.value;
        if (wrap) wrap.classList.toggle("has-value", !!value);
        if (timer) clearTimeout(timer);
        timer = setTimeout(function () {
            setRoomFilter(value);
        }, 120);
    });

    function reset() {
        // 取消还没执行的防抖任务，否则清空后旧关键词可能被重新应用
        if (timer) {
            clearTimeout(timer);
            timer = null;
        }
        input.value = "";
        if (wrap) wrap.classList.remove("has-value");
        setRoomFilter("");
        input.focus();
    }

    if (clearBtn) clearBtn.addEventListener("click", reset);
    input.addEventListener("keydown", function (e) {
        if (e.key === "Escape") reset();
    });
}
