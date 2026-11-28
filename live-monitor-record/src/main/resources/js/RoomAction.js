/**
 * 是否已持有可用的秘钥令牌：有效期内的令牌说明本次会话已经通过操作验证
 */
function hasSecretTokenReady() {
    try {
        return typeof isTokenValid === "function"
            && typeof TOKEN_KIND !== "undefined"
            && isTokenValid(TOKEN_KIND.SECRET);
    } catch (e) {
        return false;
    }
}

/**
 * 已通过验证时的二次确认：不再重复输入秘钥，只确认本次操作，防误触
 * @param actionLabel 操作描述（如“开始监听 · 房间 123”）
 * @param onSubmit 确认回调，入参为记住的秘钥（可能为空字符串）
 * @param onCancel 取消回调
 */
function showConfirmBeforeAction(actionLabel, onSubmit, onCancel) {
    const label = actionLabel || "该操作";
    setModalContent(
        "操作确认",
        `<div class="text-sm text-gray-700 leading-relaxed">
            <p class="mb-3">本次操作已通过验证，确认执行？</p>
            <p class="mb-3"><span class="inline-flex items-center px-2.5 py-1 rounded-md bg-gray-100 text-gray-800 font-medium break-all">${escapeHtml(label)}</span></p>
            <p class="text-xs text-gray-500 mb-0">该确认仅用于防误操作，秘钥令牌有效期内不再重复输入秘钥。</p>
         </div>`,
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
                text: "确认执行",
                className:
                    "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                onClick: () => {
                    let key = "";
                    try {
                        key = (window.localStorage && window.localStorage.getItem("actionKey")) || "";
                    } catch (e) {
                        key = "";
                    }
                    closeModal();
                    onSubmit(key);
                },
            },
        ]
    );
}

/**
 * 操作秘钥输入弹窗（纯输入，不做二次确认）
 * @param onSubmit 校验通过回调，入参为秘钥
 * @param onCancel 取消回调（Promise 场景需要）
 */
