package com.otokit.noharayh.ui.chrome

/**
 * 标题栏的固定像素尺寸。
 *
 * 来源按运行时密度计算并逐项取整，XML 的 48dp / 72dp 只是旧初始值，不能直接照抄。
 */
data class TitleBarMetrics(
    val rowHeightPx: Int,
    val buttonSizePx: Int,
    val padPx: Int,
    val titleAnchorStartPx: Int,
    val iconPaddingPx: Int,
    val englishTextSizePx: Float,
    val chineseTextSizePx: Float,
    val lineGapPx: Int,
    val opticalNudgePx: Float,
)

object TitleBarMetricsCalculator {

    private const val SPACING_DP = 18f
    private const val BUTTON_SCALE = 2.55f
    private const val PAD_DP = 12f
    private const val ICON_DP = 24f
    private const val LINE_GAP_DP = 2.5f
    private const val OPTICAL_NUDGE_DP = 1.25f

    /** 英文与中文字号相对菜单边长的比例。 */
    private const val ENGLISH_SIZE_RATIO = 0.30f
    private const val CHINESE_SIZE_RATIO = 0.506f

    fun calculate(density: Float): TitleBarMetrics {
        val spacingPx = (SPACING_DP * density).toInt()
        val buttonSizePx = (spacingPx * BUTTON_SCALE).toInt()
        val padPx = (PAD_DP * density).toInt()
        val iconPx = (ICON_DP * density).toInt()

        return TitleBarMetrics(
            // 尾部 1 是来源的 1px，不是 1dp。
            rowHeightPx = padPx + buttonSizePx + padPx + 1,
            buttonSizePx = buttonSizePx,
            padPx = padPx,
            titleAnchorStartPx = padPx + buttonSizePx + padPx,
            iconPaddingPx = ((buttonSizePx - iconPx) / 2).coerceAtLeast(0),
            englishTextSizePx = buttonSizePx * ENGLISH_SIZE_RATIO,
            chineseTextSizePx = buttonSizePx * CHINESE_SIZE_RATIO,
            lineGapPx = (LINE_GAP_DP * density).toInt(),
            opticalNudgePx = OPTICAL_NUDGE_DP * density,
        )
    }
}
