function createLivingBiliInfoGridItems(room) {
    return `<div class="lml-info-item">
                <i class="fa fa-users is-accent"></i>
                <span>粉丝: ${escapeHtml(room.followers || 0)}</span>
            </div>
            <div class="lml-info-item">
                <i class="fa fa-eye is-accent"></i>
                <span>观看人数: ${escapeHtml(room.viewers || 0)}</span>
            </div>`;
}
