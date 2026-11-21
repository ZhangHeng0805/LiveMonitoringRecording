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
  }
</style>`;

/** 打开直播记录弹窗 */
function showFileSelectModal(key) {
  const safeKey = escapeHtml(key);
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
        hint: describeRoomKey(key),
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
        </div>
        <div class="frx-child" data-path="${escapeHtml(item.path)}" data-current-page="1" data-page-size="50" hidden></div>
      </div>`;
    } else {
      const kind = fileKind(item.name);
      const href = withApiToken(
          `${MainUrl}fileRes?key=${encodeURIComponent(key)}&path=${encodeURIComponent(item.path)}`
      );
      html += `
      <a class="frx-row frx-file" href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer"
         data-name="${escapeHtml(item.name)}" title="${escapeHtml(item.name)}">
        <i class="frx-chevron"></i>
        <i class="fa ${kind.icon} ${kind.color}"></i>
        <span class="frx-name">${escapeHtml(item.name)}</span>
        <span class="frx-size">${formatSize(item.size)}</span>
      </a>`;
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
