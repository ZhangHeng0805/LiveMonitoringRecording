/**
 * 操作秘钥验证弹窗
 * @param onSubmit 校验通过回调，入参为秘钥
 * @param onCancel 取消回调（Promise 场景需要）
 */
function showActionBeforeModal(onSubmit, onCancel) {
    setModalContent(
        "操作验证",
        `<div id="inputContainer" class="mb-4">
                <label for="actionKey" class="block text-sm font-medium text-gray-700 mb-1">操作秘钥</label>
                <input type="password"
                    value="${escapeHtml((window.localStorage && window.localStorage.getItem("actionKey")) || '')}"
                    id="actionKey"
                    class="input-field"
                    placeholder="请输入操作秘钥">
              </div>
             <div id="isRememberContainer" class="mb-0">
                  <label class="flex items-center space-x-2 cursor-pointer">
                    <input type="checkbox"
                        id="isRemember"
                        class="h-4 w-4 text-primary focus:ring-primary" checked>
                    <span class="text-sm font-medium text-gray-700">记住秘钥</span>
                  </label>
                </div>
            <p id="inputError" class="mt-1 text-sm text-red-600 hidden"></p>`,
        [
            {
                text: "取消",
                className:
                    "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                onClick: () => {
                    closeModal();
                    if (typeof onCancel === "function") onCancel();
                },
            },
            {
                text: "确定",
                className:
                    "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                onClick: () => {
                    let key = document.getElementById("actionKey").value;
                    let remember = document.getElementById("isRemember").checked
                    // console.log("秘钥", val);
                    if (key.length < 1) {
                        let inputError = document.getElementById("inputError");
                        inputError.textContent = "输入内容不能为空！";
                        inputError.classList.remove("hidden");
                        return;
                    }
                    if (remember) {
                        if (window.localStorage) {
                            window.localStorage.setItem("actionKey", key);
                        }
                    } else {
                        if (window.localStorage) {
                            window.localStorage.removeItem("actionKey");
                        }
                    }
                    closeModal();
                    onSubmit(key);
                },
            },
        ]
    );
}

// 供 util.js 换取接口令牌时弹出秘钥输入的 Promise 包装
// 注意：右上角 ✕ 关闭弹窗时也必须 resolve(null)，否则 Promise 永久 pending，
// apiTokenTask 会被一直复用，后续请求既不发出去也不再弹验证框。
window.promptActionKey = function () {
    return new Promise(function (resolve) {
        let settled = false;

        function finish(value) {
            if (settled) return;
            settled = true;
            if (typeof window.offModalDismiss === "function") {
                window.offModalDismiss(onDismiss);
            }
            resolve(value);
        }

        function onDismiss() {
            finish(null);
        }

        if (typeof window.onModalDismiss === "function") {
            window.onModalDismiss(onDismiss);
        }

        showActionBeforeModal(
            function (key) {
                finish(key);
            },
            function () {
                finish(null);
            }
        );
    });
};

//操作结果弹窗
function showActionAfterModal(msg, callback) {
    setModalContent(
        msg.title || "操作结果",
        `<p class="${msg.success ? "text-green-600" : "text-red-600"} text-center">${msg.message}</p>`,
        [
            {
                text: "知道了",
                className:
                    "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                onClick: () => {
                    closeModal();
                    // 延迟300ms执行回调，匹配弹窗关闭动画时长
                    if (typeof callback === "function") {
                        setTimeout(callback, 500);
                    }
                },
            },
        ]
    );
}


//房间刷新
function actionRefresh(key) {
    refreshDebounce(key);
}

const refreshDebounce = debounce(function (key) {
    actionRequest("refresh", {key: key}).then((data) => {
        data.title = "刷新操作结果";
        showActionAfterModal(data, () => {
            if (data.success) {
                handleManualRefresh();
            }
        });
    });
}, 500, true);

async function actionRequest(path, params, timeoutMs) {
    return getRequest(`action/${path}`, params, timeoutMs);
}

