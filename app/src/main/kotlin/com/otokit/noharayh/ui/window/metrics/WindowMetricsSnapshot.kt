package com.otokit.noharayh.ui.window.metrics

/**
 * 当前应用窗口的不可变尺寸事实。
 *
 * [widthPx]、[heightPx] 是窗口边界（含系统装饰区）；根宿主的原点与尺寸单独记录，
 * 不能假定根与窗口永远等大（例如软键盘调整或系统装饰策略）。
 */
data class WindowMetricsSnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val rootLeftPx: Int,
    val rootTopPx: Int,
    val rootWidthPx: Int,
    val rootHeightPx: Int,
) {
    val aspectRatio: Float
        get() = if (heightPx > 0) widthPx.toFloat() / heightPx else 0f

    /** 宽高为零或 density 非有限正数时不得发布为有效快照。 */
    val isValid: Boolean
        get() = widthPx > 0 && heightPx > 0 && density.isFinite() && density > 0f
}
