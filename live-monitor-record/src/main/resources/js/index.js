//创建直播间卡片头部
function createRoomHeadElement(item) {
    const room = item.room;
    const living = room.living;
    return `<!-- 卡片头部 -->
                <div class="bg-primary/5 p-4 border-b border-gray-100">
                    <div class="flex items-center justify-between">
                        <div class="flex items-center">
                            ${room.avatar ? `<img src="${room.avatar}" alt="${room.nickname || "未知主播"}" class="w-10 h-10 rounded-full object-cover mr-3">` : `<div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-400 mr-3"><i class="fa fa-user"></i></div>`}
                               <div>
                                    <h3 class="font-bold">${room.nickname || "未知主播"}</h3>
                                    <div class="flex items-center text-xs text-gray-500 mt-0.5">
                                        <span class="bg-primary/10 text-primary px-2 py-0.5 rounded mr-2">
                                            ${platformName[room.platform] || "未知平台"}
                                        </span>
                                        <span>房间ID: ${room.id || "未知"}</span>
                                        <span class="${living ? "bg-primary/10 text-gray-600" : "bg-gray-100 text-gray-600"} px-2 py-0.5 rounded ml-1">
                                            更新于${formatDate(new Date(room.updateTime))} | 频率: ${room.setting.delayIntervalSec + "s" || "未知"}
                                        </span>
                                    </div>
                                </div>
                        </div>
                        <span class="status-badge ${living ? "bg-success/20 text-success" : "bg-gray-200 text-gray-600"}">
                            <i class="fa fa-circle text-xs mr-1 animate-pulse"></i>${living ? "直播中" : "未直播"}
                            <button onClick="actionRefresh('${item.key}')"  class="ml-2"><i class="fa fa-refresh"></i></button>
                        </span>
                    </div>
                </div>`;
}

// 创建直播中房间元素
function createLivingRoomElement(item, index) {
    const room = item.room;
    const recorder = item.recorder || {};

    // 创建元素
    const div = document.createElement("div");
    div.className = "bg-white rounded-xl overflow-hidden card-shadow hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1 opacity-0";
    div.style.animation = `fadeIn 0.3s ease-in-out ${index * 0.1}s forwards`;

    const startTime = room.startTime
        ? formatDate(new Date(room.startTime))
        : "未知";
    // 平台特定信息网格
    let infoGridItems = `<div class="flex items-center">
                                    <i class="fa fa-calendar text-primary mr-1"></i>
                                    <span>开始时间: ${startTime}</span>
                                </div>`;
    // 哔哩哔哩直播间数据
    if (room.platform === "Bili") {
        infoGridItems += createLivingBiliInfoGridItems(room);
    }
    // 抖音直播间数据
    else if (room.platform === "DouYin") {
        infoGridItems += createLivingDouYinInfoGridItems(room);
    }
    // 快手直播间数据
    else if (room.platform === "KuaiShou") {
    }

    // 确定当前显示的观众数（用于封面显示）
    let displayViewers = 0;
    //纯享播放按钮
    let platVideoButton = getPlayVideoSelectButton(
        "纯享播放",
        room.streams
    );
    if (room.platform === "Bili") {
        displayViewers = room.viewers || 0;
        platVideoButton = ""; //B站限制
    } else if (room.platform === "DouYin") {
        displayViewers = room.userCountStr || 0;
    }
    let isRecord = recorder.isRecord;
    div.innerHTML = `${createRoomHeadElement(item)}
                    <!-- 卡片内容 -->
                    <div class="p-4">
                        <!-- 直播封面与信息 -->
                        <div class="flex flex-col md:flex-row gap-4 mb-4" title="${room.statistics || ""}">
                            <div class="relative flex-shrink-0">
                                ${room.cover ? `<img src="${room.cover}" alt="直播封面" class="w-full h-full md:w-48 md:h-32 object-cover rounded-lg">` : `<div class="w-full md:w-48 h-32 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400"><i class="fa fa-television text-3xl"></i></div>`}
                                <div class="absolute bottom-2 right-2 bg-black/60 text-white text-xs px-1.5 py-0.5 rounded viewer-count">
                                    ${formatNumber(displayViewers)} 观众
                                </div>
                            </div>

                            <div class="flex-1">
                                <h4 class="font-medium line-clamp-2 mb-2">${room.title || "无标题"}</h4>
                                <div class="grid grid-cols-2 gap-2 text-sm text-gray-600">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>

                        <!-- 录制信息 -->
                        <div class="bg-light rounded-lg p-3 mb-4 border border-gray-100">
                            <div class="flex items-center justify-between">
                                <h5 class="font-medium flex items-center ${isRecord ? "text-primary" : "text-gray"}">
                                    <i class="fa fa-film mr-1"></i> 录制状态: ${isRecord ? recorder.definition : "未开启录制"}
                                </h5>
                                <span class="status-badge bg-primary/20 ${isRecord ? "text-primary" : "text-gray"}">
                                    <i class="fa fa-circle text-xs mr-1 animate-pulse"></i>${isRecord ? "正在录制" : "未录制"}
                                </span>
                            </div>
                            ${isRecord ? `<div class="grid grid-cols-2 md:grid-cols-[4fr,3fr,2fr,3fr] gap-2 text-sm text-gray-600 mt-2" >
                                <div class="flex items-center">
                                    <i class="fa fa-clock-o text-gray-400 mr-1"></i>
                                    <span class="record-time">${recorder.msg ? recorder.msg.split(" / ")[0] : "-"}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-file-video-o text-gray-400 mr-1"></i>
                                    <span>${recorder.msg ? recorder.msg.split(" / ")[1] : "-"}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-film text-gray-400 mr-1"></i>
                                    <span>${recorder.msg ? recorder.msg.split(" / ")[2] : "-"}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-signal text-gray-400 mr-1"></i>
                                    <span>${recorder.msg ? recorder.msg.split(" / ")[3] : "-"}</span>
                                </div>
                            </div>

                            <div class="mt-2 text-xs text-gray-500 truncate" title="${recorder.path || "无路径信息"}">
                                <i class="fa fa-folder-open mr-1"></i>
                                保存路径: ${recorder.path || "无路径信息"}
                            </div>` : ""}
                        </div>
                        <!-- 操作按钮 -->
                        <div class="flex flex-wrap items-center justify-between gap-2">
                            <a href="${room.roomUrl || "#"}" target="_blank"
                               class="inline-flex items-center px-3 py-1.5 bg-primary text-white rounded-lg text-sm hover:bg-primary/50 transition-colors">
                                <span><i class="fa fa-external-link mr-1"></i>进入直播间</span>
                            </a>
                            ${platVideoButton}
                            <button onclick="showFileSelectModal('${item.key}')" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-yellow-600 transition-colors">
                                <span><i class="fa fa-folder text-yellow-400 mr-1"></i>直播记录</span>
                            </button>
                            <button onclick="actionMonitor('${item.key}',false)" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                              <span class="text-danger"><i class="fa fa-stop mr-1"></i>停止监控</span>
                            </button>
                            <button onclick="actionRecord('${item.key}',${!isRecord})" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                                <span class="${isRecord ? "text-warning" : "text-success"}"><i class="fa ${isRecord ? "fa-pause" : "fa-play"} mr-1"></i>${isRecord ? "停止录制" : "开始录制"}</span>
                            </button>
                            <button class="inline-flex items-center px-3 py-1.5 bg-gray-500 border text-white border-gray-300 rounded-lg text-sm hover:bg-gray-800 transition-colors setting-toggle-btn">
                                <span class="text-white"> <i class="fa fa-cog mr-1"></i>设置</span>
                            </button>
                       </div>
                    </div>`;
    return div;
}

// 创建未直播房间元素
function createNotLivingRoomElement(item, index) {
    const room = item.room;
    const running = item.isRunning;
    // 创建元素
    const div = document.createElement("div");
    div.className = "bg-white rounded-xl overflow-hidden card-shadow hover:shadow-lg transition-all duration-300 transform hover:-translate-y-1 opacity-0";
    div.style.animation = `fadeIn 0.3s ease-in-out ${index * 0.1}s forwards`;

    // 平台特定信息网格
    let infoGridItems = `<div class="flex items-center">
                                    <i class="fa fa-key text-gray-400 mr-1"></i>
                                    <span>标识: ${item.key || "未知"}</span>
                                </div>
                                <div class="flex items-center" title="${formatDate(new Date(item.isRunning ? item.startTime : item.endTime))}">
                                    <i class="fa fa-refresh text-gray-400 mr-1"></i>
                                    监控状态:<span class="${running ? "bg-green-100" : "bg-red-100"} text-gray-600 px-2 py-0.5 rounded ml-1">${running ? "运行中" : "已停止"}</span>
                                </div>
                                <div class="flex items-center">
                                    <i class="fa fa-bell text-gray-400 mr-1"></i>
                                    开播提醒:<span class="${room.setting && room.setting.isNotice ? "bg-green-100" : "bg-red-100"} text-gray-600 px-2 py-0.5 rounded ml-1">${room.setting && room.setting.isNotice ? "已开启" : "未开启"}</span>
                                </div>`;
    if (room.platform === "Bili") {
        // 哔哩哔哩房间显示粉丝数
        infoGridItems = `${infoGridItems}
                        <div class="flex items-center">
                            <i class="fa fa-users text-gray-400 mr-1"></i>
                            <span>粉丝: ${room.followers || 0}</span>
                        </div>`;
    }

    div.innerHTML = `${createRoomHeadElement(item)}
                    <!-- 卡片内容 -->
                    <div class="p-4">
                        <!-- 直播封面与信息 -->
                        <div class="flex flex-col md:flex-row gap-4 mb-4">
                            <div class="relative flex-shrink-0">
                                ${room.cover ? `<img src="${room.cover}" alt="直播封面" class="w-full h-full md:w-48 md:h-32 object-cover rounded-lg">` : `<div class="w-full md:w-48 h-32 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400"><i class="fa fa-television text-3xl"></i></div>`}
                            </div>
                            
                            <div class="flex-1">
                                <h4 class="font-medium text-gray-500 line-clamp-2 mb-2">当前未开启直播</h4>
                                <div class="grid grid-cols-2 gap-2 text-sm text-gray-600">
                                    ${infoGridItems}
                                </div>
                            </div>
                        </div>
                        <!-- 操作按钮 -->
                        <div class="flex flex-wrap items-center justify-between gap-2">
                            <a href="${room.roomUrl || ""}" target="_blank" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-primary transition-colors">
                                <span><i class="fa fa-external-link mr-1"></i>访问直播间</span>
                            </a>
                            <button onclick="showFileSelectModal('${item.key}')" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-yellow-600 transition-colors">
                                <span><i class="fa fa-folder text-yellow-400 mr-1"></i>直播记录</span>
                            </button>
                            <button onclick="actionMonitor('${item.key}',${!running})" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-gray-500 transition-colors">
                                <span class="${running ? "text-warning" : "text-success"}"><i class="fa ${running ? "fa-stop" : "fa-play"} mr-1"></i>${running ? "停止" : "开启"}监控</span>
                            </button>
                            <button onclick="delRoom('${item.key}')" class="inline-flex items-center px-3 py-1.5 bg-white border border-gray-300 rounded-lg text-sm hover:bg-red-200 transition-colors">
                                <span class="text-danger"><i class="fa fa-remove text-danger mr-1"></i>删除监听</span>
                            </button>
                            <button class="inline-flex items-center px-3 py-1.5 bg-gray-500 border text-white border-gray-300 rounded-lg text-sm hover:bg-gray-800 transition-colors setting-toggle-btn">
                                <span class="text-white"> <i class="fa fa-cog mr-1"></i>设置</span>
                            </button>
                        </div>
                    </div>`;
    return div;
}

//纯享播放下拉选择按钮
function getPlayVideoSelectButton(title, options) {
    let html = `<div class="relative inline-block group">
                        <button class="px-3 py-1.5 bg-primary text-white rounded-lg hover:bg-primary/50 flex items-center gap-1 text-sm cursor-pointer transition-colors">
                            ${title}
                            <i class="fa fa-chevron-down text-xs transition-transform group-hover:rotate-180"></i>
                        </button>
                        <div class="absolute bottom-full mb-1 w-44 bg-white border rounded shadow-md z-50 invisible opacity-0 group-hover:visible group-hover:opacity-100 transition-all duration-150">`;
    Object.keys(options).forEach((key) => {
        html += `<a href="https://zhangheng0805.github.io/FLVPlayer/?url=${btoa(options[key])}" class="block px-3 py-1.5 hover:bg-gray-100 whitespace-nowrap" target="_blank">
                    <i class="fa fa-video-camera text-primary mr-2" title="${options[key]}"></i>${key}
                </a>`;
    });
    html += `</div></div>`;
    return html;
}

//绑定设置按钮
function bindSettingClick(item, dom) {
    const settingBtn = dom.querySelector("button.setting-toggle-btn");
    settingBtn.onclick = () => {
        actionSetting(item.key, item.room);
    };
}