//直播监听操作
function actionMonitor(roomKey, isOpen) {
    showActionBeforeModal((key) => {
        console.log("操作监听", roomKey, isOpen, key);
        actionRequest("monitor", {
            key: roomKey,
            flag: isOpen,
            actionKey: key,
        }).then((data) => {
            data.title = "监听操作结果";
            showActionAfterModal(data, () => {
                if (data.success) {
                    setTimeout(() => {
                        handleManualRefresh();
                    }, 1000);
                }
            });

        });
    });
}

//直播录制操作
function actionRecord(roomKey, isRecord) {
    showActionBeforeModal((key) => {
        console.log("操作录制", roomKey, isRecord, key);
        actionRequest("record", {
            key: roomKey,
            flag: isRecord,
            actionKey: key,
        }).then((data) => {
            data.title = "录制操作结果";
            showActionAfterModal(data, () => {
                if (data.success) {
                    handleManualRefresh();
                }
            });
        });
    });
}

//删除
function delRoom(roomKey) {
    showActionBeforeModal((key) => {
        actionRequest("delRoom", {
            key: roomKey,
            actionKey: key,
        }).then((data) => {
            data.title = "操作结果";
            showActionAfterModal(data, () => {
                if (data.success) {
                    handleManualRefresh();
                }
            });
        });
    });
}

//直播间新增操作
/**
 * 从解析结果中提取抖音直播间号，兼容三种返回形态：
 * 1. 纯数字房间号：209309564
 * 2. 标准直播间地址：https://live.douyin.com/209309564
 * 3. 带追踪参数的直播间地址：https://live.douyin.com/209309564?room_id=xxx&enter_from=web_share_link...
 * 注意只取 live.douyin.com 路径上的数字，不使用 room_id 查询参数（那是内部房间号，和直播间号不同）
 */
function extractDouYinRoomId(raw) {
    const text = String(raw === null || raw === undefined ? "" : raw).trim();
    if (/^\d{5,20}$/.test(text)) return text;
    const liveMatch = text.match(/live\.douyin\.com\/(\d{5,20})/);
    if (liveMatch) return liveMatch[1];
    return "";
}

