/**
 * 直播记录文件浏览器
 * 结构：工具栏（搜索 / 刷新 / 数量） + 可独立滚动的目录树 + 底部按钮
 * 交互：目录懒加载、分页「加载更多」、文件名过长省略并保留完整 title、按类型着色图标
 */

// 每个文件类型的图标与配色（仅用于视觉区分，不影响下载行为）
const FILE_KIND_MAP = {
  log: { icon: "fa-file-text-o", color: "text-sky-500", label: "日志" },
  txt: { icon: "fa-file-text-o", color: "text-sky-500", label: "文本" },
  jpg: { icon: "fa-file-image-o", color: "text-emerald-500", label: "图片" },
  jpeg: { icon: "fa-file-image-o", color: "text-emerald-500", label: "图片" },
  png: { icon: "fa-file-image-o", color: "text-emerald-500", label: "图片" },
  gif: { icon: "fa-file-image-o", color: "text-emerald-500", label: "图片" },
  mp4: { icon: "fa-file-video-o", color: "text-rose-500", label: "视频" },
  flv: { icon: "fa-file-video-o", color: "text-rose-500", label: "视频" },
  ts: { icon: "fa-file-video-o", color: "text-rose-500", label: "视频" },
  mp3: { icon: "fa-file-audio-o", color: "text-amber-500", label: "音频" },
  json: { icon: "fa-file-code-o", color: "text-violet-500", label: "配置" },
};

function fileKind(name) {
  const ext = (name.split(".").pop() || "").toLowerCase();
  return FILE_KIND_MAP[ext] || { icon: "fa-file-o", color: "text-gray-400", label: "" };
}

/** 文件大小：按量级统一小数位，避免 1.39 KB 与 118.67 KB 混排 */
function formatSize(bytes) {
  const n = Number(bytes);
  if (!n || n < 0) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  let value = n;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  const digits = i === 0 ? 0 : value >= 100 ? 1 : 2;
  return value.toFixed(digits) + " " + units[i];
}

/** 目录只显示「月-日」，避免把整串日期时间挤成两行 */
function formatFolderDate(value) {
  const date = new Date(value);
  if (!value || isNaN(date.getTime())) return "";
  const pad = function (n) { return n < 10 ? "0" + n : String(n); };
  return pad(date.getMonth() + 1) + "-" + pad(date.getDate());
}

/** 把 DouYin-541976200890 这类房间标识拆成「平台 + 房间号」用于标题提示 */
function describeRoomKey(key) {
  const raw = String(key || "");
  const idx = raw.indexOf("-");
  if (idx < 0) return raw;
  const platformMap = {
    DouYin: "抖音",
    Bili: "哔哩哔哩",
    Bilibili: "哔哩哔哩",
    KuaiShou: "快手",
  };
  const platform = platformMap[raw.substring(0, idx)] || raw.substring(0, idx);
  return platform + " · " + raw.substring(idx + 1);
}

