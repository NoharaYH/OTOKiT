package com.otokit.noharayh.ui.chrome

/** 抽屉临时呈现的唯一状态；不含 View、导航或持久化。 */
internal class DrawerMotionState {
    var progress = 0f
        private set
    var settledOpen = false
        private set
    var targetOpen: Boolean? = null
        private set
    var dragging = false
        private set
    var discardUntilEnd = false
        private set
    private var dragOriginOpen = false

    val blocksInput: Boolean
        get() = progress > 0f || targetOpen != null || dragging || discardUntilEnd

    fun beginDrag(): Boolean {
        if (discardUntilEnd) return false
        dragOriginOpen = settledOpen
        targetOpen = null
        dragging = true
        return true
    }

    fun moveTo(value: Float) {
        if (dragging || targetOpen != null) progress = value.coerceIn(0f, 1f)
    }

    /** 外部命令取消已捕获手势，剩余事件直到 UP/CANCEL 都不能重新接管。 */
    fun request(open: Boolean) {
        if (dragging) discardUntilEnd = true
        dragging = false
        targetOpen = open
    }

    fun releaseTo(open: Boolean) {
        dragging = false
        targetOpen = open
    }

    fun cancelDrag() {
        request(dragOriginOpen)
    }

    fun finishSettle() {
        val open = targetOpen ?: return
        settledOpen = open
        progress = if (open) 1f else 0f
        targetOpen = null
    }

    fun forceClose() {
        if (dragging) discardUntilEnd = true
        dragging = false
        targetOpen = null
        settledOpen = false
        progress = 0f
    }

    fun endStream() {
        discardUntilEnd = false
    }
}
