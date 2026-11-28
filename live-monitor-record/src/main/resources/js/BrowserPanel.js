/**
 * 监听面板 · Playwright 浏览器组件
 * 展示 Playwright/Chromium 运行状态、上下文缓存与组件信息，并提供重启/关闭/切换无头模式等操作。
 * 只读状态走 info/browser（普通令牌）；写操作走 action/*（私密令牌，需操作秘钥验证 + 二次确认）。
 */
(function () {
    var AUTO_REFRESH_MS = 3000;
    var refreshTimer = null;
    var lastStatus = null;
    var loading = false;
    // 底部操作按钮随运行状态切换：运行中才有关闭/重启，未运行只保留启动
    var actionButtons = {close: null, restart: null, toggle: null};

    function syncActionButtons(status) {
        var running = !!(status && status.running);
        if (actionButtons.close) actionButtons.close.style.display = running ? "" : "none";
        if (actionButtons.restart) {
            actionButtons.restart.textContent = running ? "重启浏览器" : "启动浏览器";
        }
        if (actionButtons.toggle) {
            var headless = !!(status && status.headless);
            actionButtons.toggle.textContent = status
                ? (headless ? "切换为有头窗口" : "切换为无头窗口")
                : "切换运行模式";
            actionButtons.toggle.title = "切换后立即重启浏览器生效";
        }
    }

    function panelRoot() {
        return document.getElementById("browserPanelRoot");
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
            loadBrowserStatus(false);
        }, AUTO_REFRESH_MS);
    }

    function formatDuration(sec) {
        sec = Number(sec) || 0;
        if (sec < 60) return sec + " 秒";
        var m = Math.floor(sec / 60);
        var s = sec % 60;
        if (m < 60) return m + " 分" + (s ? " " + s + " 秒" : "");
        var h = Math.floor(m / 60);
        return h + " 小时 " + (m % 60) + " 分";
    }

    function shortUa(ua) {
        if (!ua) return "浏览器默认";
        var text = String(ua);
        var m = text.match(/Chrome\/[\d.]+/);
        if (m) return m[0];
        return text.length > 28 ? text.substring(0, 28) + "…" : text;
    }

    function statCard(label, value, valueClass) {
        return '<div class="rounded-xl border border-gray-200 bg-white px-3 py-2.5">' +
            '<p class="text-[11px] text-gray-500 mb-1">' + escapeHtml(label) + '</p>' +
            '<p class="text-base font-semibold ' + (valueClass || "text-gray-800") + '">' + escapeHtml(value) + '</p>' +
            '</div>';
    }

    // 组件信息脱敏展示：*** 表示需要操作秘钥验证后才返回真实值
    function secureText(value, emptyText) {
        if (value === "***") {
            return '<span class="text-gray-400">***（验证操作秘钥后可见）</span>';
        }
        return escapeHtml(value || emptyText || "未知");
    }

    function renderStatus(data) {
        var root = panelRoot();
        if (!root) return;
        lastStatus = data || {};
        var d = lastStatus;
        var running = !!d.running;
        var headlessText = d.headless ? "无头（不显示窗口）" : "有头（显示窗口）";

        // 运行中显示「关闭/重启」，未运行只显示「启动浏览器」，避免无意义按钮
        syncActionButtons(d);

        var ctxRows;
        var contexts = Array.isArray(d.contexts) ? d.contexts : [];
        if (!contexts.length) {
            ctxRows = '<tr><td colspan="6" class="px-3 py-4 text-center text-gray-400">暂无缓存的浏览器上下文</td></tr>';
        } else {
            ctxRows = contexts.map(function (c) {
                return '<tr class="border-t border-gray-100">' +
                    '<td class="px-3 py-2 font-medium text-gray-700 break-all">' + escapeHtml(c.key || "-") + '</td>' +
                    '<td class="px-3 py-2">' + escapeHtml(String(c.count == null ? 0 : c.count)) + '</td>' +
                    '<td class="px-3 py-2 text-gray-500 break-all">' + escapeHtml(c.threadName || "-") + '</td>' +
                    '<td class="px-3 py-2 text-gray-500 whitespace-nowrap">' + escapeHtml(c.lastTime || "-") + '</td>' +
                    '<td class="px-3 py-2 text-gray-500">' + escapeHtml(shortUa(c.userAgent)) + '</td>' +
                    '<td class="px-3 py-2">' + (c.cookieConfigured ? '<span class="text-green-600">已设置</span>' : '<span class="text-gray-400">未设置</span>') + '</td>' +
                    '</tr>';
            }).join("");
        }

        var idleText = "-";
        if (!running) {
            idleText = "已关闭";
        } else if (Number(d.idleCloseRemainSec) > 0) {
            idleText = formatDuration(d.idleCloseRemainSec) + "后自动关闭";
        } else {
            idleText = "无（有上下文在使用）";
        }

        root.innerHTML =
            '<div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">' +
            statCard("浏览器状态", running ? "运行中" : "已关闭", running ? "text-green-600" : "text-red-500") +
            statCard("运行模式", headlessText) +
            statCard("上下文缓存", (d.cacheSize == null ? 0 : d.cacheSize) + " 个") +
            statCard("累计取用次数", String(d.totalRequests == null ? 0 : d.totalRequests)) +
            '</div>' +
            '<div class="rounded-xl border border-gray-200 bg-gray-50/60 px-4 py-3 mb-4 text-xs text-gray-600 leading-6">' +
            '<div><span class="text-gray-400">Playwright 版本：</span>' + secureText(d.playwrightVersion) +
            '<span class="mx-3 text-gray-300">|</span><span class="text-gray-400">缓存版本号：</span>' + escapeHtml(String(d.cacheVersion == null ? "-" : d.cacheVersion)) + '</div>' +
            '<div><span class="text-gray-400">Chromium 路径：</span><span class="break-all">' + secureText(d.chromiumPath, running ? "未知" : "未启动") + '</span></div>' +
            '<div><span class="text-gray-400">启动时间：</span>' + escapeHtml(d.browserStartTime || "-") +
            '<span class="mx-3 text-gray-300">|</span><span class="text-gray-400">已运行：</span>' + escapeHtml(running ? formatDuration(d.browserUptimeSec) : "-") + '</div>' +
            '<div><span class="text-gray-400">空闲回收：</span>' + escapeHtml(idleText) + '</div>' +
            '</div>' +
            '<div class="rounded-xl border border-gray-200 overflow-hidden">' +
            '<div class="px-4 py-2.5 bg-gray-50 border-b border-gray-200 text-xs font-medium text-gray-600">浏览器上下文缓存（按房间复用，3 秒自动刷新）</div>' +
            '<div class="overflow-x-auto"><table class="w-full text-xs">' +
            '<thead class="bg-white text-gray-500"><tr>' +
            '<th class="px-3 py-2 text-left font-medium">标识</th>' +
            '<th class="px-3 py-2 text-left font-medium">页面数</th>' +
            '<th class="px-3 py-2 text-left font-medium">使用线程</th>' +
            '<th class="px-3 py-2 text-left font-medium">最后使用</th>' +
            '<th class="px-3 py-2 text-left font-medium">UA</th>' +
            '<th class="px-3 py-2 text-left font-medium">Cookie</th>' +
            '</tr></thead>' +
            '<tbody>' + ctxRows + '</tbody></table></div>' +
            '</div>';
    }

    /**
     * 状态查询优先带秘钥令牌（静默，不弹验证框）：后端据此判断是否脱敏，
     * 验证过操作秘钥后才能看到 Playwright 版本 / Chromium 路径的真实值。
     * 没有有效秘钥令牌时退回普通令牌（接口仍可访问，只是这些字段显示 ***）。
     */
    async function loadBrowserStatus(showError) {
        if (loading) return;
        loading = true;
        try {
            var msg = await fetchStatusWithSecretToken();
            if (!msg) {
                msg = await getRequest("info/browser", null, 8000, "normal");
            }
            if (!msg || !msg.success) {
                if (showError) notifyDialog((msg && msg.message) || "浏览器组件状态获取失败", {title:"浏览器组件", tone:"danger"});
                return;
            }
            renderStatus(msg.data || {});
        } catch (e) {
            if (showError) {
                console.warn("浏览器组件状态获取失败", e);
            }
        } finally {
            loading = false;
        }
    }

    async function fetchStatusWithSecretToken() {
        // 只有本次会话亲手验证过秘钥才用秘钥令牌取真实数据；
        // 记住的令牌不算已验证；旧版 util.js 没有该函数时按未验证处理（宁可脱敏也不泄露）
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
            var res = await fetch(base + "info/browser", {
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
        if (action === "restart") return "重启 Playwright 浏览器（会关闭现有上下文并立即重建）";
        if (action === "close") return "关闭 Playwright 浏览器进程（下次解析/监听会按需重新启动）";
        return "切换浏览器运行模式";
    }

    async function runBrowserAction(action, params) {
        var label = actionLabelOf(action);
        var path = action === "close" ? "action/closeBrowser"
            : (action === "restart" ? "action/restartBrowser" : "action/setHeadless");
        try {
            var key = await confirmSubmitAuth(label);
            if (key === null) return;
            var body = params || {};
            var msg = await postRequest(path + "?actionKey=" + encodeURIComponent(key), body, 30000, "private");
            if (!msg || !msg.success) {
                notifyDialog((msg && msg.message) || "操作失败", {title:"浏览器组件", tone:"danger"});
                return;
            }
            notifyDialog(msg.message || "操作成功", {title:"浏览器组件", tone:"success"});
            await loadBrowserStatus(true);
            if (typeof window.refreshBrowserComponentBadge === "function") {
                window.refreshBrowserComponentBadge();
            }
        } catch (e) {
            console.error("浏览器组件操作失败", e);
        }
    }

    window.browserPanel = function () {
        var btns = setModalContent(
            "Playwright 浏览器组件",
            '<div id="browserPanelRoot" class="text-sm text-gray-700">' +
            '<div class="py-8 text-center text-gray-400">正在读取浏览器组件状态…</div></div>',
            [
                {
                    text: "刷新状态",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: function () {
                        loadBrowserStatus(true);
                    }
                },
                {
                    text: "关闭浏览器",
                    className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                    onClick: function () {
                        runBrowserAction("close");
                    }
                },
                {
                    text: "重启浏览器",
                    className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                    onClick: function () {
                        runBrowserAction("restart");
                    }
                }
            ],
            { maxWidth: "min(64rem, 95vw)", hint: "状态每 3 秒自动刷新" }
        );

        // btns 顺序：0 刷新状态、1 关闭浏览器、2 重启/启动浏览器
        actionButtons.close = btns && btns[1] ? btns[1] : null;
        actionButtons.restart = btns && btns[2] ? btns[2] : null;

        // 无头/有头切换按钮放到最前面，文案随当前模式变化
        var footer = document.getElementById("modalFooter");
        if (footer && !footer.querySelector("#browserToggleHeadlessBtn")) {
            var toggle = document.createElement("button");
            toggle.id = "browserToggleHeadlessBtn";
            toggle.className = "btn bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400";
            toggle.addEventListener("click", function () {
                var current = !!(lastStatus && lastStatus.headless);
                runBrowserAction("headless", { headless: !current });
            });
            footer.insertBefore(toggle, footer.firstChild);
            actionButtons.toggle = toggle;
        }

        // 打开时先按“未运行”收敛，状态读取完成后再按真实状态切换按钮
        lastStatus = null;
        syncActionButtons(null);

        window.onModalDismiss(stopAutoRefresh);
        loadBrowserStatus(true);
        startAutoRefresh();
    };
})();