function addRoom() {
    showActionBeforeModal((key) => {
        let submitting = false;

        setModalContent(
            "新增直播监听",
            `${SETTING_MODAL_STYLE}
            <div class="setx setx-2col">
              <div class="setx-col">
                <!-- 直播间信息 -->
                <section class="setx-card">
                  <div class="setx-title"><i class="fa fa-television"></i>直播间信息</div>

                  <div class="setx-field">
                    <div class="setx-label">
                      <span>直播间 ID <span class="setx-required">*</span></span>
                      <span class="setx-hint-inline">抖音可粘贴分享链接</span>
                    </div>
                    <div class="setx-inline">
                      <input type="text" id="roomID" class="setx-input" style="max-width:none"
                             placeholder="例如 172730382391，或粘贴分享链接">
                      <button type="button" id="roomIDParseBtn" class="setx-btn" disabled>
                        <i class="fa fa-magic"></i><span>解析</span>
                      </button>
                    </div>
                    <p class="setx-help">抖音分享口令 / 短链点「解析」自动提取房间号；哔哩哔哩、快手直接填房间号。</p>
                  </div>

                  <div class="setx-field">
                    <div class="setx-label"><span>直播间名称</span></div>
                    <input type="text" id="roomName" class="setx-input"
                           placeholder="选填，留空则首次刷新后自动取昵称">
                  </div>

                  <div class="setx-field">
                    <div class="setx-label"><span>直播间平台</span></div>
                    <select id="platform" class="setx-input"></select>
                  </div>
                </section>

                <!-- 通知与凭据：默认折叠 -->
                <details class="setx-card setx-collapse">
                  <summary class="setx-title">
                    <i class="fa fa-bell-o"></i>通知与凭据
                    <span class="setx-hint-inline">选填，可稍后改</span>
                    <i class="fa fa-angle-down setx-collapse-arrow"></i>
                  </summary>
                  <div class="setx-collapse-body">
                    <div class="setx-field">
                      <div class="setx-label"><span>息知通知地址</span></div>
                      <input type="text" id="xiZhiUrl" class="setx-input" placeholder="https://xz.qqoq.net/xxx.send">
                      <p class="setx-help">开播 / 下播推送地址，留空表示不推送。</p>
                    </div>
                    <div class="setx-field">
                      <div class="setx-label"><span>直播间 Cookie</span></div>
                      <textarea id="cookieValue" class="setx-textarea" rows="3"
                                placeholder="留空沿用全局 Cookie，或填 file:cookie/douyin-cookie.txt"></textarea>
                      <p class="setx-help">需要登录态的高画质/粉丝弹幕时再填；支持 <code>file:</code> 文件引用。</p>
                    </div>
                  </div>
                </details>

              </div>

              <div class="setx-col">
                <!-- 行为开关 -->
                <section class="setx-card">
                  <div class="setx-title"><i class="fa fa-sliders"></i>行为开关</div>
                  <div class="setx-grid">
                    <label class="setx-toggle">
                      <input type="checkbox" id="isRecord" checked>
                      <div>
                        <div class="setx-toggle-name">自动录制</div>
                        <div class="setx-toggle-desc">检测到开播立即开始录制</div>
                      </div>
                    </label>
                    <label class="setx-toggle">
                      <input type="checkbox" id="openSubtitle">
                      <div>
                        <div class="setx-toggle-name">弹幕记录</div>
                        <div class="setx-toggle-desc">录制时同步保存直播间弹幕</div>
                      </div>
                    </label>
                    <label class="setx-toggle">
                      <input type="checkbox" id="convertFlvToMp4" checked>
                      <div>
                        <div class="setx-toggle-name">转 MP4</div>
                        <div class="setx-toggle-desc">录制结束后自动 FLV → MP4</div>
                      </div>
                    </label>
                    <label class="setx-toggle">
                      <input type="checkbox" id="isLoop" checked>
                      <div>
                        <div class="setx-toggle-name">循环监听</div>
                        <div class="setx-toggle-desc">下播后继续保持监听等待下次开播</div>
                      </div>
                    </label>
                  </div>
                </section>

                <!-- 监听频率 -->
                <section class="setx-card">
                  <div class="setx-title"><i class="fa fa-clock-o"></i>监听频率</div>
                  <div class="setx-inline">
                    <input type="number" id="delayIntervalSec" class="setx-input" min="10" step="1" value="30">
                    <span class="setx-unit">秒</span>
                  </div>
                  <p class="setx-help">最小 10 秒，默认 30 秒。数值越大越省资源，代价是开播检测有延迟。</p>
                </section>

              </div>

              <p id="inputError" class="setx-error setx-span hidden"></p>
            </div>`,
            [
                {
                    text: "取消",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: closeModal,
                },
                {
                    text: "添加监听",
                    className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: () => {
                        if (submitting) return;
                        const errorElement = document.getElementById("inputError");
                        const roomIDInput = document.getElementById("roomID").value.trim();
                        const roomNameInput = document.getElementById("roomName").value.trim();
                        const platformSelect = document.getElementById("platform").value;
                        const delayRaw = document.getElementById("delayIntervalSec").value.trim();
                        const delayIntervalSec = delayRaw === "" ? null : Number(delayRaw);

                        const showError = function (text) {
                            errorElement.textContent = text;
                            errorElement.classList.remove("hidden");
                            const idInput = document.getElementById("roomID");
                            if (idInput) idInput.focus();
                        };

                        if (roomIDInput.length < 1) {
                            return showError("直播间 ID 不能为空");
                        }
                        if (/^http/i.test(roomIDInput)) {
                            return showError("这里需要房间号，请先点右侧「解析链接」把链接转成房间号");
                        }
                        if (delayRaw !== "" && (isNaN(delayIntervalSec) || delayIntervalSec < 10)) {
                            return showError("刷新频率必须是大于等于 10 的整数");
                        }
                        errorElement.classList.add("hidden");

                        submitting = true;
                        const saveBtn = modalFooter.querySelector("button:last-child");
                        if (saveBtn) {
                            saveBtn.disabled = true;
                            saveBtn.textContent = "添加中…";
                        }
                        const restore = function () {
                            submitting = false;
                            if (saveBtn) {
                                saveBtn.disabled = false;
                                saveBtn.textContent = "添加监听";
                            }
                        };

                        postRequest("action/addRoom?actionKey=" + encodeURIComponent(key), {
                            id: roomIDInput,
                            name: roomNameInput.length > 0 ? roomNameInput : null,
                            platform: platformSelect,
                            isAutoRecord: document.getElementById("isRecord").checked,
                            setting: {
                                delayIntervalSec: delayIntervalSec,
                                convertFlvToMp4: document.getElementById("convertFlvToMp4").checked,
                                openSubtitle: document.getElementById("openSubtitle").checked,
                                isLoop: document.getElementById("isLoop").checked,
                                cookie: document.getElementById("cookieValue").value.trim() || null,
                                xiZhiUrl: document.getElementById("xiZhiUrl").value.trim() || null,
                            },
                        }).then((msg) => {
                            restore();
                            if (!msg.success) {
                                showError(msg.message || "添加失败，请稍后重试");
                                return;
                            }
                            closeModal();
                            showActionAfterModal(msg, () => handleManualRefresh());
                        }).catch((err) => {
                            restore();
                            showError("添加失败：" + (err && err.message ? err.message : err));
                        });
                    },
                },
            ],
            { maxWidth: "min(56rem, 94vw)", hint: "房间会立即加入监听列表" }
        );

        // 平台下拉
        const platformSelectDom = document.getElementById("platform");
        Object.entries(platformName).forEach(([key, text]) => {
            const option = document.createElement("option");
            option.value = key;
            option.textContent = text;
            platformSelectDom.appendChild(option);
        });

        const roomIDDom = document.getElementById("roomID");
        const parseBtn = document.getElementById("roomIDParseBtn");

        // 输入时：本地能直读房间号的链接直接提取，分享口令才交给后端解析
        function syncParseBtn() {
            const value = roomIDDom.value.trim();
            const isLiveLink = /live\.douyin\.com\/\d+/.test(value);
            if (isLiveLink) {
                const match = value.match(/live\.douyin\.com\/(\d+)/);
                if (match) {
                    roomIDDom.value = match[1];
                    setPlatformByValue("DouYin");
                    parseBtn.disabled = true;
                    parseBtn.title = "已从链接中提取房间号，无需再解析";
                    return;
                }
            }
            // 纯数字本身就是房间号，不让点解析，避免白跑一次浏览器
            if (/^\d{5,20}$/.test(value)) {
                parseBtn.disabled = true;
                parseBtn.title = "这已经是直播间号，无需解析";
                return;
            }
            // 只有含 douyin 且有内容时才需要解析
            const needParse = value.indexOf("douyin") > -1 && value.indexOf("http") > -1;
            parseBtn.disabled = !needParse;
            parseBtn.title = needParse ? "解析分享链接为直播间号" : "粘贴抖音分享链接后可解析";
        }

        roomIDDom.addEventListener("input", debounce(syncParseBtn, 200));

        function setPlatformByValue(value) {
            if (platformSelectDom && value && platformName[value]) {
                platformSelectDom.value = value;
            }
        }

        parseBtn.addEventListener("click", async function () {
            if (this.disabled) return;
            const roomIDBefore = roomIDDom.value.trim();
            const errEl = document.getElementById("inputError");
            const showParseError = function (text) {
                if (!errEl) return;
                errEl.textContent = text;
                errEl.classList.remove("hidden");
            };
            const clearError = function () {
                if (errEl) {
                    errEl.textContent = "";
                    errEl.classList.add("hidden");
                }
            };

            // 本地先校验输入，避免把纯数字/无效内容发给后端跑一次浏览器
            if (roomIDBefore.length < 1) {
                return showParseError("请先粘贴抖音直播间分享链接");
            }
            if (/^\d{5,20}$/.test(roomIDBefore)) {
                return showParseError("这已经是直播间号，无需解析，直接点「添加监听」即可");
            }
            if (!/http/i.test(roomIDBefore)) {
                return showParseError("没有识别到链接，请粘贴完整的抖音分享文案或 v.douyin.com 短链");
            }

            const icon = this.querySelector("i");
            const label = this.querySelector("span") || this;
            const origText = label.textContent;
            this.disabled = true;
            this.classList.add("is-loading");
            if (icon) icon.classList.add("fa-spin");
            if (label !== this) label.textContent = "解析中…";
            clearError();
            try {
                // 解析要启动无头浏览器跳转抖音，实测 30 秒左右，客户端超时必须比服务端宽，否则会先超时把结果丢掉
                const data = await actionRequest("parseDouYinLink", {shareLink: roomIDBefore}, 60000);
                const raw = data && data.data !== null && data.data !== undefined ? String(data.data).trim() : "";
                if (!data || !data.success) {
                    showParseError((data && data.message) || "链接解析失败，请确认是抖音直播间分享链接");
                    return;
                }
                // 结果兼容三种形态：纯数字房间号 / live.douyin.com/{id} / 带查询参数的直播间地址
                const parsed = extractDouYinRoomId(raw);
                if (!parsed) {
                    showParseError("解析结果无效，未能得到直播间号" + (raw ? "（返回：" + raw + "）" : ""));
                    return;
                }
                roomIDDom.value = parsed;
                setPlatformByValue("DouYin");
                parseBtn.disabled = true;
                clearError();
            } catch (e) {
                if (e && e.timeout) {
                    showParseError("解析超时（60 秒），后端仍在加载抖音页面，请稍等几秒再点一次「解析」");
                } else {
                    showParseError("链接解析失败：" + (e && e.message ? e.message : e));
                }
            } finally {
                this.classList.remove("is-loading");
                if (icon) icon.classList.remove("fa-spin");
                if (label !== this) label.textContent = origText;
                syncParseBtn();
                roomIDDom.focus();
            }
        });

        syncParseBtn();
        roomIDDom.focus();
    });
}

