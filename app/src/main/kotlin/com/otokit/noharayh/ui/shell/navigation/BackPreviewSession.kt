package com.otokit.noharayh.ui.shell.navigation

/** 返回手势的瞬态会话，不持有导航状态或 View。失效后仍等待本轮终止事件。 */
internal class BackPreviewSession {
    enum class Target { None, Settings, Drawer }
    private enum class Phase { Idle, Active, Invalidated }
    data class Preview(val target: Target, val progress: Float)

    private var phase = Phase.Idle
    var target = Target.None
        private set
    var progress = 0f
        private set

    val awaitingTerminal: Boolean get() = phase != Phase.Idle

    fun begin(target: Target) {
        if (phase == Phase.Active) return
        reset()
        if (target == Target.None) return
        this.target = target
        phase = Phase.Active
    }

    fun update(progress: Float) {
        if (phase == Phase.Active) this.progress = progress.coerceIn(0f, 1f)
    }

    /** 立即撤销目标，但保留失效标记，防止完成事件落入普通返回。 */
    fun invalidate(): Target {
        val previous = target
        if (phase == Phase.Active) {
            phase = Phase.Invalidated
            target = Target.None
            progress = 0f
        }
        return previous
    }

    /** cancel/finish 均终止本轮；只有有效会话才能交还其锁定的目标。 */
    fun take(): Preview? {
        val preview = if (phase == Phase.Active) Preview(target, progress) else null
        reset()
        return preview
    }

    fun reset() {
        phase = Phase.Idle
        target = Target.None
        progress = 0f
    }
}
