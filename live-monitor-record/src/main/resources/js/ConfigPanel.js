/**
 * 监听面板 · 系统配置（xxxr.setting）
 * 读取 / 编辑 / 保存工作目录下的配置文件：
 * - 读取、保存都走私密接口（需操作秘钥验证）
 * - 保存前由后端校验 Properties 格式并备份 .bak，保存后立即重载配置
 */
(function () {
    var currentKey = null;
    var originalContent = "";
    var currentPath = "";
    var saving = false;

    function modalBody() {
        return document.getElementById("modalBody");
    }

    function renderEditor(content, path, lastModified, charset) {
        var body = modalBody();
        if (!body) return;
        originalContent = content || "";
        currentPath = path || "";
        var timeText = lastModified ? new Date(Number(lastModified)).toLocaleString() : "-";
        var charsetText = charset || "UTF-8";
        body.innerHTML =
            '<div class="mb-2 text-xs text-gray-500 leading-5">' +
            '<div>文件：<span class="break-all text-gray-700">' + escapeHtml(currentPath || "-") + '</span></div>' +
            '<div>文件编码：' + escapeHtml(charsetText) +
            (charsetText.toUpperCase() !== "UTF-8" ? '<span class="text-orange-500">（保存后统一转为 UTF-8）</span>' : '') +
            '</div>' +
            '<div>最后修改：' + escapeHtml(timeText) + '<span class="mx-2 text-gray-300">|</span>' +
            '保存后立即重载配置，端口、运行模式等需重启程序生效</div>' +
            '</div>' +
            '<textarea id="systemConfigText" spellcheck="false" autocomplete="off" ' +
            'class="w-full h-[52vh] p-3 rounded-lg border border-gray-200 font-mono text-xs leading-5 text-gray-700 ' +
            'focus:outline-none focus:ring-2 focus:ring-primary/40 resize-y"></textarea>';
        var textarea = document.getElementById("systemConfigText");
        textarea.value = originalContent;
    }

    function readEditor() {
        var textarea = document.getElementById("systemConfigText");
        return textarea ? textarea.value : null;
    }

    async function loadConfig(showError) {
        try {
            var msg = await getRequest("config/raw", {actionKey: currentKey}, 10000, "private");
            if (!msg || !msg.success) {
                if (showError) notifyDialog((msg && msg.message) || "读取配置文件失败", {title: "系统配置", tone: "danger"});
                return;
            }
            renderEditor((msg.data && msg.data.content) || "", msg.data && msg.data.path, msg.data && msg.data.lastModified, msg.data && msg.data.charset);
        } catch (e) {
            if (showError) notifyDialog("读取配置文件失败 - " + (e && e.message ? e.message : e), {title: "系统配置", tone: "danger"});
        }
    }

    async function saveConfig() {
        if (saving) return;
        var content = readEditor();
        if (content === null) return;
        if (!content.trim()) {
            notifyDialog("配置内容不能为空", {title: "无法保存", tone: "warn"});
            return;
        }
        saving = true;
        try {
            var msg = await postRequest("config/save?actionKey=" + encodeURIComponent(currentKey),
                {content: content}, 15000, "private");
            if (msg && msg.success) {
                notifyDialog(msg.message || "保存成功", {title: "保存成功", tone: "success"});
                await loadConfig(false);
            } else {
                notifyDialog((msg && msg.message) || "保存失败", {title: "保存失败", tone: "danger"});
            }
        } catch (e) {
            notifyDialog("保存失败 - " + (e && e.message ? e.message : e), {title: "保存失败", tone: "danger"});
        } finally {
            saving = false;
        }
    }

    async function reloadWithConfirm() {
        var content = readEditor();
        if (content !== null && content !== originalContent) {
            var ok = await confirmDialog("刷新会丢弃当前未保存的修改，继续刷新吗？", {title: "刷新配置"});
            if (!ok) return;
        }
        await loadConfig(true);
    }

    window.systemConfig = function () {
        // 与回收站一致：已验证过直接打开；没验证过只弹一次秘钥，取消静默退出，不做二次确认
        requireVerified(function (key) {
            currentKey = key || "";
            setModalContent(
                "系统配置（xxxr.setting）",
                '<div class="py-8 text-center text-gray-400">正在读取配置文件…</div>',
                [
                    {
                        text: "刷新",
                        className: "bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-400",
                        onClick: reloadWithConfirm
                    },
                    {
                        text: "保存并重载",
                        className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
                        onClick: saveConfig
                    }
                ],
                {maxWidth: "min(72rem, 96vw)", hint: "私密操作 · 需操作秘钥"}
            );
            loadConfig(true);
        });
    };
})();
