function showFileSelectModal(key) {
  setModalContent(
      `${key}直播记录`,
      `<div class="bg-white rounded-lg shadow p-5">
        <!-- 根目录：设置纵向滚动，避免内容溢出撑爆弹窗 -->
        <div id="fileTree" data-key="${key}" style="overflow-x: auto; white-space: nowrap;"></div>
    </div>`,
      [
        {
          text: "关闭",
          className:
              "bg-primary text-white hover:bg-primary/90 focus:ring-primary",
          onClick: closeModal,
        },
      ]
  );

  // 初始化根目录，默认第一页
  const rootDom = document.getElementById("fileTree");
  // 挂载分页状态：当前页、每页条数
  rootDom.dataset.currentPage = "1";
  rootDom.dataset.pageSize = "100";
  renderContent(rootDom, "./");
}

/**
 * 分页请求文件夹内容
 * @param {string} path 文件夹路径
 * @param {number} pageNum 页码
 * @param {number} pageSize 页大小
 * @returns {Promise<Object>} {files:[],total,pageNum,pageSize}
 */
async function fetchFolderContents(path = "./", pageNum = 1, pageSize = 100) {
  try {
    const key = document.getElementById("fileTree").dataset.key;
    const res = await getRequest("fileRes", {
      key: key,
      path: path,
      pageNum: pageNum,
      pageSize: pageSize,
    });
    // 后端返回 FilePageResult
    return res.data || { files: [], total: 0, pageNum: 1, pageSize: 100 };
  } catch (error) {
    alert(`请求失败: ${error}`);
    return { files: [], total: 0, pageNum: 1, pageSize: 100 };
  }
}

/**
 * 渲染文件夹容器（支持分页追加，加载更多永远在最底部）
 * @param {HTMLElement} boxDom 渲染容器
 * @param {string} path 当前文件夹路径
 * @param {boolean} isAppend 是否追加下一页（false=清空重新渲染）
 */
async function renderContent(boxDom, path, isAppend = false) {
  // 读取容器上存储的分页状态
  let currentPage = parseInt(boxDom.dataset.currentPage || "1");
  const pageSize = parseInt(boxDom.dataset.pageSize || "100");

  // 非追加模式先清空+loading
  if (!isAppend) {
    boxDom.innerHTML = `<div class="text-gray-500 text-sm py-1"><i class="fa fa-spinner fa-spin mr-1"></i>加载中...</div>`;
    currentPage = 1;
    boxDom.dataset.currentPage = currentPage;
  }

  const pageData = await fetchFolderContents(path, currentPage, pageSize);
  const { files, total } = pageData;
  const totalPage = Math.ceil(total / pageSize);

  // 首次渲染且无数据
  if (!isAppend) {
    boxDom.innerHTML = "";
    if (total === 0 || !files || files.length === 0) {
      boxDom.innerHTML = `<div class="text-gray-400 text-sm py-1">暂无信息</div>`;
      // 清空残留按钮
      const oldBtn = boxDom.querySelector(".load-more-btn");
      if(oldBtn) oldBtn.remove();
      return;
    }
  }

  const key = document.getElementById("fileTree").dataset.key;
  let htmlStr = "";
  // 拼接当前页条目字符串
  files.forEach((item) => {
    if (item.type === "folder") {
      // 文件夹项，子容器挂载分页默认值
      htmlStr += `
      <div class="folder-item my-1">
        <div class="flex items-center gap-2 cursor-pointer py-1 hover:text-blue-600 transition-colors folder-toggle" data-fid="${item.path}">
          <i class="fa fa-chevron-right text-xs transition-transform duration-200"></i>
          <i class="fa fa-folder text-yellow-400"></i>
          <span>${item.name}</span>
        </div>
        <div class="folder-child-wrap pl-6 hidden" data-path="${item.path}" data-current-page="1" data-page-size="100"></div>
      </div>`;
    } else {
      htmlStr += `
      <a href="${MainUrl}fileRes?key=${key}&path=${encodeURIComponent(item.path)}" target="_blank" style="overflow-x: auto;">
          <div class="flex items-center justify-between hover:text-blue-600 py-1 text-gray-600">
              <div class="flex items-center gap-2">
                  <i class="fa fa-file"></i>
                  <span>${item.name}</span>
              </div>
              <div class="text-xs text-gray-400 ml-3">${formatFileSize(item.size)}</div> 
          </div>
      </a>`;
    }
  });

  // ==========先移除旧按钮，再插入列表内容，避免内容插在按钮后面==========
  const oldBtn = boxDom.querySelector(".load-more-btn");
  if(oldBtn) oldBtn.remove();

  // 追加当前页列表HTML
  boxDom.insertAdjacentHTML("beforeend", htmlStr);

  // ==========有下一页则重新创建按钮，追加到容器最末尾==========
  if (currentPage < totalPage) {
    const moreBtn = document.createElement("div");
    moreBtn.className = "load-more-btn text-center py-2 text-blue-500 text-sm cursor-pointer hover:underline";
    moreBtn.textContent = "加载更多";
    moreBtn.dataset.folderPath = path;
    moreBtn.onclick = async function () {
      const parentBox = this.parentElement;
      let p = parseInt(parentBox.dataset.currentPage);
      parentBox.dataset.currentPage = ++p;
      await renderContent(parentBox, path, true);
    };
    boxDom.appendChild(moreBtn);
  }

  bindClick();
}

// 绑定文件夹展开折叠（修复重复绑定问题）
function bindClick() {
  document.querySelectorAll(".folder-toggle").forEach((item) => {
    if (item.dataset.loaded) return;
    item.dataset.loaded = "1";
    item.onclick = async function () {
      const arrow = this.querySelector(".fa-chevron-right");
      const folderIcon = this.querySelector(".fa-folder");
      const wrapDom = this.nextElementSibling;
      const fid = this.dataset.fid;

      arrow.classList.toggle("rotate-90");
      folderIcon.classList.toggle("fa-folder-open");
      folderIcon.classList.toggle("text-yellow-500");

      if (wrapDom.classList.contains("hidden")) {
        wrapDom.classList.remove("hidden");
        // 未初始化过才初次渲染第一页
        if (wrapDom.innerHTML === "") {
          await renderContent(wrapDom, fid);
        }
      } else {
        wrapDom.classList.add("hidden");
      }
    };
  });
}