function showActionKeyModal(onSubmit, onCancel) {
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

/**
 * 操作前置弹窗：
 * 1. 还没通过验证（无有效秘钥令牌）→ 弹秘钥输入；
 * 2. 已通过验证 → 弹二次确认，避免验证一次后私密操作被静默执行。
 * @param actionLabel 操作描述，用于二次确认弹窗展示具体动作
 * @param onSubmit 确认/校验通过回调，入参为秘钥（二次确认分支可能为空字符串）
 * @param onCancel 取消回调
 */
function showActionBeforeModal(actionLabel, onSubmit, onCancel) {
    if (hasSecretTokenReady()) {
        showConfirmBeforeAction(actionLabel, onSubmit, onCancel);
        return;
    }
    showActionKeyModal(onSubmit, onCancel);
}

/**
 * 打开弹窗前的验证：已经持有有效秘钥令牌就直接放行（不再弹框），否则弹一次秘钥验证
 * 取消时静默返回，不发起后续动作
 * @param onReady 放行回调，入参为记住的秘钥（可能为空字符串）
 */
function requireVerified(onReady) {
    if (hasSecretTokenReady()) {
        let key = "";
        try {
            key = (window.localStorage && window.localStorage.getItem("actionKey")) || "";
        } catch (e) {
            key = "";
        }
        onReady(key);
        return;
    }
    showActionKeyModal(onReady);
}

/**
 * 提交类动作的二次确认/验证：用独立浮层，不占用主弹窗容器，
 * 这样「新增/设置」正在填写的表单不会被顶掉，取消后还能接着改。
 * @param actionLabel 提交动作描述
 * @returns {Promise<string|null>} 确认通过返回秘钥（已验证时是记住的秘钥或空串）；取消返回 null
 */
function confirmSubmitAuth(actionLabel) {
    return new Promise(function (resolve) {
        const ready = hasSecretTokenReady();
        let remembered = "";
        if (ready) {
            try {
                remembered = (window.localStorage && window.localStorage.getItem("actionKey")) || "";
            } catch (e) {
                remembered = "";
            }
        }

        const mask = document.createElement("div");
        mask.setAttribute("style", "position:fixed;inset:0;z-index:2000;display:flex;align-items:center;justify-content:center;padding:16px;background:rgba(17,24,39,.45);");

        const card = document.createElement("div");
        card.setAttribute("style", "width:100%;max-width:380px;background:#fff;border-radius:14px;box-shadow:0 18px 46px rgba(22,28,45,.2);padding:20px;color:#1f2937;font-size:14px;line-height:1.6;");

        const title = ready ? "操作确认" : "操作验证";
        const desc = ready ? "本次提交已通过验证，确认执行？" : "提交前需要验证操作秘钥。";
        const okText = ready ? "确认提交" : "验证并提交";
        const inputBlock = ready ? "" : `
            <input id="submitAuthKey" type="password" autocomplete="off" placeholder="请输入操作秘钥"
                   style="width:100%;height:36px;padding:0 10px;border:1px solid #dfe2ec;border-radius:8px;font-size:13px;outline:none;box-sizing:border-box;">
            <label style="display:flex;align-items:center;gap:6px;margin-top:10px;font-size:12px;color:#5b6172;cursor:pointer;">
              <input id="submitAuthRemember" type="checkbox" checked style="width:16px;height:16px;margin:0;">
              <span>记住秘钥</span>
            </label>`;

        card.innerHTML = `
            <div style="font-size:16px;font-weight:600;margin-bottom:6px;">${title}</div>
            <div style="color:#5b6172;font-size:13px;margin-bottom:10px;">${desc}</div>
            <div style="margin-bottom:12px;">
              <span style="display:inline-block;padding:4px 10px;border-radius:6px;background:#f3f4f6;font-weight:500;word-break:break-all;">${escapeHtml(actionLabel || "该提交操作")}</span>
            </div>
            ${inputBlock}
            <div id="submitAuthError" style="display:none;margin-top:8px;color:#ef4444;font-size:12px;"></div>
            <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px;">
              <button type="button" id="submitAuthCancel" style="height:32px;padding:0 12px;border-radius:8px;border:1px solid #dfe2ec;background:#fff;color:#1f2937;font-size:13px;cursor:pointer;">取消</button>
              <button type="button" id="submitAuthOk" style="height:32px;padding:0 12px;border-radius:8px;border:1px solid #6366f1;background:#6366f1;color:#fff;font-size:13px;cursor:pointer;">${okText}</button>
            </div>`;

        mask.appendChild(card);
        document.body.appendChild(mask);

        const errEl = card.querySelector("#submitAuthError");
        const showErr = function (msg) {
            errEl.textContent = msg || "";
            errEl.style.display = msg ? "block" : "none";
        };
        const finish = function (value) {
            if (mask.parentNode) mask.parentNode.removeChild(mask);
            resolve(value);
        };
        const cancel = function () {
            finish(null);
        };

        card.querySelector("#submitAuthCancel").addEventListener("click", cancel);
        mask.addEventListener("click", function (e) {
            if (e.target === mask) cancel();
        });

        const okBtn = card.querySelector("#submitAuthOk");
        okBtn.addEventListener("click", function () {
            if (ready) {
                finish(remembered);
                return;
            }
            const input = card.querySelector("#submitAuthKey");
            const value = input ? input.value.trim() : "";
            if (!value) {
                showErr("请输入操作秘钥");
                if (input) input.focus();
                return;
            }
            const remember = card.querySelector("#submitAuthRemember");
            const shouldRemember = !!(remember && remember.checked);
            okBtn.disabled = true;
            const origText = okBtn.textContent;
            okBtn.textContent = "验证中…";
            showErr("");
            Promise.resolve()
                .then(function () {
                    // 先用秘钥换令牌：秘钥错误直接在本浮层提示，不把用户弹回主弹窗
                    return ensureSecretToken(true, value);
                })
                .then(function () {
                    // 校验通过后才记住秘钥，避免把错误秘钥写进 localStorage
                    try {
                        if (shouldRemember && window.localStorage) window.localStorage.setItem("actionKey", value);
                        else if (window.localStorage) window.localStorage.removeItem("actionKey");
                    } catch (e) {
                    }
                    finish(value);
                })
                .catch(function (e) {
                    okBtn.disabled = false;
                    okBtn.textContent = origText;
                    showErr((e && e.message) || "操作秘钥错误");
                });
        });

        if (!ready) {
            const input = card.querySelector("#submitAuthKey");
            if (input) setTimeout(function () { input.focus(); }, 30);
        }
    });
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

        // 换取令牌时必须是真输入秘钥，不能走“已通过验证”的二次确认分支
        showActionKeyModal(
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
    showActionBeforeModal((isOpen ? "开始监听" : "停止监听") + " · 房间 " + roomKey, (key) => {
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
    showActionBeforeModal((isRecord ? "开始录制" : "停止录制") + " · 房间 " + roomKey, (key) => {
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

//弹幕录制操作（仅抖音支持）
function actionSubtitle(roomKey, isOpen) {
    showActionBeforeModal((isOpen ? "开启弹幕录制" : "停止弹幕录制") + " · 房间 " + roomKey, (key) => {
        actionRequest("subtitle", {
            key: roomKey,
            flag: isOpen,
            actionKey: key,
        }).then((data) => {
            data.title = "弹幕录制操作结果";
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
    showActionBeforeModal("删除监听 · 房间 " + roomKey, (key) => {
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
    // 已验证过（有有效秘钥令牌）直接打开表单；没验证过才弹「操作验证」；取消静默
    requireVerified(function () {
        openAddRoomForm();
    });
}

/**
 * 打开「新增直播监听」表单：打开阶段不做任何验证/确认，提交时才确认
 */
function openAddRoomForm() {
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
                                placeholder="留空沿用全局 Cookie，或填 file:cookie/DouYin.cookie"></textarea>
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

                <!-- 录制画质 -->
                <section class="setx-card">
                  <div class="setx-title"><i class="fa fa-film"></i>录制画质</div>
                  <select id="recordQuality" class="setx-input">
                    <option value="HIGH" selected>最高画质（默认，推荐）</option>
                    <option value="MIDDLE">中等画质</option>
                    <option value="LOW">最低画质</option>
                  </select>
                  <p class="setx-help">按接口返回的清晰度档位选择：最高取第一档、中等取中间档、最低取最后一档，只影响本项目的录制。</p>
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

                        // 打开表单不验证，提交前才二次确认：已验证弹确认框，未验证先输秘钥
                        confirmSubmitAuth("新增直播监听 · 房间 " + roomIDInput).then(function (submitKey) {
                            if (submitKey === null) {
                                restore();
                                return;
                            }
                            postRequest("action/addRoom?actionKey=" + encodeURIComponent(submitKey), {
                                id: roomIDInput,
                                name: roomNameInput.length > 0 ? roomNameInput : null,
                                platform: platformSelect,
                                isAutoRecord: document.getElementById("isRecord").checked,
                                setting: {
                                    delayIntervalSec: delayIntervalSec,
                                    convertFlvToMp4: document.getElementById("convertFlvToMp4").checked,
                                    openSubtitle: document.getElementById("openSubtitle").checked,
                                    isLoop: document.getElementById("isLoop").checked,
                                    recordQuality: document.getElementById("recordQuality").value,
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
}

/**
 * 录制画质：接口/监听文件里存枚举名（HIGH/MIDDLE/LOW），界面显示中文；
 * 兼容早期存成中文标签的房间数据
 */
const RECORD_QUALITY_KEYS = {
    HIGH: "HIGH", MIDDLE: "MIDDLE", LOW: "LOW",
    "最高": "HIGH", "中等": "MIDDLE", "最低": "LOW"
};

function normalizeRecordQuality(value) {
    return RECORD_QUALITY_KEYS[value] || "HIGH";
}

//直播间设置操作
function actionSetting(roomKey, room) {
    // 已验证过直接打开设置表单；没验证过才弹「操作验证」；取消静默
    requireVerified(function () {
        openRoomSettingForm(roomKey, room);
    });
}

/**
 * 打开「监听设置」表单：打开阶段不做验证/确认，保存时才确认
 */
function openRoomSettingForm(roomKey, room) {
    let setting = room.setting || {};
    const platformText = (typeof platformName !== "undefined" && platformName[room.platform]) || room.platform || "";
    const roomHint = [platformText, roomKey].filter(Boolean).join(" · ");
    let submitting = false;

    const delayValue = setting.delayIntervalSec === null || setting.delayIntervalSec === undefined ? "" : setting.delayIntervalSec;
    const qualityValue = normalizeRecordQuality(setting.recordQuality);
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

              <!-- 录制画质 -->
              <section class="setx-card">
                <div class="setx-title"><i class="fa fa-film"></i>录制画质</div>
                <select id="recordQuality" class="setx-input">
                  <option value="HIGH" ${qualityValue === "HIGH" ? "selected" : ""}>最高画质（默认）</option>
                  <option value="MIDDLE" ${qualityValue === "MIDDLE" ? "selected" : ""}>中等画质</option>
                  <option value="LOW" ${qualityValue === "LOW" ? "selected" : ""}>最低画质</option>
                </select>
                <p class="setx-help">最高=第一档、中等=中间档、最低=最后一档；改动在下次开播录制时生效。</p>
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
                            placeholder="${hasCookie ? `已配置 Cookie [${escapeHtml(setting.cookie)}]，如需替换请直接粘贴新内容` : "粘贴 Cookie 内容，或填写 file:cookie/DouYin.cookie 引用本地文件"}"></textarea>
                  <p class="setx-help">留空表示沿用全局 <code>Cookie.${escapeHtml(room.platform || "")}</code>；支持 <code>file:cookie/DouYin-XXX.cookie</code> 文件引用，避免长串粘贴。</p>
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

                        // 保存前才二次确认：已验证弹确认框，未验证先输秘钥
                        confirmSubmitAuth("保存监听设置 · " + roomHint).then(function (submitKey) {
                            if (submitKey === null) {
                                restore();
                                return;
                            }
                            postRequest(`action/setting?actionKey=${encodeURIComponent(submitKey)}&key=${encodeURIComponent(roomKey)}`, {
                                isAutoRecord: document.getElementById("isAutoRecord").checked,
                                platform: room.platform,
                                setting: {
                                    delayIntervalSec: delayIntervalSec,
                                    convertFlvToMp4: document.getElementById("convertFlvToMp4").checked,
                                    openSubtitle: document.getElementById("openSubtitle").checked,
                                    isLoop: document.getElementById("isLoop").checked,
                                    recordQuality: document.getElementById("recordQuality").value,
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