/** 弹窗内样式（frx- 前缀，避免影响页面其它元素） */
const FILE_TREE_STYLE = `
<style>
  .frx-toolbar { display: flex; align-items: center; gap: .5rem; padding: 0 0 .75rem; flex-wrap: wrap; }
  .frx-search { position: relative; flex: 1 1 220px; min-width: 180px; }
  .frx-search i { position: absolute; left: .6rem; top: 50%; transform: translateY(-50%); color: #9ca3af; font-size: .8rem; }
  .frx-search input { width: 100%; padding: .45rem .6rem .45rem 1.7rem; font-size: .82rem; border: 1px solid #e5e7eb;
    border-radius: .5rem; outline: none; background: #fff; transition: border-color .15s, box-shadow .15s; }
  .frx-search input:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,.12); }
  .frx-btn { display: inline-flex; align-items: center; gap: .35rem; padding: .45rem .75rem; font-size: .8rem;
    color: #374151; background: #f3f4f6; border: 1px solid #e5e7eb; border-radius: .5rem; cursor: pointer; transition: all .15s; }
  .frx-btn:hover { background: #e5e7eb; color: #111827; }
  .frx-meta { font-size: .75rem; color: #9ca3af; margin-left: auto; white-space: nowrap; }
  .frx-tree { max-height: min(60vh, 560px); overflow: auto; border: 1px solid #f1f5f9; border-radius: .6rem;
    padding: .35rem; background: #fff; }
  .frx-tree::-webkit-scrollbar { width: 8px; height: 8px; }
  .frx-tree::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }
  .frx-tree::-webkit-scrollbar-thumb:hover { background: #9ca3af; }
  .frx-row { display: flex; align-items: center; gap: .5rem; padding: .35rem .5rem; border-radius: .4rem;
    font-size: .82rem; color: #374151; text-decoration: none; cursor: pointer; transition: background .12s; }
  .frx-row:hover { background: #f1f5ff; }
  /* 文件行 + 单场统计图按钮：a 标签里不能嵌 button，所以外面再套一层 */
  .frx-filewrap { display: flex; align-items: center; gap: .1rem; }
  .frx-filewrap > .frx-row { flex: 1 1 auto; min-width: 0; }
  .frx-filewrap > .frx-chart-btn { margin-left: 0; }
  /* 没有统计图按钮的文件行补一个等宽占位，保证右侧大小文字对齐 */
  .frx-chart-spacer { flex: 0 0 auto; width: 1.5rem; height: 1.5rem; }
  .frx-name { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .frx-size { flex: 0 0 auto; width: 5.5rem; text-align: right; font-size: .74rem; color: #9ca3af;
    font-variant-numeric: tabular-nums; }
  .frx-chevron { width: .85rem; color: #9ca3af; font-size: .7rem; transition: transform .18s; }
  .frx-chevron.open { transform: rotate(90deg); }
  .frx-folder .frx-name { font-weight: 500; color: #1f2937; }
  .frx-child { margin-left: 1.15rem; padding-left: .6rem; border-left: 1px dashed #e5e7eb; }
  .frx-child[hidden] { display: none; }
  .frx-more { margin: .35rem 0 0 1.15rem; padding: .4rem .75rem; font-size: .78rem; color: #2563eb;
    background: #eff6ff; border: 1px dashed #bfdbfe; border-radius: .5rem; cursor: pointer; }
  .frx-more:hover { background: #dbeafe; }
  .frx-empty { padding: 1.75rem .75rem; text-align: center; color: #9ca3af; font-size: .84rem; }
  .frx-empty i { display: block; font-size: 1.6rem; margin-bottom: .5rem; color: #e5e7eb; }
  .frx-mask { display: none; }
  .frx-tip { font-size: .74rem; color: #9ca3af; margin-right: auto; }

  /* 目录行尾的「统计图」按钮 */
  .frx-chart-btn { flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center;
    width: 1.5rem; height: 1.5rem; margin-left: .15rem; padding: 0; font-size: .78rem; color: #6366f1;
    background: transparent; border: 1px solid transparent; border-radius: .35rem; cursor: pointer;
    transition: all .15s; }
  .frx-chart-btn:hover { background: #eef2ff; border-color: #dbe2ff; color: #4338ca; }

  /* 统计图面板 */
  .frx-chart { border: 1px solid #f1f5f9; border-radius: .6rem; background: #fff; padding: .65rem .75rem .7rem; }
  .frx-chart[hidden] { display: none; }
  .frx-chart-head { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
  .frx-chart-title { font-size: .9rem; font-weight: 600; color: #1f2937; display: inline-flex; align-items: center; gap: .35rem; }
  .frx-chart-title i { color: #6366f1; }
  .frx-chart-sub { font-size: .74rem; color: #9ca3af; }
  .frx-chart-back { margin-left: auto; }
  .frx-chart-legend { display: flex; align-items: center; gap: .9rem; flex-wrap: wrap; margin: .45rem 0 .15rem; }
  .frx-chart-legend span { display: inline-flex; align-items: center; gap: .3rem; font-size: .74rem; color: #6b7280; }
  .frx-chart-legend i { width: .55rem; height: .55rem; border-radius: 50%; display: inline-block; }
  .frx-chart-legend b { font-weight: 600; color: #374151; font-variant-numeric: tabular-nums; }
  .frx-canvas { display: block; width: 100%; height: 260px; margin-top: .35rem; cursor: crosshair; }
  .frx-chart-stats { display: flex; align-items: center; gap: .45rem; flex-wrap: wrap; margin-top: .5rem; }
  .frx-chip { font-size: .73rem; color: #475569; background: #f8fafc; border: 1px solid #eef2f7;
    border-radius: .4rem; padding: .18rem .45rem; font-variant-numeric: tabular-nums; }
  .frx-chip b { color: #111827; font-weight: 600; }
  .frx-chart-empty { padding: 2.2rem .75rem; text-align: center; color: #9ca3af; font-size: .84rem; }
  .frx-chart-empty > i { display: block; font-size: 1.6rem; margin-bottom: .5rem; color: #c7d2fe; }
  .frx-chart-empty-title { font-size: .88rem; color: #374151; font-weight: 600; }
  .frx-chart-empty-sub { font-size: .76rem; color: #9ca3af; margin-top: .3rem; }
  .frx-chart-empty .frx-chart-back { display: inline-flex; margin-top: .9rem; }
  /* 窄屏：文件名改为换行完整显示，避免只露出「2026-06-18 06-15-51封...」 */
  @media (max-width: 640px) {
    .frx-tree { max-height: min(58vh, 460px); }
    .frx-row { align-items: flex-start; padding: .4rem .45rem; }
    .frx-chevron { margin-top: .18rem; }
    .frx-chevron + i, .frx-row > i:first-child { margin-top: .15rem; }
    .frx-name { white-space: normal; overflow: visible; text-overflow: clip;
      word-break: break-word; line-height: 1.35; }
    .frx-size { width: 4rem; padding-top: .12rem; font-size: .7rem; }
    .frx-child { margin-left: .55rem; padding-left: .45rem; }
    .frx-more { margin-left: .55rem; }
    .frx-canvas { height: 200px; }
  }
</style>`;

