package com.otokit.noharayh.ui.kit.settings

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors as Colors

/** 折叠态保留浅主题底；展开选中态沿真实按压轮廓绘制内描边。 */
internal class ThemeMenuPreview(primaryColor: Int, density: Float) {
    private var palette = ThemeMenuPreviewPalette.fromPrimary(primaryColor)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dimensions.MENU_SELECTED_STROKE_DP * density * 2f
    }
    private var expansion = 0f
    private var selected = false
    val backgroundColor: Int get() = if (selected) previewColorBetween(palette.underpaint, Colors.SELECTED, expansion) else palette.underpaint
    val needsDecoration: Boolean get() = selected && expansion > 0f

    fun render(expansion: Float, selected: Boolean) {
        this.expansion = expansion
        this.selected = selected
    }

    fun setPrimaryColor(color: Int) {
        if (palette.primary != color) palette = ThemeMenuPreviewPalette.fromPrimary(color)
    }

    fun draw(canvas: Canvas, bounds: RectF, surfacePath: Path) {
        if (selected && expansion > 0f) {
            outline.color = previewColorBetween(Colors.SELECTED_STROKE and 0x00FFFFFF, Colors.SELECTED_STROKE, expansion)
            canvas.drawPath(surfacePath, outline)
        }
    }
}
