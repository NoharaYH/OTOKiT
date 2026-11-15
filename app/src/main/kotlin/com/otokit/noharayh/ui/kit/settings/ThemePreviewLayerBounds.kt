package com.otokit.noharayh.ui.kit.settings

import android.graphics.RectF
import kotlin.math.max
import com.otokit.noharayh.ui.theme.ThemePreviewCrop

/** 分层切入与后台静帧合成共享构图，不分别拉伸横纵比例。 */
internal fun themePreviewLayerBounds(sourceWidth: Int, sourceHeight: Int, bounds: RectF,
    fullHeight: Float, cover: Boolean, destination: RectF,
    crop: ThemePreviewCrop? = null,
) {
    if (crop != null) {
        val scale = fullHeight / crop.canvasHeight
        val left = bounds.right - crop.canvasWidth * scale + crop.left * scale
        val top = bounds.centerY() - fullHeight / 2f + crop.top * scale
        destination.set(left, top, left + crop.width * scale, top + crop.height * scale)
        return
    }
    val scale = if (cover) max(bounds.width() / sourceWidth, fullHeight / sourceHeight) else fullHeight / sourceHeight
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    val x = if (cover) bounds.centerX() - width / 2f else bounds.right - width
    destination.set(x, bounds.centerY() - height / 2f, x + width, bounds.centerY() + height / 2f)
}