/** 从房间缓存里取博主昵称（弹窗可能从回收站等入口打开，取不到就返回空） */
function roomNicknameOf(key) {
  try {
    if (typeof roomCache !== "undefined" && roomCache && typeof roomCache.get === "function") {
      const item = roomCache.get(key);
      if (item) {
        const room = item.room || {};
        if (room.nickname) return String(room.nickname);
        if (item.nickname) return String(item.nickname);
      }
    }
  } catch (e) {
    // 预览页 / 其它入口没有房间缓存，忽略即可
  }
  return "";
}

/** 打开直播记录弹窗 */
function showFileSelectModal(key) {
  const safeKey = escapeHtml(key);
  const nickname = roomNicknameOf(key);
  const roomHint = (nickname ? nickname + " · " : "") + describeRoomKey(key);
  setModalContent(
      "直播记录",
      `${FILE_TREE_STYLE}
      <div class="frx">
        <div class="frx-toolbar">
          <div class="frx-search">
            <i class="fa fa-search"></i>
            <input id="frxSearch" type="search" placeholder="搜索已加载的文件名" autocomplete="off">
          </div>
          <button id="frxRefresh" class="frx-btn" type="button">
            <i class="fa fa-rotate-right"></i>刷新
          </button>
          <span id="frxMeta" class="frx-meta"></span>
        </div>
        <div id="frxTree" class="frx-tree" data-key="${safeKey}" data-current-page="1" data-page-size="50"></div>
        <div id="frxChart" class="frx-chart" hidden></div>
      </div>`,
      [
        {
          text: "关闭",
          className: "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
          onClick: closeModal,
        },
      ],
      {
        maxWidth: "min(64rem, 94vw)",
        hint: roomHint,
      }
  );

  const treeDom = document.getElementById("frxTree");
  const searchDom = document.getElementById("frxSearch");
  const refreshDom = document.getElementById("frxRefresh");

  // 搜索：只过滤已加载的条目，避免边搜边请求
  if (searchDom) {
    const applyFilter = debounce(function () {
      const keyword = searchDom.value.trim().toLowerCase();
      treeDom.querySelectorAll(".frx-row").forEach(function (row) {
        const name = (row.dataset.name || "").toLowerCase();
        const hit = !keyword || name.indexOf(keyword) > -1;
        row.hidden = !hit;
      });
      treeDom.querySelectorAll(".frx-more").forEach(function (btn) {
        btn.hidden = !!keyword;
      });
    }, 180);
    searchDom.addEventListener("input", applyFilter);
  }

  if (refreshDom) {
    refreshDom.addEventListener("click", function () {
      // 刷新重置分页并重新拉取根目录
      treeDom.dataset.currentPage = "1";
      treeDom.innerHTML = "";
      if (searchDom) searchDom.value = "";
      renderContent(treeDom, "./");
    });
  }

  renderContent(treeDom, "./");
}

