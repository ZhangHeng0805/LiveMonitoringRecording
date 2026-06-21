function roomRecycle() {
    showActionBeforeModal((key) => {
        getRequest("room-recycle/getList", {
            actionKey: key,
            pageNum: 1,
            pageSize: 100,
        })
            .then((msg) => {
                if (!msg.success) {
                    showActionAfterModal(msg);
                } else {
                    showRoomRecycleModel(key, msg.data)
                }
            });
    })
}

async function fetchRecycleContents(pageNum = 1, pageSize = 100) {
    try {
        const key = document.getElementById("roomRecycleList").dataset.key;
        const res = await getRequest("room-recycle/getList", {
            actionKey: key,
            pageNum: pageNum,
            pageSize: pageSize,
        });

        // 后端返回 FilePageResult
        return res.data || {files: [], total: 0, pageNum: 1, pageSize: 100};

    } catch (error) {
        alert(`请求失败: ${error}`);
    }
    return {files: [], total: 0, pageNum: 1, pageSize: 100};
}

function showRoomRecycleModel(key, data) {
    setModalContent(
        `直播监听回收站`,
        `<div class="bg-white rounded-lg shadow p-5">
        <!-- 根目录：设置纵向滚动，避免内容溢出撑爆弹窗 -->
        <div id="roomRecycleList" data-key="${key}" style="overflow-x: auto; white-space: nowrap;"></div>
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
    const rootDom = document.getElementById("roomRecycleList");
    // 挂载分页状态：当前页、每页条数
    rootDom.dataset.currentPage = "1";
    rootDom.dataset.pageSize = "100";
    renderRoomRecycleContent(rootDom, data);
}

async function renderRoomRecycleContent(rootDom, data, isAppend = false) {
    // 读取容器上存储的分页状态
    let currentPage = parseInt(rootDom.dataset.currentPage || "1");
    const pageSize = parseInt(rootDom.dataset.pageSize || "100");
    const key = document.getElementById("roomRecycleList").dataset.key;
    // 非追加模式先清空+loading
    if (!isAppend) {
        rootDom.innerHTML = `<div class="text-gray-500 text-sm py-1"><i class="fa fa-spinner fa-spin mr-1"></i>加载中...</div>`;
        currentPage = 1;
        rootDom.dataset.currentPage = currentPage;
    }
    if (data === null) {
        data = await fetchRecycleContents(currentPage, pageSize)
    }
    const {list, total} = data;
    const totalPage = Math.ceil(total / pageSize);
    // 首次渲染且无数据
    if (!isAppend) {
        rootDom.innerHTML = "";
        if (total === 0 || !list || list.length === 0) {
            rootDom.innerHTML = `<div class="text-gray-400 text-sm py-1">暂无信息</div>`;
            // 清空残留按钮
            const oldBtn = rootDom.querySelector(".load-more-btn");
            if (oldBtn) oldBtn.remove();
            return;
        }
    }

    let htmlStr = "";
    list.forEach((item) => {
        htmlStr += `
      <div class="my-1">
        <div class="flex items-center gap-2 cursor-pointer py-1 hover:text-blue-600 transition-colors recycle-toggle">
          <i class="fa fa-chevron-right text-xs transition-transform duration-200"></i>
          <i class="fa fa-recycle text-green-400"></i>
          <span>${item.title}</span>
        </div>
        <div class="recycle-child-wrap pl-2 py-2 hidden bg-gray-200 rounded-xl" data-path="${item.path}"></div>
      </div>`;
    });

    // ==========先移除旧按钮，再插入列表内容，避免内容插在按钮后面==========
    const oldBtn = rootDom.querySelector(".load-more-btn");
    if (oldBtn) oldBtn.remove();
    // 追加当前页列表HTML
    rootDom.insertAdjacentHTML("beforeend", htmlStr);

    // ==========有下一页则重新创建按钮，追加到容器最末尾==========
    if (currentPage < totalPage) {
        const moreBtn = document.createElement("div");
        moreBtn.className = "load-more-btn text-center py-2 text-blue-500 text-sm cursor-pointer hover:underline";
        moreBtn.textContent = "加载更多";
        moreBtn.onclick = async function () {
            let p = parseInt(rootDom.dataset.currentPage);
            rootDom.dataset.currentPage = ++p;
            await renderRoomRecycleContent(rootDom, null, true);
        };
        rootDom.appendChild(moreBtn);
    }
    //绑定点击事件
    document.querySelectorAll(".recycle-toggle").forEach((item) => {
        if (item.dataset.loaded) return;
        item.dataset.loaded = "1";
        item.onclick = async function () {
            const arrow = this.querySelector(".fa-chevron-right");
            arrow.classList.toggle("rotate-90");
            const wrapDom = this.nextElementSibling;
            const path = wrapDom.dataset.path;
            if (wrapDom.classList.contains("hidden")) {
                wrapDom.classList.remove("hidden");
                // 未初始化过才初次渲染第一页
                if (wrapDom.innerHTML === "") {
                    const res = await getRequest("room-recycle/getDetails", {path: path, actionKey: key});
                    let html;
                    try {
                        const room = JSON.parse(res.message);
                        html = `
                        <p><i class="fa fa-id-card mr-1"></i>ID: ${room.id}</p>
                        <p><i class="fa fa-user mr-1"></i>名称: ${room.name || ""}</p>
                        <p><i class="fa fa-tag mr-1"></i>平台: ${platformName[room.platform]}</p>
                        <p><i class="fa fa-hourglass mr-1"></i>频率: ${room.setting.delayIntervalSec}秒</p>
                        <p class="pt-2">
                        <button class="ml-6 text-green-500" onclick="recoverRoom('${path}')"><i class="fa fa-repeat mr-1"></i>恢复监听</button>
                        <button class="ml-6 text-red-500" onclick="deleteRoom('${path}')"><i class="fa fa-times mr-1"></i>彻底删除</button>
                        </p>
                        `;
                    } catch (e) {
                        html = `<p class="text-red-600">错误: ${res.message}</p>`
                    }

                    wrapDom.innerHTML = html;
                }
            } else {
                wrapDom.classList.add("hidden");
            }
        }
    });
}

function recoverRoom(path) {
    const rootDom = document.getElementById("roomRecycleList");
    const key = rootDom.dataset.key;
    getRequest("room-recycle/recover", {actionKey: key, path: path})
        .then(msg => {
            if (msg.success) {
                rootDom.dataset.currentPage = "1";
                renderRoomRecycleContent(rootDom, null, false);
            }else {
                alert(msg.message);
            }
        })
}

function deleteRoom(path) {
    const rootDom = document.getElementById("roomRecycleList");
    const key = rootDom.dataset.key;
    getRequest("room-recycle/delete", {actionKey: key, path: path})
        .then(msg => {
            if (msg.success) {
                rootDom.dataset.currentPage = "1";
                renderRoomRecycleContent(rootDom, null, false);
            }else {
                alert(msg.message);
            }
        })
}

