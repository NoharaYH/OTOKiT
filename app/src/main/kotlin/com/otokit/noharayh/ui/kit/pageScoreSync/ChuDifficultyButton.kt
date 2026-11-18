package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

internal enum class ChuDifficulty(val label: String, val border: Int) {
    Basic("BASIC", 0xff2dbd51.toInt()), Advanced("ADVANCED", 0xffe87628.toInt()),
    Expert("EXPERT", 0xffd52d2c.toInt()), Master("MASTER", 0xffa103d3.toInt()),
    Ultima("ULTIMA", Color.BLACK), WorldsEnd("WORLD'S END", 0xff19388a.toInt()),
}

internal class ChuDifficultyButton(context: Context, private val difficulty: ChuDifficulty) : PressSurface(context) {
    private val density = resources.displayMetrics.density
    private val caption = ViewPrimitives.text(context, difficulty.label, 18f, Color.WHITE, bold = true)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tile = Path()
    private val ultima = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(
        0xffff607a.toInt(), 0xffe5121d.toInt(), 0xff8d0a0f.toInt(), 0xff340100.toInt(),
    ), floatArrayOf(0f, .41f, .74f, 1f), Shader.TileMode.CLAMP)
    private val facets = listOf(
        gradient(0xffea2d2e.toInt(), 0xfff7b400.toInt(), true),
        gradient(0xffa3d221.toInt(), 0xff0072b8.toInt(), false),
        gradient(0xff00aeef.toInt(), 0xff0c4da2.toInt(), true),
        gradient(0xff9d005d.toInt(), 0xff2f3293.toInt(), false),
    )

    init {
        baseRadius = 0f
        outerHaloEnabled = true
        innerRippleEnabled = true
        whiteMaxAlpha = 0f
        contentDescription = difficulty.label
        surfaceContent = { canvas, bounds, _ -> drawFace(canvas, bounds) }
        renderSelection(isSelected)
        val inset = ViewPrimitives.dp(context, 7)
        addView(caption.apply {
            gravity = Gravity.CENTER
            maxLines = 1
            letterSpacing = .028f
            setShadowLayer(3f * density, 0f, 1.5f * density, 0x88000000.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(-1, -1).apply { setMargins(inset, 0, inset, 0) })
    }

    override fun setSelected(selected: Boolean) {
        if (selected == isSelected) return
        super.setSelected(selected)
        renderSelection(selected)
    }

    private fun renderSelection(selected: Boolean) {
        ink.colorFilter = if (selected) null else DifficultySelectionColors.dimFilter
        caption.setTextColor(if (selected) Color.WHITE else DifficultySelectionColors.dim(Color.WHITE))
        fillColor = if (selected) difficulty.border else DifficultySelectionColors.dim(difficulty.border)
    }

    fun setWide(wide: Boolean) {
        caption.setAutoSizeTextTypeUniformWithConfiguration(8, if (wide) 27 else 18, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    override fun buildSurfacePath(path: Path, bounds: RectF, top: Float, bottom: Float) {
        path.rewind()
        path.addRect(bounds, Path.Direction.CW)
    }

    private fun gradient(start: Int, end: Int, across: Boolean) = LinearGradient(
        0f, 0f, if (across) 1f else 0f, if (across) 0f else 1f, start, end, Shader.TileMode.CLAMP,
    )

    private fun drawFace(canvas: Canvas, bounds: RectF) {
        val outer = 4.5f * density
        val rim = outer + 1.2f * density
        ink.shader = null
        ink.color = Color.WHITE
        canvas.drawRect(bounds.left + outer, bounds.top + outer, bounds.right - outer, bounds.bottom - outer, ink)
        val w = bounds.width() - 2 * rim
        val h = bounds.height() - 2 * rim
        if (w <= 0 || h <= 0) return
        canvas.save()
        canvas.translate(bounds.left + rim, bounds.top + rim)
        canvas.scale(w, h)
        when (difficulty) {
            ChuDifficulty.WorldsEnd -> drawFacets(canvas, h / (2 * w))
            ChuDifficulty.Ultima -> {
                ink.shader = ultima
                canvas.drawRect(0f, 0f, 1f, 1f, ink)
            }
            else -> {
                ink.color = difficulty.border
                canvas.drawRect(0f, 0f, 1f, 1f, ink)
            }
        }
        canvas.restore()
    }

    private fun drawFacets(canvas: Canvas, inset: Float) {
        fun facet(index: Int, x1: Float, y1: Float, x2: Float, y2: Float,
                  x3: Float, y3: Float, x4: Float = x3, y4: Float = y3) {
            tile.rewind()
            tile.moveTo(x1, y1)
            tile.lineTo(x2, y2)
            tile.lineTo(x3, y3)
            tile.lineTo(x4, y4)
            tile.close()
            ink.shader = facets[index]
            canvas.drawPath(tile, ink)
        }
        facet(0, 0f, 0f, 1f, 0f, 1f - inset, .5f, inset, .5f)
        facet(1, 1f, 0f, 1f, 1f, 1f - inset, .5f)
        facet(2, inset, .5f, 1f - inset, .5f, 1f, 1f, 0f, 1f)
        facet(3, 0f, 0f, 0f, 1f, inset, .5f)
    }
}