/**
 * 请求文件夹内容
 * @param {string} key 房间标识
 * @param {string} path 文件夹路径
 * @param {number} pageNum 页码
 * @param {number} pageSize 每页条数
 * @returns {Promise<{files:Array,total:number}>}
 */
async function fetchFolderContents(key, path, pageNum, pageSize) {
  try {
    const res = await getRequest("fileRes", {
      key: key,
      path: path,
      pageNum: pageNum,
      pageSize: pageSize,
    });
    return res.data || { files: [], total: 0, pageNum: 1, pageSize: pageSize };
  } catch (error) {
    return { files: [], total: 0, pageNum: 1, pageSize: pageSize, error: String(error) };
  }
}

/**
 * 渲染目录内容
 * @param {HTMLElement} boxDom 容器（根部或某个文件夹的子容器）
 * @param {string} path 目录路径
 * @param {boolean} isAppend 是否追加（分页加载更多）
 */
async function renderContent(boxDom, path, isAppend) {
  const key = document.getElementById("frxTree").dataset.key;
  const pageSize = parseInt(boxDom.dataset.pageSize || "50", 10);
  let currentPage = parseInt(boxDom.dataset.currentPage || "1", 10);
  const isRoot = boxDom.id === "frxTree";

  if (!isAppend) {
    currentPage = 1;
    boxDom.dataset.currentPage = "1";
    boxDom.innerHTML = `<div class="frx-empty"><i class="fa fa-spinner fa-spin"></i>正在读取目录…</div>`;
  }

  const pageData = await fetchFolderContents(key, path, currentPage, pageSize);
  const files = pageData.files || [];
  const total = Number(pageData.total) || 0;
  const totalPage = pageSize > 0 ? Math.ceil(total / pageSize) : 1;

  // 非追加模式先清掉 loading 占位，避免它残留在列表上方
  if (!isAppend) boxDom.innerHTML = "";

  if (!isAppend && files.length === 0) {
    boxDom.innerHTML = pageData.error
        ? `<div class="frx-empty"><i class="fa fa-exclamation-triangle"></i>读取失败，请检查接口权限后重试</div>`
        : `<div class="frx-empty"><i class="fa fa-folder-open-o"></i>这个目录还没有录制文件</div>`;
    if (isRoot) updateMeta(0);
    return;
  }

  let html = "";
  files.forEach(function (item) {
    if (item.type === "folder") {
      html += `
      <div class="frx-folder">
        <div class="frx-row frx-folder-toggle" data-name="${escapeHtml(item.name)}" data-fid="${escapeHtml(item.path)}">
          <i class="fa fa-chevron-right frx-chevron"></i>
          <i class="fa fa-folder text-amber-400"></i>
          <span class="frx-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</span>
          <span class="frx-size">${formatFolderDate(item.lastModified)}</span>
          <button type="button" class="frx-chart-btn" title="查看该日期全部场次合并的统计图"
                  data-name="${escapeHtml(item.name)}" data-fid="${escapeHtml(item.path)}">
            <i class="fa fa-line-chart"></i>
          </button>
        </div>
        <div class="frx-child" data-path="${escapeHtml(item.path)}" data-current-page="1" data-page-size="50" hidden></div>
      </div>`;
    } else {
      const kind = fileKind(item.name);
      const href = withApiToken(
          `${MainUrl}fileRes?key=${encodeURIComponent(key)}&path=${encodeURIComponent(item.path)}`
      );
      // 监听日志单独挂一个统计图按钮：每场直播一张图
      const isLog = /监听\.log$/i.test(item.name);
      const chartBtn = isLog ? `
          <button type="button" class="frx-chart-btn frx-chart-file" title="查看本场直播的统计图"
                  data-name="${escapeHtml(item.name.replace(/监听\.log$/i, ""))}"
                  data-fid="${escapeHtml(item.path)}">
            <i class="fa fa-line-chart"></i>
          </button>` : `<span class="frx-chart-spacer"></span>`;
      html += `
      <div class="frx-filewrap">
        <a class="frx-row frx-file" href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer"
           data-name="${escapeHtml(item.name)}" title="${escapeHtml(item.name)}">
          <i class="frx-chevron"></i>
          <i class="fa ${kind.icon} ${kind.color}"></i>
          <span class="frx-name">${escapeHtml(item.name)}</span>
          <span class="frx-size">${formatSize(item.size)}</span>
        </a>${chartBtn}
      </div>`;
    }
  });

  const oldBtn = boxDom.querySelector(".frx-more");
  if (oldBtn) oldBtn.remove();
  boxDom.insertAdjacentHTML("beforeend", html);

  if (currentPage < totalPage) {
    const moreBtn = document.createElement("div");
    moreBtn.className = "frx-more";
    moreBtn.innerHTML = `<i class="fa fa-arrow-down mr-1"></i>加载更多（已显示 ${files.length} / ${total}）`;
    moreBtn.onclick = async function () {
      const parentBox = this.parentElement;
      let p = parseInt(parentBox.dataset.currentPage, 10);
      parentBox.dataset.currentPage = ++p;
      await renderContent(parentBox, path, true);
    };
    boxDom.appendChild(moreBtn);
  }

  if (isRoot && !isAppend) updateMeta(total);
  bindFolderToggle(boxDom);
  bindChartButtons(boxDom);
}

