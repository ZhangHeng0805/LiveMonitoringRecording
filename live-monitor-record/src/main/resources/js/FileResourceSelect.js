function showFileSelectModal(key) {
  setModalContent(
    `${key}直播记录`,
    `<div class="bg-white rounded-lg shadow p-5">
            <!-- 根目录 -->
            <div id="fileTree" data-key="${key}"></div>
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

  // 初始化根目录
  renderContent(document.getElementById("fileTree"), "./");
}
// 模拟后端API请求
async function fetchFolderContents(path = "./") {
  try {
    // 这里直接用你的 request 方法 + await 异步等待
    const res = await request("fileRes", {
      key: document.getElementById("fileTree").dataset.key,
      path: path,
    });
    // 返回接口数据
    return res.data || [];
  } catch (error) {
    alert(`请求失败: ${error}`);
    return [];
  }
}

// 渲染内容方法（接收DOM容器对象+文件夹ID）
async function renderContent(boxDom, path) {
  // loading
  boxDom.innerHTML = `<div class="text-gray-500 text-sm py-1"><i class="fa fa-spinner fa-spin mr-1"></i>加载中...</div>`;
  const list = await fetchFolderContents(path);
  console.log("list", list);
  boxDom.innerHTML = "";
  if (!list || list.length === 0) {
    boxDom.innerHTML = `<div class="text-gray-400 text-sm py-1">暂无信息</div>`;
    return;
  }
  const key = document.getElementById("fileTree").dataset.key;
  list.forEach((item) => {
    if (item.type === "folder") {
      boxDom.innerHTML += `
            <div class="folder-item my-1">
              <div class="flex items-center gap-2 cursor-pointer py-1 hover:text-blue-600 transition-colors folder-toggle" data-fid="${item.path}">
                <i class="fa fa-chevron-right text-xs transition-transform duration-200"></i>
                <i class="fa fa-folder text-yellow-400"></i>
                <span>${item.name}</span>
              </div>
              <div class="folder-child-wrap pl-6 hidden"></div>
            </div>`;
    } else {
      boxDom.innerHTML += `
            <a href="${MainUrl}fileRes?key=${key}&path=${encodeURIComponent(item.path)}" target="_blank">
                <div class="flex items-center justify-between py-1 text-gray-600">
                    <div class="flex items-center gap-2">
                        <i class="fa fa-file"></i>
                        <span>${item.name}</span>
                    </div>
                    <span class="text-xs text-gray-400">${item.size}</span> 
                </div>
            </a>`;
    }
  });
  bindClick();
}

// 绑定点击事件
function bindClick() {
  document.querySelectorAll(".folder-toggle").forEach((item) => {
    if (item.dataset.loaded) return;
    item.dataset.loaded = "1";
    item.onclick = async function () {
      const arrow = this.querySelector(".fa-chevron-right");
      const folderIcon = this.querySelector(".fa-folder");
      const wrapDom = this.nextElementSibling;
      const fid = this.dataset.fid;

      // 箭头、文件夹图标切换
      arrow.classList.toggle("rotate-90");
      folderIcon.classList.toggle("fa-folder-open");
      folderIcon.classList.toggle("text-yellow-500");

      if (wrapDom.classList.contains("hidden")) {
        wrapDom.classList.remove("hidden");
        // 没有内容才请求接口加载
        if (wrapDom.innerHTML === "") {
          await renderContent(wrapDom, fid);
        }
      } else {
        wrapDom.classList.add("hidden");
      }
    };
  });
}
