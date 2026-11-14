package com.otokit.noharayh.ui.kit.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.otokit.noharayh.ui.animation.common.ContinuousRoundedPath

/** Static backgrounds reuse their path until bounds change. Radius is in pixels. */
internal class ContinuousCornerDrawable(color: Int, private var radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()
    private val continuousCorners = ContinuousRoundedPath()
    private val rect = RectF()
    private var bottomRadius = radius
    fun setRadii(top: Float, bottom: Float) {
        radius = top; bottomRadius = bottom
        continuousCorners.setPath(path, rect, radius, bottomRadius)
        invalidateSelf()
    }
    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        continuousCorners.setPath(path, rect, radius, bottomRadius)
    }
    override fun draw(canvas: Canvas) = canvas.drawPath(path, paint)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
