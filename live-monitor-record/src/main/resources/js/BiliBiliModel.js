function createLivingBiliInfoGridItems(room) {
    const startTime = room.startTime
        ? formatDate(new Date(room.startTime))
        : "未知";
    return `<div class="flex items-center">
                                <i class="fa fa-users text-primary mr-1"></i>
                                <span>粉丝: ${room.followers || 0}</span>
                            </div>
                            <div class="flex items-center">
                                <i class="fa fa-eye text-primary mr-1"></i>
                                <span>观看人数: ${room.viewers || 0}</span>
                            </div>
                            <div class="flex items-center">
                                <i class="fa fa-calendar text-primary mr-1"></i>
                                <span>开始时间: ${startTime}</span>
                            </div>`
}