/** 更新右上角数量提示 */
function updateMeta(count) {
  const meta = document.getElementById("frxMeta");
  if (!meta) return;
  meta.textContent = count > 0 ? `共 ${count} 项` : "";
}

/** 绑定文件夹展开/折叠（懒加载，避免重复绑定） */
function bindFolderToggle(scope) {
  scope.querySelectorAll(".frx-folder-toggle").forEach(function (row) {
    if (row.dataset.bound) return;
    row.dataset.bound = "1";
    row.addEventListener("click", async function () {
      const chevron = this.querySelector(".frx-chevron");
      const folderIcon = this.querySelector(".fa-folder");
      const wrap = this.nextElementSibling;
      const fid = this.dataset.fid;
      const opened = !wrap.hidden;

      if (chevron) chevron.classList.toggle("open", !opened);
      if (folderIcon) {
        folderIcon.classList.toggle("fa-folder-open");
        folderIcon.classList.toggle("fa-folder");
        folderIcon.classList.toggle("text-amber-500");
      }
      wrap.hidden = opened;

      if (!opened && wrap.dataset.loaded !== "1") {
        wrap.dataset.loaded = "1";
        await renderContent(wrap, fid);
      }
    });
  });
}

/** 绑定目录行尾的统计图按钮（阻止冒泡，避免同时展开目录） */
function bindChartButtons(scope) {
  scope.querySelectorAll(".frx-chart-btn").forEach(function (btn) {
    if (btn.dataset.chartBound) return;
    btn.dataset.chartBound = "1";
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      const tree = document.getElementById("frxTree");
      if (!tree) return;
      showFileStats(tree.dataset.key, this.dataset.fid, this.dataset.name);
    });
  });
}

/* ==========================================================================
 * 监听日志统计图
 * 每个录制目录里都有 ***监听.log，后端解析成时间序列，前端用 Canvas 画折线
 * ========================================================================== */

function frxCompact(value) {
  const n = Number(value);
  if (!isFinite(n)) return "0";
  if (n >= 100000000) return (n / 100000000).toFixed(1).replace(/\.0$/, "") + "亿";
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, "") + "万";
  if (n >= 1000) return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return String(Math.round(n));
}

