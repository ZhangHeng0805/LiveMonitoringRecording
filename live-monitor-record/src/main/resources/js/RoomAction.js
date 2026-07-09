/**
 * 操作秘钥验证
 * @param onSubmit
 */
function showActionBeforeModal(onSubmit) {
    setModalContent(
        "操作验证",
        `<div id="inputContainer" class="mb-4">
                <label for="actionKey" class="block text-sm font-medium text-gray-700 mb-1">操作秘钥</label>
                <input type="password"
                    value="${window.localStorage && window.localStorage.getItem("actionKey") || ''}"
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
                    "bg-gray text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                onClick: closeModal,
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

//操作结果弹窗
function showActionAfterModal(msg) {
    setModalContent(
        msg.title || "操作结果",
        `<p class="
            ${msg.success ? "text-green-600" : "text-red-600"} text-center">${
            msg.message
        }</p>`,
        [
            {
                text: "知道了",
                className:
                    "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                onClick: closeModal,
            },
        ]
    );
}

//房间刷新
function actionRefresh(key) {
    actionRequest("refresh", {
        key: key,
    }).then((data) => {
        data.title = "刷新操作结果";
        showActionAfterModal(data);
        if (data.success) {
            handleManualRefresh();
        }
    });
}

async function actionRequest(path, params) {
    return getRequest(`action/${path}`, params);
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
            showActionAfterModal(data);
            if (data.success) {
                handleManualRefresh();
            }
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
            showActionAfterModal(data);
            if (data.success) {
                handleManualRefresh();
            }
        });
    });
}

function delRoom(roomKey) {
    showActionBeforeModal((key) => {
        actionRequest("delRoom", {
            key: roomKey,
            actionKey: key,
        }).then((data) => {
            data.title = "操作结果";
            showActionAfterModal(data);
            if (data.success) {
                handleManualRefresh();
            }
        });
    });
}

//直播间设置操作
function addRoom() {
    showActionBeforeModal((key) => {
        setModalContent(
            `新增直播监听`,
            `
            <div id="roomIDContainer" class="mb-4">
                <label for="roomID" class="block text-sm font-medium text-gray-700 mb-1">直播间ID</label>
                <input type="text"
                    id="roomID"
                    class="input-field"
                    placeholder="请输入直播间ID">
            </div>
            <div id="roomNameContainer" class="mb-4">
                <label for="roomName" class="block text-sm font-medium text-gray-700 mb-1">直播间名称</label>
                <input type="text"
                    id="roomName"
                    class="input-field"
                    placeholder="请输入直播间名称（选填）">
            </div>
            <div id="platformContainer" class="mb-4">
                <label for="platform" class="block text-sm font-medium text-gray-700 mb-1">直播间平台</label>
                <select id="platform" class="input-field">
                </select>
            </div>
            <div id="delayIntervalSecContainer" class="mb-4">
                <label for="delayIntervalSec" class="block text-sm font-medium text-gray-700 mb-1">刷新频率(s)</label>
                <input type="number"
                    value=30
                    min=10
                    id="delayIntervalSec"
                    class="input-field"
                    placeholder="请设置直播间刷新频率">
            </div>
            <div id="isRecordContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer" title="直播开始后自动开始录制">
                <input type="checkbox"
                    id="isRecord"
                    class="h-4 w-4 text-primary focus:ring-primary" checked>
                <span class="text-sm font-medium text-gray-700">开启直播自动录制</span>
              </label>
            </div>
            <div id="openSubtitleContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer">
                <input type="checkbox"
                    id="openSubtitle"
                    class="h-4 w-4 text-primary focus:ring-primary">
                <span class="text-sm font-medium text-gray-700">开启弹幕记录</span>
              </label>
            </div>
            <div id="convertFileContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer">
                <input type="checkbox"
                    id="convertFlvToMp4"
                    class="h-4 w-4 text-primary focus:ring-primary" checked>
                <span class="text-sm font-medium text-gray-700">转换录制文件(FLV转MP4)</span>
              </label>
            </div>
            <div id="isLoopContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer" title="直播结束后是否继续监听">
                <input type="checkbox"
                    id="isLoop"
                    class="h-4 w-4 text-primary focus:ring-primary" checked>
                <span class="text-sm font-medium text-gray-700">是否循环监听</span>
              </label>
            </div>
            <div id="xiZhiUrlContainer" class="mb-4">
                <label for="xiZhiUrl" class="block text-sm font-medium text-gray-700 mb-1">息知通知地址</label>
                <input type="text"
                    id="xiZhiUrl"
                    class="input-field"
                    placeholder="息知通知URl">
            </div>
            <div id="cookieContainer" class="mb-4">
              <label for="cookieValue" class="block text-sm font-medium text-gray-700 mb-1">Cookie设置</label>
              <textarea
                  id="cookieValue"
                  class="input-field w-full min-h-[100px] resize-y"
                  placeholder="请输入Cookie内容，也可以使用 file:cookie/douyin-cookie.txt 文本文件引用形式"></textarea>
            </div>
            <p id="inputError" class="mt-1 text-sm text-red-600 hidden"></p>`,
            [
                {
                    text: "取消",
                    className:
                        "bg-gray text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: closeModal,
                },
                {
                    text: "确定",
                    className:
                        "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: () => {
                        const roomIDInput = document.getElementById("roomID").value.trim();
                        const roomNameInput = document.getElementById("roomName").value.trim();
                        const xiZhiUrlInput = document
                            .getElementById("xiZhiUrl")
                            .value.trim();
                        const delayIntervalSecInput =
                            document.getElementById("delayIntervalSec").value;
                        const platformSelect = document.getElementById("platform").value;
                        const convertFlvToMp4Checked =
                            document.getElementById("convertFlvToMp4").checked;
                        const openSubtitleChecked =
                            document.getElementById("openSubtitle").checked;
                        const isLoopChecked = document.getElementById("isLoop").checked;
                        const isRecordChecked = document.getElementById("isRecord").checked;
                        const cookieValueInput = document
                            .getElementById("cookieValue")
                            .value.trim();
                        const errorElement = document.getElementById("inputError");
                        // 简单的输入验证
                        if (roomIDInput.length < 1) {
                            errorElement.textContent = "直播间ID不能为空！";
                            errorElement.classList.remove("hidden");
                            return;
                        }
                        if (
                            delayIntervalSecInput &&
                            (isNaN(delayIntervalSecInput) ||
                                Number(delayIntervalSecInput) < 10)
                        ) {
                            errorElement.textContent = "刷新频率必须是大于等于10的数字";
                            errorElement.classList.remove("hidden");
                            return;
                        }
                        errorElement.classList.add("hidden");

                        closeModal();
                        postRequest("action/addRoom?actionKey=" + key, {
                            id: roomIDInput,
                            name: roomNameInput.length > 0 ? roomNameInput : null,
                            platform: platformSelect,
                            isAutoRecord: isRecordChecked,
                            setting: {
                                delayIntervalSec: delayIntervalSecInput,
                                convertFlvToMp4: convertFlvToMp4Checked,
                                openSubtitle: openSubtitleChecked,
                                isLoop: isLoopChecked,
                                cookie: cookieValueInput.length > 0 ? cookieValueInput : null,
                                xiZhiUrl: xiZhiUrlInput.length > 0 ? xiZhiUrlInput : null,
                            },
                        }).then((msg) => {
                            showActionAfterModal(msg);
                            if (msg.success) {
                                handleManualRefresh();
                            }
                        });
                    },
                },
            ]
        );
        // 遍历对象动态生成下拉选项
        Object.entries(platformName).forEach(([key, text]) => {
            const option = document.createElement("option");
            option.value = key;
            option.textContent = text;
            document.getElementById("platform").appendChild(option);
        });
    });
}

//直播间设置操作
function actionSetting(roomKey, room) {
    let setting = room.setting;
    showActionBeforeModal((key) => {
        // console.log("操作设置", roomKey, setting);
        setModalContent(
            `${room.nickname || roomKey} 监听设置`,
            `<div id="inputContainer" class="mb-4">
              <label for="delayIntervalSec" class="block text-sm font-medium text-gray-700 mb-1">刷新频率(s)</label>
              <input type="number"
                  value="${setting.delayIntervalSec}"
                  id="delayIntervalSec"
                  class="input-field"
                  placeholder="请设置直播间刷新频率">
            </div>
            <div id="openSubtitleContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer">
                <input type="checkbox"
                    id="openSubtitle"
                    class="h-4 w-4 text-primary focus:ring-primary"
                    ${setting.openSubtitle ? "checked" : ""}>
                <span class="text-sm font-medium text-gray-700">开启弹幕记录</span>
              </label>
            </div>
            <div id="isAutoRecordContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer" title="直播开始后自动开启录制">
                <input type="checkbox"
                    id="isAutoRecord"
                    class="h-4 w-4 text-primary focus:ring-primary"
                    ${setting.isAutoRecord ? "checked" : ""}>
                <span class="text-sm font-medium text-gray-700">开启自动录制</span>
              </label>
            </div>
            <div id="convertFileContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer" title="录制完成后自动将录制文件转换格式">
                <input type="checkbox"
                    id="convertFlvToMp4"
                    class="h-4 w-4 text-primary focus:ring-primary"
                    ${setting.convertFlvToMp4 ? "checked" : ""}>
                <span class="text-sm font-medium text-gray-700">转换录制文件(FLV转MP4)</span>
              </label>
            </div>
            <div id="isLoopContainer" class="mb-4">
              <label class="flex items-center space-x-2 cursor-pointer" title="直播结束后是否继续监听">
                <input type="checkbox"
                    id="isLoop"
                    class="h-4 w-4 text-primary focus:ring-primary"
                    ${setting.isLoop ? "checked" : ""}>
                <span class="text-sm font-medium text-gray-700">是否循环监听</span>
              </label>
            </div>
            <div id="xiZhiUrlContainer" class="mb-4">
                <label for="xiZhiUrl" class="block text-sm font-medium text-gray-700 mb-1">息知通知地址</label>
                <input type="text"
                    id="xiZhiUrl"
                    class="input-field"
                    title="息知通知API地址，输入null可清空设置"
                    placeholder="${setting.xiZhiUrl || "息知通知URl"}">
            </div>
            <div id="cookieContainer" class="mb-4">
              <label for="cookieValue" class="block text-sm font-medium text-gray-700 mb-1">Cookie设置</label>
              <textarea
                  id="cookieValue"
                  class="input-field w-full min-h-[100px] resize-y"
                  title="直播间Cookie，输入null可清空设置"
                  placeholder="${setting.cookie || '请输入Cookie内容，也可以使用 file:cookie/douyin-cookie.txt 文本文件引用形式'}"></textarea>
            </div>
            <p id="inputError" class="mt-1 text-sm text-red-600 hidden"></p>`,
            [
                {
                    text: "取消",
                    className:
                        "bg-gray text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: closeModal,
                },
                {
                    text: "确定",
                    className:
                        "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: () => {
                        const delayIntervalSecInput =
                            document.getElementById("delayIntervalSec").value;
                        const isAutoRecordChecked =
                            document.getElementById("isAutoRecord").checked;
                        const convertFlvToMp4Checked =
                            document.getElementById("convertFlvToMp4").checked;
                        const openSubtitleChecked =
                            document.getElementById("openSubtitle").checked;
                        const isLoopChecked = document.getElementById("isLoop").checked;
                        const cookieValueInput = document
                            .getElementById("cookieValue")
                            .value.trim();
                        const xiZhiUrlInput = document
                            .getElementById("xiZhiUrl")
                            .value.trim();
                        const errorElement = document.getElementById("inputError");
                        // 简单的输入验证
                        if (
                            delayIntervalSecInput &&
                            (isNaN(delayIntervalSecInput) ||
                                Number(delayIntervalSecInput) < 10)
                        ) {
                            errorElement.textContent = "刷新频率必须是大于等于10的数字";
                            errorElement.classList.remove("hidden");
                            return;
                        }
                        errorElement.classList.add("hidden");

                        closeModal();
                        postRequest(`action/setting?actionKey=${key}&key=${roomKey}`, {
                            isAutoRecord: isAutoRecordChecked,
                            platform: room.platform,
                            setting: {
                                delayIntervalSec: delayIntervalSecInput,
                                convertFlvToMp4: convertFlvToMp4Checked,
                                openSubtitle: openSubtitleChecked,
                                isLoop: isLoopChecked,
                                cookie: cookieValueInput.length > 0 ? cookieValueInput : null,
                                xiZhiUrl: xiZhiUrlInput.length > 0 ? xiZhiUrlInput : null,
                            }
                        }).then((msg) => {
                            showActionAfterModal(msg);
                            if (msg.success) {
                                handleManualRefresh();
                            }
                        });
                    },
                },
            ]
        );
    });
}
