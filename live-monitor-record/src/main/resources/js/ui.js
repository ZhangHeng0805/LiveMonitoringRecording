/**
 * 统一弹窗组件：替代原生 alert / confirm / prompt
 * - 纯 JS + 内联样式，不依赖 Tailwind，所有页面通用
 * - UI.alert(message, opts)      -> Promise<void>
 * - UI.confirm(message, opts)    -> Promise<boolean>
 * - UI.prompt(message, opts)     -> Promise<string|null>
 * - 多次调用自动排队，避免弹窗互相覆盖
 */
(function () {
    if (window.UI && window.UI.__lmr) return;

    const STYLE_ID = "lmr-ui-style";

    function injectStyle() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement("style");
        style.id = STYLE_ID;
        style.textContent = `
.lmr-ui-mask{position:fixed;inset:0;z-index:2147483000;display:flex;align-items:center;justify-content:center;
  padding:24px;background:rgba(17,24,39,.46);backdrop-filter:blur(2px);animation:lmr-ui-fade .16s ease-out;}
.lmr-ui-card{width:min(420px,100%);background:#fff;border-radius:14px;box-shadow:0 24px 60px rgba(15,23,42,.24);
  padding:20px 22px 18px;color:#1f2430;font-size:14px;line-height:1.6;
  animation:lmr-ui-pop .18s cubic-bezier(.22,1,.36,1);}
.lmr-ui-head{display:flex;align-items:center;gap:9px;margin-bottom:10px;}
.lmr-ui-ico{width:26px;height:26px;border-radius:50%;display:flex;align-items:center;justify-content:center;
  font-size:13px;font-weight:700;flex:0 0 auto;background:rgba(99,102,241,.12);color:#6366f1;}
.lmr-ui-ico.is-danger{background:rgba(220,38,38,.12);color:#dc2626;}
.lmr-ui-ico.is-warn{background:rgba(217,119,6,.12);color:#d97706;}
.lmr-ui-title{font-size:15px;font-weight:700;color:#1f2430;}
.lmr-ui-body{white-space:pre-wrap;word-break:break-word;color:#5b6272;max-height:46vh;overflow:auto;}
.lmr-ui-body.is-strong{color:#1f2430;}
.lmr-ui-input{width:100%;margin-top:12px;padding:9px 12px;border:1px solid #ecedf1;border-radius:9px;
  font-size:14px;color:#1f2430;outline:none;box-sizing:border-box;transition:border-color .15s,box-shadow .15s;}
.lmr-ui-input:focus{border-color:#6366f1;box-shadow:0 0 0 3px rgba(99,102,241,.16);}
.lmr-ui-msg{min-height:16px;margin-top:6px;font-size:12px;color:#dc2626;}
.lmr-ui-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px;}
.lmr-ui-btn{min-width:82px;height:34px;padding:0 16px;border-radius:9px;border:1px solid #ecedf1;background:#fff;
  color:#5b6272;font-size:13px;font-weight:600;cursor:pointer;transition:all .15s;}
.lmr-ui-btn:hover{background:#f6f7fa;color:#1f2430;}
.lmr-ui-btn.is-primary{background:#6366f1;border-color:#6366f1;color:#fff;}
.lmr-ui-btn.is-primary:hover{background:#4f46e5;border-color:#4f46e5;color:#fff;}
.lmr-ui-btn.is-danger{background:#dc2626;border-color:#dc2626;color:#fff;}
.lmr-ui-btn.is-danger:hover{background:#b91c1c;border-color:#b91c1c;color:#fff;}
.lmr-ui-btn:focus-visible{outline:2px solid rgba(99,102,241,.45);outline-offset:1px;}
@keyframes lmr-ui-fade{from{opacity:0}to{opacity:1}}
@keyframes lmr-ui-pop{from{opacity:0;transform:translateY(8px) scale(.98)}to{opacity:1;transform:none}}
@media (prefers-reduced-motion: reduce){.lmr-ui-mask,.lmr-ui-card{animation:none}}
`;
        document.head.appendChild(style);
    }

    const ICONS = {
        info: "i",
        success: "✓",
        warn: "!",
        danger: "!",
        question: "?"
    };

    function createDialog(opts) {
        injectStyle();
        const kind = opts.kind || "alert";
        const tone = opts.tone || (kind === "confirm" ? "question" : "info");
        const isDanger = tone === "danger" || (kind === "confirm" && opts.danger !== false && !!opts.danger);
        const mask = document.createElement("div");
        mask.className = "lmr-ui-mask";
        const card = document.createElement("div");
        card.className = "lmr-ui-card";
        card.setAttribute("role", "dialog");
        card.setAttribute("aria-modal", "true");

        const head = document.createElement("div");
        head.className = "lmr-ui-head";
        const ico = document.createElement("span");
        ico.className = "lmr-ui-ico" + (isDanger ? " is-danger" : tone === "warn" ? " is-warn" : "");
        ico.textContent = ICONS[tone] || ICONS.info;
        const title = document.createElement("span");
        title.className = "lmr-ui-title";
        title.textContent = opts.title || (kind === "confirm" ? "确认操作" : kind === "prompt" ? "请输入" : "提示");
        head.appendChild(ico);
        head.appendChild(title);

        const body = document.createElement("div");
        body.className = "lmr-ui-body" + (opts.strong ? " is-strong" : "");
        body.textContent = opts.message || "";

        const actions = document.createElement("div");
        actions.className = "lmr-ui-actions";

        let input = null;
        let error = null;
        if (kind === "prompt") {
            input = document.createElement("input");
            input.className = "lmr-ui-input";
            input.type = opts.inputType || "text";
            input.placeholder = opts.placeholder || "";
            input.autocomplete = "off";
            input.spellcheck = false;
            if (opts.value) input.value = opts.value;
            error = document.createElement("div");
            error.className = "lmr-ui-msg";
        }

        const cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "lmr-ui-btn";
        const okBtn = document.createElement("button");
        okBtn.type = "button";
        okBtn.className = "lmr-ui-btn " + (isDanger ? "is-danger" : "is-primary");

        if (kind === "alert") {
            okBtn.textContent = opts.okText || "知道了";
        } else {
            cancelBtn.textContent = opts.cancelText || "取消";
            okBtn.textContent = opts.okText || (kind === "prompt" ? "确定" : "确定");
        }

        card.appendChild(head);
        card.appendChild(body);
        if (input) {
            card.appendChild(input);
            card.appendChild(error);
        }
        actions.appendChild(cancelBtn);
        actions.appendChild(okBtn);
        card.appendChild(actions);
        mask.appendChild(card);

        return {mask: mask, card: card, input: input, error: error, okBtn: okBtn, cancelBtn: cancelBtn, isDanger: isDanger};
    }

    function openDialog(opts) {
        return new Promise(function (resolve) {
            const dlg = createDialog(opts);
            const kind = opts.kind;
            let settled = false;
            let lastFocus = document.activeElement;

            function close(result) {
                if (settled) return;
                settled = true;
                document.removeEventListener("keydown", onKeydown, true);
                dlg.mask.remove();
                if (lastFocus && typeof lastFocus.focus === "function") {
                    try { lastFocus.focus({preventScroll: true}); } catch (e) { /* ignore */ }
                }
                resolve(result);
            }

            function cancelValue() {
                return kind === "confirm" ? false : kind === "prompt" ? null : undefined;
            }

            function submit() {
                if (kind === "prompt") {
                    const value = dlg.input.value.trim();
                    if (opts.required && !value) {
                        dlg.error.textContent = opts.requiredMessage || "内容不能为空";
                        dlg.input.focus();
                        return;
                    }
                    if (typeof opts.validate === "function") {
                        const ret = opts.validate(value);
                        if (ret) {
                            dlg.error.textContent = String(ret);
                            dlg.input.focus();
                            return;
                        }
                    }
                    close(value);
                    return;
                }
                if (kind === "confirm") {
                    close(true);
                    return;
                }
                close(undefined);
            }

            function onKeydown(e) {
                if (e.key === "Escape") {
                    e.preventDefault();
                    close(cancelValue());
                } else if (e.key === "Enter") {
                    // 避免在输入法组词阶段回车误提交
                    if (e.isComposing) return;
                    e.preventDefault();
                    submit();
                }
            }

            dlg.okBtn.addEventListener("click", submit);
            dlg.cancelBtn.addEventListener("click", function () { close(cancelValue()); });
            dlg.mask.addEventListener("mousedown", function (e) {
                if (e.target === dlg.mask) close(cancelValue());
            });
            document.addEventListener("keydown", onKeydown, true);
            document.body.appendChild(dlg.mask);

            setTimeout(function () {
                if (dlg.input) {
                    dlg.input.focus();
                    try { dlg.input.select(); } catch (e) { /* ignore */ }
                } else {
                    (dlg.isDanger ? dlg.cancelBtn : dlg.okBtn).focus();
                }
            }, 50);
        });
    }

    // 串行队列：多个弹窗依次展示，不互相覆盖
    let queue = Promise.resolve();
    function enqueue(factory) {
        const run = queue.then(factory, factory);
        queue = run.catch(function () { /* 单个弹窗失败不影响后续 */ });
        return run;
    }

    const UI = {
        __lmr: true,
        alert: function (message, opts) {
            opts = opts || {};
            return enqueue(function () {
                return openDialog(Object.assign({}, opts, {kind: "alert", message: message}));
            });
        },
        confirm: function (message, opts) {
            opts = opts || {};
            return enqueue(function () {
                return openDialog(Object.assign({}, opts, {kind: "confirm", message: message}));
            });
        },
        prompt: function (message, opts) {
            opts = opts || {};
            return enqueue(function () {
                return openDialog(Object.assign({}, opts, {kind: "prompt", message: message}));
            });
        }
    };

    window.UI = UI;
    window.uiAlert = function (message, opts) { return UI.alert(message, opts); };
    window.uiConfirm = function (message, opts) { return UI.confirm(message, opts); };
    window.uiPrompt = function (message, opts) { return UI.prompt(message, opts); };
})();
