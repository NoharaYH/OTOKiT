package com.otokit.noharayh.ui.kit.settings

import android.graphics.Rect
import android.graphics.RectF
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun themePreviewTravel(width: Float, fullHeight: Float): Float =
    min(width * 0.25f, fullHeight * Dimensions.PREVIEW_TRAVEL_MAX_DP / Dimensions.MENU_ROW_DP)

/** 共用一张底图，居中截取 W + D 对应区域；从右边对齐移动到左边对齐，全程覆盖行。 */
internal fun themePreviewBackgroundBounds(sourceWidth: Int, sourceHeight: Int, bounds: RectF,
    fullHeight: Float, progress: Float, source: Rect, destination: RectF,
) {
    val travel = themePreviewTravel(bounds.width(), fullHeight)
    val width = bounds.width() + travel
    val scale = max(width / sourceWidth, fullHeight / sourceHeight)
    val cropWidth = width / scale
    val cropHeight = fullHeight / scale
    source.set(((sourceWidth - cropWidth) / 2f).roundToInt(), ((sourceHeight - cropHeight) / 2f).roundToInt(),
        ((sourceWidth + cropWidth) / 2f).roundToInt(), ((sourceHeight + cropHeight) / 2f).roundToInt())
    val left = bounds.left - travel * (1f - progress.coerceIn(0f, 1f))
    destination.set(left, bounds.centerY() - fullHeight / 2f,
        left + width, bounds.centerY() + fullHeight / 2f)
}

/** 人物保留用户透明画布及内部位置，按行高等比缩放并靠右。 */
internal fun themePreviewCharacterBounds(sourceWidth: Int, sourceHeight: Int, bounds: RectF,
    fullHeight: Float, destination: RectF,
) {
    val width = sourceWidth * fullHeight / sourceHeight
    destination.set(bounds.right - width, bounds.centerY() - fullHeight / 2f,
        bounds.right, bounds.centerY() + fullHeight / 2f)
}
