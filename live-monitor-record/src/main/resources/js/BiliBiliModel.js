function createLivingBiliInfoGridItems(room) {
    
    return `<div class="flex items-center">
                <i class="fa fa-users text-primary mr-1"></i>
                <span>粉丝: ${room.followers || 0}</span>
            </div>
            <div class="flex items-center">
                <i class="fa fa-eye text-primary mr-1"></i>
                <span>观看人数: ${room.viewers || 0}</span>
            </div>`
}