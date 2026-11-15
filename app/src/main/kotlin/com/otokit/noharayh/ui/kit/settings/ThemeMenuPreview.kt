package com.otokit.noharayh.ui.kit.settings

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors as Colors

/** 每行只画共享几何的一个切片；裁剪与按压反馈由 PressSurface 统一完成。 */
internal class ThemeMenuPreview(primaryColor: Int, density: Float) {
    private var palette = ThemeMenuPreviewPalette.fromPrimary(primaryColor)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // 路径位于外边界，外半侧被表面裁剪后得到指定宽度的内描边。
        strokeWidth = Dimensions.MENU_SELECTED_STROKE_DP * density * 2f
    }
    private val polygon = Path()
    private var expansion = 0f
    private var selected = false
    private var rowTop = 0f
    private var regionCenter = 0f
    val backgroundColor: Int get() = previewColorBetween(palette.primary,
        if (selected) Colors.SELECTED else palette.underpaint, expansion)
    val needsDecoration: Boolean get() = expansion < 1f || selected

    fun render(expansion: Float, selected: Boolean, rowTop: Float, regionCenter: Float) {
        this.expansion = expansion
        this.selected = selected
        this.rowTop = rowTop
        this.regionCenter = regionCenter
    }

    fun setPrimaryColor(color: Int) {
        if (palette.primary != color) palette = ThemeMenuPreviewPalette.fromPrimary(color)
    }

    fun draw(canvas: Canvas, bounds: RectF, surfacePath: Path) {
        val base = if (selected) Colors.SELECTED else palette.underpaint
        if (expansion < 1f) {
            val anchor = bounds.left + bounds.width() * Dimensions.PREVIEW_SPLIT_POSITION
            val center = regionCenter - rowTop
            fun cut(y: Float) = anchor + Dimensions.PREVIEW_SPLIT_SLOPE * (y - center)
            polygon.rewind()
            polygon.moveTo(cut(bounds.top), bounds.top)
            polygon.lineTo(bounds.right, bounds.top)
            polygon.lineTo(bounds.right, bounds.bottom)
            polygon.lineTo(cut(bounds.bottom), bounds.bottom)
            polygon.close()
            fill.color = previewColorBetween(palette.variant, base, expansion)
            canvas.drawPath(polygon, fill)
            if (bounds.bottom > center) {
                polygon.rewind()
                polygon.moveTo(anchor, center)
                polygon.lineTo(cut(bounds.bottom), bounds.bottom)
                polygon.lineTo(anchor - Dimensions.PREVIEW_BRANCH_SLOPE * (bounds.bottom - center), bounds.bottom)
                polygon.close()
                fill.color = previewColorBetween(palette.accent, base, expansion)
                canvas.drawPath(polygon, fill)
            }
        }
        if (selected && expansion > 0f) {
            outline.color = previewColorBetween(Colors.SELECTED_STROKE and 0x00FFFFFF, Colors.SELECTED_STROKE, expansion)
            canvas.drawPath(surfacePath, outline)
        }
    }
}