//直播间设置操作
function actionSetting(roomKey, room) {
    let setting = room.setting || {};
    const platformText = (typeof platformName !== "undefined" && platformName[room.platform]) || room.platform || "";
    const roomHint = [platformText, roomKey].filter(Boolean).join(" · ");
    let submitting = false;

    showActionBeforeModal((key) => {
        const delayValue = setting.delayIntervalSec === null || setting.delayIntervalSec === undefined ? "" : setting.delayIntervalSec;
        const hasXiZhi = !!setting.xiZhiUrl;
        const hasCookie = !!setting.cookie;

        setModalContent(
            `${room.nickname || roomKey} 监听设置`,
            `${SETTING_MODAL_STYLE}
            <div class="setx">
              <!-- 监听频率 -->
              <section class="setx-card">
                <div class="setx-title"><i class="fa fa-clock-o"></i>监听频率</div>
                <div class="setx-inline">
                  <input type="number" id="delayIntervalSec" class="setx-input" min="10" step="1"
                         value="${delayValue}" placeholder="留空沿用全局设置">
                  <span class="setx-unit">秒</span>
                </div>
                <p class="setx-help">最小 10 秒。留空表示沿用全局 <code>monitor.delayIntervalSec</code>，数值越大越省资源。</p>
              </section>

              <!-- 行为开关 -->
              <section class="setx-card">
                <div class="setx-title"><i class="fa fa-sliders"></i>行为开关</div>
                <div class="setx-grid">
                  <label class="setx-toggle">
                    <input type="checkbox" id="openSubtitle" ${setting.openSubtitle ? "checked" : ""}>
                    <div>
                      <div class="setx-toggle-name">弹幕记录</div>
                      <div class="setx-toggle-desc">录制时同步保存直播间弹幕</div>
                    </div>
                  </label>
                  <label class="setx-toggle">
                    <input type="checkbox" id="isAutoRecord" ${setting.isAutoRecord ? "checked" : ""}>
                    <div>
                      <div class="setx-toggle-name">自动录制</div>
                      <div class="setx-toggle-desc">检测到开播立即开始录制</div>
                    </div>
                  </label>
                  <label class="setx-toggle">
                    <input type="checkbox" id="convertFlvToMp4" ${setting.convertFlvToMp4 ? "checked" : ""}>
                    <div>
                      <div class="setx-toggle-name">转 MP4</div>
                      <div class="setx-toggle-desc">录制结束后自动 FLV → MP4</div>
                    </div>
                  </label>
                  <label class="setx-toggle">
                    <input type="checkbox" id="isLoop" ${setting.isLoop ? "checked" : ""}>
                    <div>
                      <div class="setx-toggle-name">循环监听</div>
                      <div class="setx-toggle-desc">下播后继续保持监听等待下次开播</div>
                    </div>
                  </label>
                </div>
              </section>

              <!-- 通知与凭据 -->
              <section class="setx-card">
                <div class="setx-title"><i class="fa fa-bell-o"></i>通知与凭据</div>

                <div class="setx-field">
                  <div class="setx-label">
                    <span>息知通知地址</span>
                    <span class="setx-chip ${hasXiZhi ? "on" : ""}" id="xiZhiChip">${hasXiZhi ? "已配置" : "未配置"}</span>
                    <button type="button" class="setx-clear" data-clear="xiZhiUrl">清除</button>
                  </div>
                  <input type="text" id="xiZhiUrl" class="setx-input"
                         placeholder="${hasXiZhi? `已配置 ${escapeHtml(setting.xiZhiUrl)} 通知地址` : "请填写息知通知地址,例:https://xz.qqoq.net/xxx.send"}">
                  <p class="setx-help">开播 / 下播时推送到息知，留空表示不推送。清空后保存即可关闭通知。</p>
                </div>

                <div class="setx-field">
                  <div class="setx-label">
                    <span>直播间 Cookie</span>
                    <span class="setx-chip ${hasCookie ? "on" : ""}" id="cookieChip">${hasCookie ? "已配置" : "未配置"}</span>
                    <button type="button" class="setx-clear" data-clear="cookieValue">清除</button>
                  </div>
                  <textarea id="cookieValue" class="setx-textarea" rows="4"
                            placeholder="${hasCookie ? `已配置 Cookie [${escapeHtml(setting.cookie)}]，如需替换请直接粘贴新内容` : "粘贴 Cookie 内容，或填写 file:cookie/douyin-cookie.txt 引用本地文件"}"></textarea>
                  <p class="setx-help">留空表示沿用全局 <code>Cookie.${escapeHtml(room.platform || "")}</code>；支持 <code>file:cookie/DouYin-XXX.room-cookie.txt</code> 文件引用，避免长串粘贴。</p>
                </div>
              </section>

              <p id="inputError" class="setx-error hidden"></p>
            </div>` +
            "",
            [
                {
                    text: "取消",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: closeModal,
                },
                {
                    text: "保存设置",
                    className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: () => {
                        if (submitting) return;
                        const errorElement = document.getElementById("inputError");
                        const delayRaw = document.getElementById("delayIntervalSec").value.trim();
                        const delayIntervalSec = delayRaw === "" ? null : Number(delayRaw);
                        if (delayRaw !== "" && (isNaN(delayIntervalSec) || delayIntervalSec < 10)) {
                            errorElement.textContent = "刷新频率必须是大于等于 10 的整数，留空则沿用全局设置";
                            errorElement.classList.remove("hidden");
                            return;
                        }
                        errorElement.classList.add("hidden");

                        const cookieValueInput = document.getElementById("cookieValue").value.trim();
                        const xiZhiUrlInput = document.getElementById("xiZhiUrl").value.trim();

                        submitting = true;
                        const saveBtn = modalFooter.querySelector("button:last-child");
                        if (saveBtn) {
                            saveBtn.disabled = true;
                            saveBtn.textContent = "保存中…";
                        }
                        const restore = function () {
                            submitting = false;
                            if (saveBtn) {
                                saveBtn.disabled = false;
                                saveBtn.textContent = "保存设置";
                            }
                        };

                        postRequest(`action/setting?actionKey=${encodeURIComponent(key)}&key=${encodeURIComponent(roomKey)}`, {
                            isAutoRecord: document.getElementById("isAutoRecord").checked,
                            platform: room.platform,
                            setting: {
                                delayIntervalSec: delayIntervalSec,
                                convertFlvToMp4: document.getElementById("convertFlvToMp4").checked,
                                openSubtitle: document.getElementById("openSubtitle").checked,
                                isLoop: document.getElementById("isLoop").checked,
                                cookie: cookieValueInput.length > 0 ? cookieValueInput : null,
                                xiZhiUrl: xiZhiUrlInput.length > 0 ? xiZhiUrlInput : null,
                            }
                        }).then((msg) => {
                            restore();
                            closeModal();
                            showActionAfterModal(msg, () => {
                                if (msg.success) {
                                    handleManualRefresh();
                                }
                            });
                        }).catch((err) => {
                            restore();
                            errorElement.textContent = "保存失败：" + (err && err.message ? err.message : err);
                            errorElement.classList.remove("hidden");
                        });
                    },
                },
            ],
            { maxWidth: "min(46rem, 94vw)", hint: roomHint }
        );

        // 「清除」按钮：清空输入并把状态标记为待清除
        document.querySelectorAll(".setx-clear").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const targetId = this.dataset.clear;
                const dom = document.getElementById(targetId);
                if (dom) dom.value = "";
                const chip = document.getElementById(targetId === "xiZhiUrl" ? "xiZhiChip" : "cookieChip");
                if (chip) {
                    chip.textContent = "保存后沿用全局";
                    chip.classList.remove("on");
                }
            });
        });

        // 状态徽标反映「已保存的配置 + 本次输入」，避免用户误以为没存上
        function bindChip(inputId, chipId, saved) {
            const input = document.getElementById(inputId);
            const chip = document.getElementById(chipId);
            if (!input || !chip) return;
            input.addEventListener("input", function () {
                const hasValue = this.value.trim().length > 0;
                if (hasValue) {
                    chip.textContent = "已配置";
                    chip.classList.add("on");
                } else if (saved) {
                    chip.textContent = "保存后沿用全局";
                    chip.classList.remove("on");
                } else {
                    chip.textContent = "未配置";
                    chip.classList.remove("on");
                }
            });
        }
        bindChip("xiZhiUrl", "xiZhiChip", hasXiZhi);
        bindChip("cookieValue", "cookieChip", hasCookie);
    });
}

