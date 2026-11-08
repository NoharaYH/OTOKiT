package com.otokit.noharayh.ui.window.insets

/** 单边四向像素值。 */
data class EdgeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val Zero: EdgeInsets = EdgeInsets()
    }
}

/**
 * 系统 Insets 的不可变快照。
 *
 * 六类 Insets 分别保存，不提前混合成一个数字；布局使用 [systemSafeEdges]，
 * 分类使用 [stableLeftPx]/[stableRightPx]，systemGestures 只供手势协调，IME 只用于交互内容避让。
 */
data class WindowInsetsSnapshot(
    val statusBars: EdgeInsets = EdgeInsets.Zero,
    val navigationBars: EdgeInsets = EdgeInsets.Zero,
    val displayCutout: EdgeInsets = EdgeInsets.Zero,
    val systemGestures: EdgeInsets = EdgeInsets.Zero,
    val captionBar: EdgeInsets = EdgeInsets.Zero,
    val ime: EdgeInsets = EdgeInsets.Zero,
    val statusBarsVisible: Boolean = false,
    val navigationBarsVisible: Boolean = false,
    val displayCutoutVisible: Boolean = false,
    val captionBarVisible: Boolean = false,
    val imeVisible: Boolean = false,
    val stableLeftPx: Int = 0,
    val stableRightPx: Int = 0,
) {
    /**
     * 当前布局安全区：状态栏、导航栏、开孔、窗口标题栏逐边取最大占用，重叠不相加。
     *
     * 不含 systemGestures 与 IME。
     */
    val systemSafeEdges: EdgeInsets
        get() = EdgeInsets(
            left = maxOf(statusBars.left, navigationBars.left, displayCutout.left, captionBar.left),
            top = maxOf(statusBars.top, navigationBars.top, displayCutout.top, captionBar.top),
            right = maxOf(statusBars.right, navigationBars.right, displayCutout.right, captionBar.right),
            bottom = maxOf(statusBars.bottom, navigationBars.bottom, displayCutout.bottom, captionBar.bottom),
        )

    /** IME 只有可见且有实际边缘占用时才参与交互区域收缩。 */
    val imeHasEdgeOccupancy: Boolean
        get() = imeVisible && ime.bottom > 0
}
