//直播中的抖音网格信息
function createLivingDouYinInfoGridItems(room) {
    const startTime = room.startTime
        ? formatDate(new Date(room.startTime))
        : "未知";
    return `<div class="flex items-center">
                                <i class="fa fa-eye text-primary mr-1"></i>
                                <span>当前观众: ${room.userCountStr || 0}</span>
                            </div>
                            <div class="flex items-center">
                                <i class="fa fa-eye text-primary mr-1"></i>
                                <span>总观看数: ${room.totalUserStr || 0}</span>
                            </div>
                            <div class="flex items-center">
                                <i class="fa fa-heart text-danger mr-1"></i>
                                <span class="like-count">点赞数: ${room.likeCount || 0}</span>
                            </div>
                            <div class="flex items-center">
                                <i class="fa fa-calendar text-primary mr-1"></i>
                                <span>开始时间: ${startTime}</span>
                            </div>`;
}