function frxPad(n) {
  return n < 10 ? "0" + n : String(n);
}

/** 时间刻度：跨度超一天时带上「月-日」 */
function frxTimeLabel(ms, spanMs) {
  const d = new Date(ms);
  const hm = frxPad(d.getHours()) + ":" + frxPad(d.getMinutes());
  if (spanMs > 24 * 3600 * 1000) {
    return frxPad(d.getMonth() + 1) + "-" + frxPad(d.getDate()) + " " + hm;
  }
  return hm;
}

/** 打开统计图：隐藏目录树，展示该目录的日志曲线 */
async function showFileStats(key, path, folderName) {
  const chartDom = document.getElementById("frxChart");
  const treeDom = document.getElementById("frxTree");
  if (!chartDom) return;
  chartDom.hidden = false;
  if (treeDom) treeDom.hidden = true;
  const title = escapeHtml(folderName || path || "");
  chartDom.innerHTML = `<div class="frx-chart-empty"><i class="fa fa-spinner fa-spin"></i>正在解析「${title}」的监听日志…</div>`;

  let res;
  try {
    res = await getRequest("fileRes/stats", { key: key, path: path });
  } catch (error) {
    res = { code: 1, message: "请求失败：" + String(error) };
  }
  const data = (res && res.data) || {};
  const series = data.series || [];
  const metrics = data.metrics || [];

  if (!series.length || !metrics.length) {
    const mainMsg = (res && res.title) || "暂无统计数据";
    const subMsg = (res && res.message && res.message !== mainMsg) ? res.message : "";
    chartDom.innerHTML = `
      <div class="frx-chart-empty">
        <i class="fa fa-line-chart"></i>
        <div class="frx-chart-empty-title">${escapeHtml(mainMsg)}</div>
        ${subMsg ? `<div class="frx-chart-empty-sub">${escapeHtml(subMsg)}</div>` : ""}
        <button type="button" class="frx-btn frx-chart-back"><i class="fa fa-arrow-left"></i>返回文件列表</button>
      </div>`;
    chartDom.querySelector(".frx-chart-back").addEventListener("click", function () {
      chartDom.hidden = true;
      if (treeDom) treeDom.hidden = false;
    });
    return;
  }

  const last = series[series.length - 1] || {};
  const legend = metrics.map(function (m) {
    return `<span><i style="background:${m.color}"></i>${escapeHtml(m.label)}<b>${frxCompact(last[m.key])}</b></span>`;
  }).join("");

  const chips = [];
  if (data.maxOnline) chips.push(`峰值在线 <b>${frxCompact(data.maxOnline)}</b>`);
  if (data.maxViewers) chips.push(`峰值观看 <b>${frxCompact(data.maxViewers)}</b>`);
  if (data.lastTotal) chips.push(`总观看 <b>${frxCompact(data.lastTotal)}</b>`);
  if (data.maxLikes) chips.push(`最终点赞 <b>${frxCompact(data.maxLikes)}</b>`);
  if (data.maxGifts) chips.push(`礼物 <b>${frxCompact(data.maxGifts)}</b>`);
  chips.push(`统计点 <b>${series.length}</b>`);
  chips.push(`日志 <b>${(data.files || []).length}</b> 个`);

  chartDom.innerHTML = `
    <div class="frx-chart-head">
      <div class="frx-chart-title"><i class="fa fa-line-chart"></i>${title}</div>
      <div class="frx-chart-sub">${escapeHtml(series[0].time)} → ${escapeHtml(last.time || "")}</div>
      <button type="button" class="frx-btn frx-chart-back"><i class="fa fa-arrow-left"></i>返回文件列表</button>
    </div>
    <div class="frx-chart-legend">${legend}</div>
    <canvas class="frx-canvas"></canvas>
    <div class="frx-chart-stats">${chips.map(c => `<span class="frx-chip">${c}</span>`).join("")}</div>`;

  const canvas = chartDom.querySelector(".frx-canvas");
  chartDom.querySelector(".frx-chart-back").addEventListener("click", function () {
    chartDom.hidden = true;
    if (treeDom) treeDom.hidden = false;
  });

  const state = { data: data, hoverIndex: -1 };
  canvas._frxChart = state;
  drawStatsChart(canvas, state);

  canvas.addEventListener("mousemove", function (e) {
    const geo = state.geo;
    if (!geo) return;
    const rect = canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    let idx = -1;
    let best = Infinity;
    for (let i = 0; i < series.length; i++) {
      const dist = Math.abs(geo.x(series[i].t) - mx);
      if (dist < best) {
        best = dist;
        idx = i;
      }
    }
    state.hoverIndex = best <= 24 ? idx : -1;
    drawStatsChart(canvas, state);
  });

  canvas.addEventListener("mouseleave", function () {
    state.hoverIndex = -1;
    drawStatsChart(canvas, state);
  });

  // 视口变化时重绘（尺寸变化后 canvas 需要重新适配 DPR）
  if (!window.__frxChartResize) {
    window.__frxChartResize = debounce(function () {
      const c = document.querySelector("#frxChart .frx-canvas");
      if (c && c._frxChart) drawStatsChart(c, c._frxChart);
    }, 200);
  }
  window.addEventListener("resize", window.__frxChartResize);
}

