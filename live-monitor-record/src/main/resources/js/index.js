/**
 * 房间卡片渲染与操作分发
 * 约定：所有接口数据先 escapeHtml 再拼串；按钮统一走 data-act + 事件委托，
 * 避免在 onclick 里直接插值房间 key 造成属性逃逸。
 */

// 当前一次渲染的数据快照，供委托事件按 key 反查房间对象
const roomCache = new Map();

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
                <div class="bg-primary/5 p-4 border-b border-gray-100">
                    <div class="flex items-center justify-between">
                        <div class="flex items-center">
                            ${room.avatar ? `<img src="${safeUrl(room.avatar, "img/favicon.ico")}" alt="${escapeHtml(room.nickname || "未知主播")}" class="w-12 h-12 rounded-full object-cover mr-3" loading="lazy" referrerpolicy="no-referrer">` : `<div class="w-12 h-12 rounded-full bg-gray-200 flex items-center justify-center text-gray-400 mr-3"><i class="fa fa-user"></i></div>`}
                               <div>
                                    <h3 class="font-bold truncate max-w-[16rem]" title="${escapeHtml(room.nickname || "未知主播")}">${escapeHtml(room.nickname || "未知主播")}</h3>
                                    <div class="flex flex-wrap items-center text-xs text-gray-500 mt-1 gap-1">
                                        <span class="bg-primary/10 text-primary rounded text-center p-1 mx-1">
                                            ${escapeHtml(platformName[room.platform] || "未知平台")}
                                        </span>
                                        <span class="text-center">房间ID: ${escapeHtml(room.id || "未知")}</span>
                                        <span class="${living ? "bg-primary/10 text-gray-600" : "bg-gray-100 text-gray-600"} rounded text-center p-1 mx-1">
                                            更新于${formatDateSafe(room.updateTime)} | 频率: ${delay ? escapeHtml(delay) + "s" : "未知"}
                                        </span>
                                    </div>
                                </div>
                        </div>
                        <span class="status-badge ${living ? "bg-success/20 text-success" : "bg-gray-200 text-gray-600"} text-center py-2">
                            <i class="fa fa-circle text-xs mr-1 animate-pulse"></i>${living ? "直播中" : "未直播"}
                            <button type="button" data-act="refresh" data-key="${key}" title="刷新该房间" class="ml-2"><i class="fa fa-refresh"></i></button>
                        </span>
                    </div>
                </div>`;
}

// 创建直播中房间元素
function createLivingRoomElement(item, index) {
    const room = item.room || {};
    const recorder = item.recorder || {};
    const key = escapeHtml(item.key);

    // 创建元素
    const div = document.createElement("div");
    div.className = "bg-white rounded-xl overflow-hidden card-shadow hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1 opacity-0";
    div.style.animation = `fadeIn 0.3s ease-in-out ${Math.min(index, 10) * 0.05}s forwards`;

    // 平台特定信息网格
    let infoGridItems = `<div class="flex items-center">
                                    <i class="fa fa-calendar text-primary mr-1"></i>
                                    <span>开始时间: ${formatDateSafe(room.startTime)}</span>
                                </div>`;
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
    const isRecord = !!recorder.isRecord;
    div.innerHTML = `${createRoomHeadElement(item)}
                    <!-- 卡片内容 -->
                    <div class="p-4">
                        <!-- 直播封面与信息 -->
                        <div class="flex flex-col md:flex-row gap-4 mb-4" title="${escapeHtml(room.statistics || "")}">
                            <div class="relative flex-shrink-0">
                                ${room.cover ? `<img src="${safeUrl(room.cover)}" alt="直播封面" class="w-full h-full md:w-48 md:h-32 object-cover rounded-lg" loading="lazy" referrerpolicy="no-referrer">` : `<div class="w-full md:w-48 h-32 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400"><i class="fa fa-television text-3xl"></i></div>`}
                                <div class="absolute bottom-2 right-2 bg-black/60 text-white text-xs px-1.5 py-0.5 rounded viewer-count">
                                    ${formatNumber(displayViewers)} 观众
                                </div>
                            </div>

                            <div class="flex-1 min-w-0">
                                <h4 class="font-medium line-clamp-2 mb-2" title="${escapeHtml(room.title || "无标题")}">${escapeHtml(room.title || "无标题")}</h4>
                                <div class="grid grid-cols-2 gap-2 text-sm text-gray-600">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>

                        <!-- 录制信息 -->
                        <div class="bg-light rounded-lg p-3 mb-4 border border-gray-100">
                            <div class="flex items-center justify-between">
                                <h5 class="font-medium flex items-center ${isRecord ? "text-primary" : "text-gray-500"}">
                                    <i class="fa fa-film mr-1"></i> 录制状态: ${isRecord ? escapeHtml(recorder.definition || "-") : "未开启录制"}
                                </h5>
                                <span class="status-badge bg-primary/20 ${isRecord ? "text-primary" : "text-gray-500"}">
                                    <i class="fa fa-circle text-xs mr-1 animate-pulse"></i>${isRecord ? "正在录制" : "未录制"}
                                </span>
                            </div>
                            ${isRecord ? `<div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm text-gray-600 mt-2">
                                <div class="flex items-center">
                                    <i class="fa fa-clock-o text-gray-400 mr-1"></i>
                                    <span class="record-time">${escapeHtml(recordField(recorder.msg, 0))}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-file-video-o text-gray-400 mr-1"></i>
                                    <span>${escapeHtml(recordField(recorder.msg, 1))}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-film text-gray-400 mr-1"></i>
                                    <span>${escapeHtml(recordField(recorder.msg, 2))}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-signal text-gray-400 mr-1"></i>
                                    <span>${escapeHtml(recordField(recorder.msg, 3))}</span>
                                </div>
                            </div>

                            <div class="mt-2 text-xs text-gray-500 truncate" title="${escapeHtml(recorder.path || "无路径信息")}">
                                <i class="fa fa-folder-open mr-1"></i>
                                保存路径: ${escapeHtml(recorder.path || "无路径信息")}
                            </div>` : ""}
                        </div>
                        <!-- 操作按钮 -->
                        <div class="flex flex-wrap items-center justify-between gap-2">
                            <a href="${safeUrl(room.roomUrl)}" target="_blank" rel="noopener noreferrer"
                               class="inline-flex items-center px-3 py-1.5 bg-primary text-white rounded-lg text-sm hover:bg-primary/50 transition-colors">
                                <span><i class="fa fa-external-link mr-1"></i>进入直播间</span>
                            </a>
                            ${platVideoButton}
                            <button type="button" data-act="files" data-key="${key}" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-yellow-600 transition-colors">
                                <span><i class="fa fa-folder text-yellow-400 mr-1"></i>直播记录</span>
                            </button>
                            <button type="button" data-act="monitor" data-key="${key}" data-flag="false" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                              <span class="text-danger"><i class="fa fa-stop mr-1"></i>停止监控</span>
                            </button>
                            <button type="button" data-act="record" data-key="${key}" data-flag="${!isRecord}" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                                <span class="${isRecord ? "text-warning" : "text-success"}"><i class="fa ${isRecord ? "fa-pause" : "fa-play"} mr-1"></i>${isRecord ? "停止录制" : "开始录制"}</span>
                            </button>
                            <button type="button" data-act="setting" data-key="${key}" class="inline-flex items-center px-3 py-1.5 bg-gray-500 border text-white border-gray-300 rounded-lg text-sm hover:bg-gray-800 transition-colors">
                                <span class="text-white"> <i class="fa fa-cog mr-1"></i>设置</span>
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
    div.className = "bg-white rounded-xl overflow-hidden card-shadow hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1 opacity-0";
    div.style.animation = `fadeIn 0.3s ease-in-out ${Math.min(index, 10) * 0.05}s forwards`;

    // 平台特定信息网格
    let infoGridItems = `<div class="flex items-center">
                                    <i class="fa fa-key text-gray-400 mr-1"></i>
                                    <span>标识: ${escapeHtml(item.key || "未知")}</span>
                                </div>
                                <div class="flex items-center" title="${running ? formatDateSafe(item.startTime) : formatDateSafe(item.endTime)}">
                                    <i class="fa fa-refresh text-gray-400 mr-1"></i>
                                    监控状态:<span class="${running ? "bg-green-100" : "bg-red-100"} text-gray-600 px-2 py-0.5 rounded ml-1">${running ? "运行中" : "已停止"}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-bell text-gray-400 mr-1"></i>
                                    开播提醒:<span class="${noticeOn ? "bg-green-100" : "bg-red-100"} text-gray-600 px-2 py-0.5 rounded ml-1">${noticeOn ? "已开启" : "未开启"}</span>
                                </div>`;
    if (room.platform === "Bili") {
        // 哔哩哔哩房间显示粉丝数
        infoGridItems = `${infoGridItems}
                        <div class="flex items-center">
                            <i class="fa fa-users text-gray-400 mr-1"></i>
                            <span>粉丝: ${escapeHtml(room.followers || 0)}</span>
                        </div>`;
    }

    div.innerHTML = `${createRoomHeadElement(item)}
                    <!-- 卡片内容 -->
                    <div class="p-4">
                        <!-- 直播封面与信息 -->
                        <div class="flex flex-col md:flex-row gap-4 mb-4">
                            <div class="relative flex-shrink-0">
                                ${room.cover ? `<img src="${safeUrl(room.cover)}" alt="直播封面" class="w-full h-full md:w-48 md:h-32 object-cover rounded-lg" loading="lazy" referrerpolicy="no-referrer">` : `<div class="w-full md:w-48 h-32 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400"><i class="fa fa-television text-3xl"></i></div>`}
                            </div>
                            
                            <div class="flex-1 min-w-0">
                                <h4 class="font-medium text-gray-500 line-clamp-2 mb-2">当前未开启直播</h4>
                                <div class="grid grid-cols-2 gap-2 text-sm text-gray-600">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>
                        <!-- 操作按钮 -->
                        <div class="flex flex-wrap items-center justify-between gap-2">
                            <a href="${safeUrl(room.roomUrl)}" target="_blank" rel="noopener noreferrer" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-primary transition-colors">
                                <span><i class="fa fa-external-link mr-1"></i>访问直播间</span>
                            </a>
                            <button type="button" data-act="files" data-key="${key}" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-yellow-600 transition-colors">
                                <span><i class="fa fa-folder text-yellow-400 mr-1"></i>直播记录</span>
                            </button>
                            <button type="button" data-act="monitor" data-key="${key}" data-flag="${!running}" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                                <span class="${running ? "text-warning" : "text-success"}"><i class="fa ${running ? "fa-stop" : "fa-play"} mr-1"></i>${running ? "停止" : "开启"}监控</span>
                            </button>
                            <button type="button" data-act="del" data-key="${key}" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-red-200 transition-colors">
                                <span class="text-danger"><i class="fa fa-remove text-danger mr-1"></i>删除监听</span>
                            </button>
                            <button type="button" data-act="setting" data-key="${key}" class="inline-flex items-center px-3 py-1.5 bg-gray-500 border text-white border-gray-300 rounded-lg text-sm hover:bg-gray-800 transition-colors">
                                <span class="text-white"> <i class="fa fa-cog mr-1"></i>设置</span>
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
    let html = `<div class="relative inline-block group">
                        <button type="button" class="px-3 py-1.5 bg-primary text-white rounded-lg hover:bg-primary/50 flex items-center gap-1 text-sm cursor-pointer transition-colors">
                            ${escapeHtml(title)}
                            <i class="fa fa-chevron-down text-xs transition-transform group-hover:rotate-180"></i>
                        </button>
                        <div class="absolute bottom-full mb-1 w-44 bg-white border rounded shadow-md z-50 invisible opacity-0 group-hover:visible group-hover:opacity-100 transition-all duration-150">`;
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
        html += `<a href="https://zhangheng0805.github.io/FLVPlayer/?url=${encodeURIComponent(param)}" class="block px-3 py-1.5 hover:bg-gray-100 whitespace-nowrap" target="_blank" rel="noopener noreferrer" title="${escapeHtml(url)}">
                    <i class="fa fa-video-camera text-primary mr-2"></i>${escapeHtml(key)}
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
