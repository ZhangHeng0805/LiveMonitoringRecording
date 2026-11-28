/**
 * 监听面板 · 内网穿透组件（cpolar）
 * 展示 cpolar 是否配置/运行、公网映射地址、Git 同步状态与配置信息，
 * 并提供启动、停止、手动同步 Git 等操作。
 * 只读状态走 info/cpolar（普通令牌）；写操作走 action/cpolar*（私密令牌，需操作秘钥验证 + 二次确认）。
 */
(function () {
    var AUTO_REFRESH_MS = 3000;
    var refreshTimer = null;
    var lastStatus = null;
    var loading = false;
    // 启动 / 停止按钮互斥：同一时刻只显示与当前运行状态对应的那个
    var actionButtons = {start: null, stop: null};

    function syncActionButtons(running) {
        if (actionButtons.start) actionButtons.start.style.display = running ? "none" : "";
        if (actionButtons.stop) actionButtons.stop.style.display = running ? "" : "none";
    }

    function panelRoot() {
        return document.getElementById("cpolarPanelRoot");
    }

    function isPanelOpen() {
        var modal = document.getElementById("modal");
        return !!(modal && modal.classList.contains("active") && panelRoot());
    }

    function stopAutoRefresh() {
        if (refreshTimer) {
            clearInterval(refreshTimer);
            refreshTimer = null;
        }
    }

    function startAutoRefresh() {
        stopAutoRefresh();
        refreshTimer = setInterval(function () {
            if (!isPanelOpen()) {
                stopAutoRefresh();
                return;
            }
            loadCpolarStatus(false);
        }, AUTO_REFRESH_MS);
    }

    function statCard(label, value, valueClass) {
        return '<div class="rounded-xl border border-gray-200 bg-white px-3 py-2.5">' +
            '<p class="text-[11px] text-gray-500 mb-1">' + escapeHtml(label) + '</p>' +
            '<p class="text-base font-semibold break-all ' + (valueClass || "text-gray-800") + '">' +
            escapeHtml(value) + '</p></div>';
    }

    // 组件信息脱敏展示：*** 表示需要操作秘钥验证后才返回真实值
    function secureText(value, emptyText) {
        if (value === "***") {
            return '<span class="text-gray-400">***（验证操作秘钥后可见）</span>';
        }
        return escapeHtml(value || emptyText || "-");
    }

    function yesNo(value, colorYes) {
        return value
            ? '<span class="' + (colorYes || "text-green-600") + '">是</span>'
            : '<span class="text-gray-400">否</span>';
    }

    function renderStatus(data) {
        var root = panelRoot();
        if (!root) return;
        lastStatus = data || {};
        var d = lastStatus;
        var configured = !!d.configured;
        var running = !!d.running;
        var publicUrl = d.publicUrl || "";

        var stateText = !configured ? "未配置" : (running ? "运行中" : "已关闭");
        var stateClass = !configured ? "text-gray-500" : (running ? "text-green-600" : "text-red-500");

        // 运行中只显示「停止穿透」，已关闭只显示「启动穿透」
        syncActionButtons(running);

        var urlText = publicUrl ||
            (!configured ? "缺少 cpolar.setting" : (running ? "正在获取公网地址…" : "未运行"));

        root.innerHTML =
            '<div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">' +
            statCard("穿透状态", stateText, stateClass) +
            statCard("公网地址", urlText, publicUrl ? "text-primary" : "text-gray-500") +
            statCard("Git 同步", d.updateGitSuccess ? "已同步" : "未同步/失败", d.updateGitSuccess ? "text-green-600" : "text-gray-500") +
            statCard("最近同步时间", d.updateGitTime || "-") +
            '</div>' +
            '<div class="rounded-xl border border-gray-200 bg-gray-50/60 px-4 py-3 mb-4 text-xs text-gray-600 leading-6">' +
            '<div><span class="text-gray-400">映射命令：</span>' + escapeHtml(d.localCommand || "-") +
            '<span class="mx-3 text-gray-300">|</span><span class="text-gray-400">启动/停止时间：</span>' + escapeHtml(d.startTime || "-") + '</div>' +
            '<div><span class="text-gray-400">程序路径：</span><span class="break-all">' + secureText(d.exePath) +
            '</span><span class="mx-2 text-gray-300">|</span><span class="text-gray-400">程序存在：</span>' + yesNo(d.exeExists) + '</div>' +
            '<div><span class="text-gray-400">Git 项目目录：</span><span class="break-all">' + secureText(d.gitRepoDir) + '</span></div>' +
            '<div><span class="text-gray-400">写入文件：</span><span class="break-all">' + secureText(d.gitFileRelativePath) + '</span></div>' +
            '</div>' +
            (publicUrl
                ? '<div class="rounded-xl border border-gray-200 bg-white px-4 py-3 text-xs text-gray-600">' +
                '<span class="text-gray-400">外网访问地址：</span>' +
                '<a class="text-primary break-all" href="' + escapeHtml(publicUrl) + '" target="_blank" rel="noopener">' +
                escapeHtml(publicUrl) + '</a></div>'
                : '');
    }

    /**
     * 状态查询优先带秘钥令牌（静默，不弹验证框）：验证过操作秘钥后
     * 才能看到程序路径 / Git 目录等本地路径的真实值；否则退回普通令牌（显示 ***）。
     */
    async function loadCpolarStatus(showError) {
        if (loading) return;
        loading = true;
        try {
            var msg = await fetchStatusWithSecretToken();
            if (!msg) {
                msg = await getRequest("info/cpolar", null, 8000, "normal");
            }
            if (!msg || !msg.success) {
                if (showError) notifyDialog((msg && msg.message) || "内网穿透组件状态获取失败", {title:"内网穿透", tone:"danger"});
                return;
            }
            renderStatus(msg.data || {});
        } catch (e) {
            if (showError) console.warn("内网穿透组件状态获取失败", e);
        } finally {
            loading = false;
        }
    }

    async function fetchStatusWithSecretToken() {
        // 仅本次会话亲手验证过秘钥才用秘钥令牌取真实数据；旧版 util.js 缺该函数时按未验证处理
        if (typeof isSecretVerified !== "function" || !isSecretVerified()) return null;
        var secret = null;
        try {
            if (typeof isTokenValid === "function" && isTokenValid(TOKEN_KIND.SECRET)) {
                secret = readToken(TOKEN_KIND.SECRET).token;
            }
        } catch (e) {
            secret = null;
        }
        if (!secret) return null;
        var controller = new AbortController();
        var timer = setTimeout(function () {
            controller.abort();
        }, 8000);
        try {
            var base = (typeof MainUrl !== "undefined" && MainUrl) ? MainUrl : (window.MainUrl || "");
            var res = await fetch(base + "info/cpolar", {
                headers: {Authorization: "Bearer " + secret},
                signal: controller.signal
            });
            if (!res.ok) return null;
            return await res.json();
        } catch (e) {
            return null;
        } finally {
            clearTimeout(timer);
        }
    }

    function actionLabelOf(action) {
        if (action === "start") return "启动内网穿透（cpolar 会把本地面板端口映射到公网，并把地址写入 Git）";
        if (action === "stop") return "停止内网穿透（公网地址会立即失效）";
        return "把当前公网地址同步到 Git 配置文件";
    }

    async function runCpolarAction(action) {
        var label = actionLabelOf(action);
        var path = action === "start" ? "action/cpolarStart"
            : (action === "stop" ? "action/cpolarStop" : "action/cpolarUpdateGit");
        try {
            var key = await confirmSubmitAuth(label);
            if (key === null) return;
            var msg = await postRequest(path + "?actionKey=" + encodeURIComponent(key), {}, 30000, "private");
            if (!msg || !msg.success) {
                notifyDialog((msg && msg.message) || "操作失败", {title:"内网穿透", tone:"danger"});
                return;
            }
            notifyDialog(msg.message || "操作成功", {title:"内网穿透", tone:"success"});
            await loadCpolarStatus(true);
            if (typeof window.refreshCpolarComponentBadge === "function") {
                window.refreshCpolarComponentBadge();
            }
        } catch (e) {
            console.error("内网穿透组件操作失败", e);
        }
    }

    window.cpolarPanel = function () {
        var btns = setModalContent(
            "内网穿透组件（cpolar）",
            '<div id="cpolarPanelRoot" class="text-sm text-gray-700">' +
            '<div class="py-8 text-center text-gray-400">正在读取内网穿透状态…</div></div>',
            [
                {
                    text: "刷新状态",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: function () {
                        loadCpolarStatus(true);
                    }
                },
                {
                    text: "停止穿透",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: function () {
                        runCpolarAction("stop");
                    }
                },
                {
                    text: "同步 Git",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: function () {
                        runCpolarAction("updateGit");
                    }
                },
                {
                    text: "启动穿透",
                    className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: function () {
                        runCpolarAction("start");
                    }
                }
            ],
            { maxWidth: "min(64rem, 95vw)", hint: "状态每 3 秒自动刷新" }
        );

        // btns 顺序：0 刷新状态、1 停止穿透、2 同步 Git、3 启动穿透
        // 打开时先按“未运行”收敛，避免沿用上次会话的状态闪一下
        lastStatus = null;
        actionButtons.stop = btns && btns[1] ? btns[1] : null;
        actionButtons.start = btns && btns[3] ? btns[3] : null;
        syncActionButtons(false);

        window.onModalDismiss(stopAutoRefresh);
        loadCpolarStatus(true);
        startAutoRefresh();
    };
})();
