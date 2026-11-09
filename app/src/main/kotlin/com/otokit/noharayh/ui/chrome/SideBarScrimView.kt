package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View

/**
 * 抽屉模态遮罩：固定“左深右透”填充 + 随抽屉扫动的蒙版。
 *
 * 复合结果 = 遮罩填充 × 蒙版；蒙版由“白色平台 + 向右渐隐的斜坡”组成，
 * 平台宽度等于自身宽度，行程为 3 倍宽度，因此：
 * - 关闭时蒙版完全在左侧外，遮罩不可见；
 * - 打开过程中遮罩从左向右逐步浮现；
 * - 完全打开时平台盖满宽度，保留遮罩自己的左深右透填充。
 */
class SideBarScrimView(context: Context) : View(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val matrix = Matrix()

    private var progress = 0f

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setProgress(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        if (bounded == progress) return
        progress = bounded
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        scrimPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            0x3D000000,
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        maskPaint.shader = LinearGradient(
            0f,
            0f,
            3f * w,
            0f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 1f / 3f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        matrix.setTranslate(-3f * width + progress * 3f * width, 0f)
        maskPaint.shader?.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        canvas.restoreToCount(layer)
    }
}
