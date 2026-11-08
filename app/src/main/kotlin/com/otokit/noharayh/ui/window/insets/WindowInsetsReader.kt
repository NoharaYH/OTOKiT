package com.otokit.noharayh.ui.window.insets

import androidx.core.view.WindowInsetsCompat

/**
 * 从 WindowInsetsCompat 提取纯值的唯一适配入口。
 *
 * 同时保留当前边距与 ignoringVisibility 的稳定横向边距：前者用于布局，后者只用于分类。
 * 不读取任何 chrome View，也不决定系统栏显示或隐藏。
 */
object WindowInsetsReader {

    fun read(insets: WindowInsetsCompat): WindowInsetsSnapshot {
        val statusBars = insets.edgeInsets(WindowInsetsCompat.Type.statusBars())
        val navigationBars = insets.edgeInsets(WindowInsetsCompat.Type.navigationBars())
        val displayCutout = insets.edgeInsets(WindowInsetsCompat.Type.displayCutout())
        val systemGestures = insets.edgeInsets(WindowInsetsCompat.Type.systemGestures())
        val captionBar = insets.edgeInsets(WindowInsetsCompat.Type.captionBar())
        val ime = insets.edgeInsets(WindowInsetsCompat.Type.ime())

        // 分类只用非 IME 系统的稳定横向边距；IME 类型不能传给 getInsetsIgnoringVisibility。
        val stableLeft = maxOf(
            insets.stableLeft(WindowInsetsCompat.Type.statusBars()),
            insets.stableLeft(WindowInsetsCompat.Type.navigationBars()),
            insets.stableLeft(WindowInsetsCompat.Type.displayCutout()),
            insets.stableLeft(WindowInsetsCompat.Type.captionBar()),
        )
        val stableRight = maxOf(
            insets.stableRight(WindowInsetsCompat.Type.statusBars()),
            insets.stableRight(WindowInsetsCompat.Type.navigationBars()),
            insets.stableRight(WindowInsetsCompat.Type.displayCutout()),
            insets.stableRight(WindowInsetsCompat.Type.captionBar()),
        )

        return WindowInsetsSnapshot(
            statusBars = statusBars,
            navigationBars = navigationBars,
            displayCutout = displayCutout,
            systemGestures = systemGestures,
            captionBar = captionBar,
            ime = ime,
            statusBarsVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars()),
            navigationBarsVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars()),
            displayCutoutVisible = insets.isVisible(WindowInsetsCompat.Type.displayCutout()),
            captionBarVisible = insets.isVisible(WindowInsetsCompat.Type.captionBar()),
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()),
            stableLeftPx = stableLeft,
            stableRightPx = stableRight,
        )
    }

    private fun WindowInsetsCompat.edgeInsets(type: Int): EdgeInsets {
        val insets = getInsets(type)
        return EdgeInsets(
            left = insets.left,
            top = insets.top,
            right = insets.right,
            bottom = insets.bottom,
        )
    }

    private fun WindowInsetsCompat.stableLeft(type: Int): Int = getInsetsIgnoringVisibility(type).left

    private fun WindowInsetsCompat.stableRight(type: Int): Int = getInsetsIgnoringVisibility(type).right
}
