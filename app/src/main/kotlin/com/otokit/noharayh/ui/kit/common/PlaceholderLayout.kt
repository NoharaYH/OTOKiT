package com.otokit.noharayh.ui.kit.common

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 六占位的纯布局公式。
 *
 * 按钮目标宽度来自 Application 窗口的 80%，但必须夹在白色矩形可用宽度内；
 * 高度固定为旧 160dp 的三分之一。不读取物理屏幕或设备名。
 */
object PlaceholderLayout {

    fun buttonHeightPx(density: Float): Int =
        ((160f / 3f) * density).roundToInt().coerceAtLeast(1)

    fun buttonWidthPx(windowWidthPx: Int, panelWidthPx: Int, density: Float): Int {
        val target = if (windowWidthPx > 0) {
            windowWidthPx * BUTTON_WINDOW_FRACTION
        } else {
            panelWidthPx * BUTTON_WINDOW_FRACTION
        }
        val available = (panelWidthPx - 2 * PANEL_INNER_PADDING_DP * density).toInt()
        return min(target.roundToInt(), available).coerceAtLeast(1)
    }

    const val BUTTON_WINDOW_FRACTION = 0.80f
    const val PANEL_WIDTH_FRACTION = 0.90f
    const val PANEL_HEIGHT_FRACTION = 0.70f
    const val PANEL_INNER_PADDING_DP = 24f
}
