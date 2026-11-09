package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.constants.dimensions.ShellProportions

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

/** 尚未取整的资源像素值；计算器不读取 Resources。 */
data class TitleBarDimensionInput(
    val spacingPx: Float,
    val padPx: Float,
    val iconPx: Float,
    val lineGapPx: Float,
    val opticalNudgePx: Float,
)

object TitleBarMetricsCalculator {
    fun calculate(input: TitleBarDimensionInput): TitleBarMetrics {
        val spacingPx = input.spacingPx.toInt()
        val buttonSizePx = (spacingPx * ShellProportions.TITLE_BUTTON_SCALE).toInt()
        val padPx = input.padPx.toInt()
        val iconPx = input.iconPx.toInt()

        return TitleBarMetrics(
            // 尾部 1 是来源的 1px，不是 1dp。
            rowHeightPx = padPx + buttonSizePx + padPx + 1,
            buttonSizePx = buttonSizePx,
            padPx = padPx,
            titleAnchorStartPx = padPx + buttonSizePx + padPx,
            iconPaddingPx = ((buttonSizePx - iconPx) / 2).coerceAtLeast(0),
            englishTextSizePx = buttonSizePx * ShellProportions.TITLE_ENGLISH_SIZE,
            chineseTextSizePx = buttonSizePx * ShellProportions.TITLE_CHINESE_SIZE,
            lineGapPx = input.lineGapPx.toInt(),
            opticalNudgePx = input.opticalNudgePx,
        )
    }
}
