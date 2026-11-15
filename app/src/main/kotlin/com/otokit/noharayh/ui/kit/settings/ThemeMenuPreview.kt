package com.otokit.noharayh.ui.kit.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.otokit.noharayh.ui.animation.settings.ThemePreviewFrames
import com.otokit.noharayh.ui.constants.motion.ThemePreviewMotion as Spec
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors as Colors
import kotlin.math.max
import kotlin.math.min

/** 一行静态 WebP 的轻量绘制；按压裁剪、覆盖和涟漪仍由 PressSurface 拥有。 */
internal class ThemeMenuPreview(primaryColor: Int, private val density: Float, private val decorationFromRight: Boolean = false) {
    private var palette = ThemeMenuPreviewPalette.fromPrimary(primaryColor)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grayscale = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Dimensions.MENU_SELECTED_STROKE_DP * density * 2f
    }
    private val destination = RectF()
    private var expansion = 0f
    private var selected = false
    private var progress = 0f
    private var scrimWidth = -1f
    var images: ThemePreviewBitmaps? = null
    val backgroundColor: Int get() = if (selected) previewColorBetween(palette.underpaint, Colors.SELECTED, expansion) else palette.underpaint
    val needsDecoration: Boolean get() = (images != null && expansion > 0f) || (selected && expansion > 0f)
    val contentColor: Int get() = previewColorBetween(if (selected) com.otokit.noharayh.ui.constants.settings.SettingsComponentColors.TITLE else com.otokit.noharayh.ui.constants.settings.SettingsComponentColors.TEXT, android.graphics.Color.WHITE,
        if (images == null) 0f else expansion * ThemePreviewFrames.layer(progress, 0f, Spec.BACKGROUND_END))

    fun render(expansion: Float, selected: Boolean) { this.expansion = expansion; this.selected = selected }
    fun motion(value: Float) { progress = value }
    fun setPrimaryColor(color: Int) { if (palette.primary != color) palette = ThemeMenuPreviewPalette.fromPrimary(color) }

    /** 布局/资源改变时更新，draw 中不创建 Shader 或其他绘制对象。 */
    fun setTextExtent(textExtent: Float, width: Float) {
        val next = min(width * Dimensions.PREVIEW_SCRIM_MAX_FRACTION,
            min(textExtent + Dimensions.PREVIEW_SCRIM_PADDING_DP * density, Dimensions.PREVIEW_SCRIM_MAX_DP * density))
        if (scrimWidth == next) return
        scrimWidth = next
        scrim.shader = LinearGradient(0f, 0f, next.coerceAtLeast(1f), 0f,
            intArrayOf(0xC0000000.toInt(), 0xA0000000.toInt(), 0x00000000), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
    }

    fun draw(canvas: Canvas, bounds: RectF, surfacePath: Path) {
        val scene = images
        if (scene != null && expansion > 0f && progress > 0f) {
            imagePaint.colorFilter = if (selected) grayscale else null
            val background = ThemePreviewFrames.layer(progress, 0f, Spec.BACKGROUND_END)
            val decoration = ThemePreviewFrames.layer(progress, Spec.DECORATION_START, if (scene.eyes == null) 1f else Spec.DECORATION_END)
            val eyes = ThemePreviewFrames.layer(progress, Spec.EYES_START, 1f)
            val travel = min(bounds.width() * 0.25f, Dimensions.PREVIEW_TRAVEL_MAX_DP * density)
            drawLayer(canvas, scene.background, bounds, -travel * (1f - background), background, true)
            drawLayer(canvas, scene.decoration, bounds,
                travel * (if (decorationFromRight) 1f else -1f) * (1f - decoration), decoration, false)
            scene.eyes?.let { drawLayer(canvas, it, bounds, travel * (1f - eyes), eyes, false, eyes = true) }
            // 局部图像遮罩，右侧无暗层；不是组件/文字投影。
            scrim.alpha = (255 * expansion * background).toInt()
            canvas.drawRect(bounds.left, bounds.top, min(bounds.right, bounds.left + scrimWidth), bounds.bottom, scrim)
        }
        if (selected && expansion > 0f) {
            outline.color = previewColorBetween(Colors.SELECTED_STROKE and 0x00FFFFFF, Colors.SELECTED_STROKE, expansion)
            canvas.drawPath(surfacePath, outline)
        }
    }

    private fun drawLayer(canvas: Canvas, bitmap: Bitmap, bounds: RectF, dx: Float, alpha: Float, cover: Boolean, eyes: Boolean = false) {
        val fullHeight = Dimensions.MENU_ROW_DP * density
        var scale = if (cover) max(bounds.width() / bitmap.width, fullHeight / bitmap.height) else fullHeight / bitmap.height
        if (eyes) scale = min(scale, bounds.width() * Dimensions.PREVIEW_EYES_MAX_FRACTION / bitmap.width)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val x = if (cover) bounds.centerX() - w / 2f else bounds.right - w
        destination.set(x + dx, bounds.centerY() - h / 2f, x + dx + w, bounds.centerY() + h / 2f)
        imagePaint.alpha = (255 * alpha * expansion).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bitmap, null, destination, imagePaint)
    }
}
