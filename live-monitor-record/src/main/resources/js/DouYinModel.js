//直播中的抖音网格信息
function createLivingDouYinInfoGridItems(room) {
    return `<div class="flex items-center">
                <i class="fa fa-eye text-primary mr-1"></i>
                <span>当前观众: ${escapeHtml(room.userCountStr || 0)}</span>
            </div>
            <div class="flex items-center">
                <i class="fa fa-eye text-primary mr-1"></i>
                <span title="${escapeHtml(room.totalUserStr || room.totalUser || "")}">总观看数: ${escapeHtml(room.totalUserStr || room.totalUser || 0)}</span>
            </div>
            <div class="flex items-center">
                <i class="fa fa-heart text-danger mr-1"></i>
                <span class="like-count">点赞数: ${escapeHtml(room.likeCount || 0)}</span>
            </div>`;
}