/** 监听设置弹窗样式（setx- 前缀，互不影响） */
const SETTING_MODAL_STYLE = `
<style>
  .setx { display: flex; flex-direction: column; gap: .7rem; }
  .setx-card { border: 1px solid #eef1f5; border-radius: .7rem; padding: .72rem .9rem; background: #fcfdff; }
  .setx-title { display: flex; align-items: center; gap: .45rem; font-size: .84rem; font-weight: 600;
    color: #111827; margin-bottom: .55rem; }
  .setx-title i { color: #3b82f6; }
  .setx-inline { display: flex; align-items: center; gap: .5rem; }
  .setx-input, .setx-textarea { width: 100%; font-size: .84rem; color: #1f2937; background: #fff;
    border: 1px solid #e5e7eb; border-radius: .5rem; padding: .5rem .65rem; outline: none;
    transition: border-color .15s, box-shadow .15s; }
  .setx-input:focus, .setx-textarea:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,.12); }
  .setx-inline .setx-input { max-width: 9rem; }
  .setx-unit { font-size: .8rem; color: #6b7280; }
  .setx-textarea { resize: vertical; min-height: 5.5rem; font-family: ui-monospace, Consolas, monospace; }
  .setx-help { margin-top: .45rem; font-size: .74rem; line-height: 1.5; color: #9ca3af; }
  .setx-help code { background: #f3f4f6; border-radius: .25rem; padding: .05rem .25rem; color: #6b7280; }
  .setx-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .5rem; }
  .setx-toggle { display: flex; align-items: flex-start; gap: .5rem; padding: .5rem .6rem; background: #fff;
    border: 1px solid #eef1f5; border-radius: .55rem; cursor: pointer; transition: border-color .15s, background .15s; }
  .setx-toggle:hover { border-color: #c7d8f7; background: #f7faff; }
  .setx-toggle input { margin-top: .15rem; width: 1rem; height: 1rem; accent-color: #3b82f6; cursor: pointer; }
  .setx-toggle-name { font-size: .82rem; font-weight: 600; color: #1f2937; }
  .setx-toggle-desc { font-size: .73rem; color: #9ca3af; margin-top: .15rem; line-height: 1.4; }
  .setx-field + .setx-field { margin-top: .65rem; }
  .setx-label { display: flex; align-items: center; gap: .45rem; font-size: .8rem; font-weight: 600;
    color: #374151; margin-bottom: .4rem; }
  .setx-chip { font-size: .68rem; font-weight: 500; padding: .1rem .4rem; border-radius: 999px;
    background: #f3f4f6; color: #9ca3af; }
  .setx-chip.on { background: #ecfdf5; color: #059669; }
  .setx-clear { margin-left: auto; font-size: .72rem; color: #9ca3af; background: none; border: none;
    cursor: pointer; padding: 0; }
  .setx-clear:hover { color: #ef4444; }
  .setx-required { color: #ef4444; }
  .setx-hint-inline { margin-left: auto; font-size: .7rem; font-weight: 400; color: #9ca3af; }
  .setx-btn { display: inline-flex; align-items: center; gap: .3rem; white-space: nowrap;
    padding: .5rem .8rem; font-size: .8rem; color: #2563eb; background: #eff6ff;
    border: 1px solid #bfdbfe; border-radius: .5rem; cursor: pointer; transition: all .15s; }
  .setx-btn:hover:not(:disabled) { background: #dbeafe; }
  .setx-btn:disabled { color: #9ca3af; background: #f9fafb; border-color: #e5e7eb; cursor: not-allowed; }
  .setx-collapse summary { list-style: none; cursor: pointer; margin-bottom: 0; user-select: none; }
  .setx-collapse summary::-webkit-details-marker { display: none; }
  .setx-collapse-arrow { margin-left: .2rem; color: #9ca3af; transition: transform .2s; }
  .setx-collapse[open] summary { margin-bottom: .7rem; }
  .setx-collapse[open] .setx-collapse-arrow { transform: rotate(180deg); }
  .setx-collapse-body { padding-top: .2rem; }
  .setx-2col { display: grid; grid-template-columns: minmax(0, 1.04fr) minmax(0, .96fr); gap: .7rem; align-items: start; }
  .setx-col { display: flex; flex-direction: column; gap: .7rem; min-width: 0; }
  .setx-span { grid-column: 1 / -1; }
  @media (max-width: 860px) {
    .setx-2col { grid-template-columns: 1fr; }
  }
  .setx-error { font-size: .78rem; color: #dc2626; background: #fef2f2; border: 1px solid #fecaca;
    border-radius: .45rem; padding: .45rem .6rem; word-break: break-all; overflow-wrap: anywhere; }
  @media (max-width: 640px) { .setx-grid { grid-template-columns: 1fr; } }
</style>`;