/**
 * 画主图表：每个指标一条独立的分量带
 * 在线人数（百级）和点赞/总观看（万级）差几个数量级，共用一根轴会把小曲线压成直线，
 * 所以按指标各分一条横带，各自用自己的值域；时间轴共用，鼠标悬停显示十字线与数值
 */
function drawStatsChart(canvas, state) {
  const data = state.data || {};
  const series = data.series || [];
  const metrics = data.metrics || [];
  if (!series.length || !metrics.length) return;
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const cssW = Math.max(240, Math.round(canvas.getBoundingClientRect().width || 600));
  const cssH = Math.max(160, Math.round(canvas.getBoundingClientRect().height || 260));
  canvas.width = Math.round(cssW * dpr);
  canvas.height = Math.round(cssH * dpr);
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssW, cssH);

  const padL = 54;
  const padR = 12;
  const padT = 8;
  const padB = 24;
  const plotW = cssW - padL - padR;
  const plotH = cssH - padT - padB;

  const t0 = Number(data.start) || series[0].t;
  const t1 = Number(data.end) || series[series.length - 1].t;
  const span = Math.max(1000, t1 - t0);
  const x = function (t) { return padL + ((t - t0) / span) * plotW; };

  const bands = metrics.map(function (m) {
    let lo = Infinity;
    let hi = -Infinity;
    series.forEach(function (p) {
      const v = p[m.key];
      if (v == null) return;
      lo = Math.min(lo, v);
      hi = Math.max(hi, v);
    });
    if (!isFinite(lo)) { lo = 0; hi = 1; }
    if (hi === lo) { hi = lo + 1; }
    const range = hi - lo;
    const rawHi = hi;
    return { metric: m, lo: Math.max(0, lo - range * 0.1), hi: hi + range * 0.1, rawHi: rawHi };
  });
  const bandH = plotH / bands.length;
  const gap = Math.max(15 * 60 * 1000, span / 200);
  ctx.font = "10.5px system-ui, -apple-system, 'Segoe UI', sans-serif";

  bands.forEach(function (b, bi) {
    const top = padT + bi * bandH;
    const bottom = top + bandH;
    const y = function (v) { return bottom - ((v - b.lo) / (b.hi - b.lo)) * bandH; };

    // 分量带底线
    ctx.strokeStyle = bi === bands.length - 1 ? "#e5e7eb" : "#f1f5f9";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padL, bottom);
    ctx.lineTo(padL + plotW, bottom);
    ctx.stroke();

    // 带顶分隔线（第一条也画，让每条带的范围一目了然）
    ctx.strokeStyle = "#eceff4";
    ctx.beginPath();
    ctx.moveTo(padL - 4, top);
    ctx.lineTo(padL + plotW, top);
    ctx.stroke();

    // 本带的值域刻度：画在绘图区外侧，避免压到曲线；带顶再点一小段指标色，方便对上是哪条曲线
    ctx.fillStyle = b.metric.color;
    ctx.fillRect(padL - 50, top + 3, 6, 2);
    ctx.fillStyle = "#9aa3b0";
    ctx.textAlign = "right";
    ctx.textBaseline = "top";
    ctx.fillText(frxCompact(b.rawHi), padL - 8, top + 1);
    ctx.textBaseline = "middle";

    // 折线（相邻点间隔过大视为分场次，断开不连）
    ctx.strokeStyle = b.metric.color;
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    let prevT = null;
    let started = false;
    series.forEach(function (p) {
      const v = p[b.metric.key];
      if (v == null) {
        prevT = null;
        started = false;
        return;
      }
      const px = x(p.t);
      const py = y(v);
      if (!started || prevT == null || p.t - prevT > gap) {
        ctx.moveTo(px, py);
        started = true;
      } else {
        ctx.lineTo(px, py);
      }
      prevT = p.t;
    });
    ctx.stroke();

    // 点数不多时补圆点，便于看单次统计值
    if (series.length <= 120) {
      ctx.fillStyle = b.metric.color;
      series.forEach(function (p) {
        if (p[b.metric.key] == null) return;
        ctx.beginPath();
        ctx.arc(x(p.t), y(p[b.metric.key]), 1.9, 0, Math.PI * 2);
        ctx.fill();
      });
    }
  });

  // X 轴时间刻度（首尾贴边对齐，避免被裁）
  ctx.fillStyle = "#9ca3af";
  ctx.textBaseline = "middle";
  const ticks = 3;
  for (let i = 0; i <= ticks; i++) {
    const t = t0 + (span * i) / ticks;
    const tx = padL + (plotW * i) / ticks;
    ctx.textAlign = i === 0 ? "left" : (i === ticks ? "right" : "center");
    ctx.fillText(frxTimeLabel(t, span), tx, cssH - 11);
  }
  ctx.textAlign = "center";

  // 悬停：竖线 + 数值气泡
  const idx = state.hoverIndex;
  if (idx >= 0 && idx < series.length) {
    const p = series[idx];
    const px = x(p.t);
    ctx.strokeStyle = "rgba(99,102,241,.55)";
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.moveTo(px, padT);
    ctx.lineTo(px, padT + plotH);
    ctx.stroke();
    ctx.setLineDash([]);

    const rows = [p.time].concat(metrics.map(m => m.label + "：" + frxCompact(p[m.key])));
    ctx.font = "10px system-ui, -apple-system, 'Segoe UI', sans-serif";
    const boxW = Math.max.apply(null, rows.map(r => ctx.measureText(r).width)) + 14;
    const boxH = rows.length * 13 + 9;
    let bx = px + 9;
    if (bx + boxW > cssW - 6) bx = px - 9 - boxW;
    let by = padT + 6;
    // 半透明深色圆角底 + 细描边
    ctx.fillStyle = "rgba(17,24,39,.92)";
    ctx.strokeStyle = "rgba(255,255,255,.14)";
    ctx.lineWidth = 1;
    const r = 4;
    ctx.beginPath();
    ctx.moveTo(bx + r, by);
    ctx.arcTo(bx + boxW, by, bx + boxW, by + boxH, r);
    ctx.arcTo(bx + boxW, by + boxH, bx, by + boxH, r);
    ctx.arcTo(bx, by + boxH, bx, by, r);
    ctx.arcTo(bx, by, bx + boxW, by, r);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    rows.forEach(function (row, i) {
      const ty = by + 10 + i * 13;
      if (i === 0) {
        ctx.fillStyle = "#cbd5e1";
        ctx.fillText(row, bx + 7, ty);
      } else {
        const m = metrics[i - 1];
        ctx.fillStyle = m ? m.color : "#f9fafb";
        ctx.beginPath();
        ctx.arc(bx + 8, ty - 1, 2.2, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = "#f9fafb";
        ctx.fillText(row, bx + 14, ty);
      }
    });
    ctx.textAlign = "center";
  }

  state.geo = { x: x };
}


