package com.otokit.noharayh.ui.window.breakpoints

/**
 * 断点阈值与分类规则，纯函数、不依赖 Android。
 *
 * 分类宽度 = max(0, 窗口宽 - 稳定左右安全区) / density，保持浮点精度，不提前取整；
 * IME、systemGestures 与应用 chrome 占用都不参与分类。
 */
object WindowBreakpointPolicy {

    const val COMPACT_MAX_WIDTH_DP: Float = 600f
    const val MEDIUM_MAX_WIDTH_DP: Float = 840f

    fun classify(stableWidthDp: Float): WindowLayoutClass = when {
        stableWidthDp < COMPACT_MAX_WIDTH_DP -> WindowLayoutClass.Compact
        stableWidthDp < MEDIUM_MAX_WIDTH_DP -> WindowLayoutClass.Medium
        else -> WindowLayoutClass.Expanded
    }

    /**
     * 扣稳定横向安全区后的浮点可用宽度。
     *
     * 宽高为零或 density 非有限正数时返回 null，调用方不得据此发布有效快照。
     */
    fun stableWidthDp(
        widthPx: Int,
        stableLeftPx: Int,
        stableRightPx: Int,
        density: Float,
    ): Float? {
        if (widthPx <= 0 || !density.isFinite() || density <= 0f) return null
        val usablePx = (widthPx - stableLeftPx - stableRightPx).toFloat()
        return (usablePx / density).coerceAtLeast(0f)
    }
}
