package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * 标题栏 chrome 行右侧的双三角装饰层。
 *
 * 几何与来源一致：w/h 为装饰自身布局尺寸。
 * - Mai 三角：`(w/2, 0) → (w, 0) → (w, 0.8h)`
 * - Chu 三角：`(0.75w, h) → (w, h) → (w, h/3)`
 *
 * 先画 Chu 再画 Mai；两者分别使用各自音游主题的 basicColor。本批不做颜色过渡动画（09 再处理）。
 */
class TitleBarDecorTrianglesView(context: Context) : View(context) {

    private val maiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val chuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }

    private val maiPath = Path()
    private val chuPath = Path()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setColors(maiColor: Int, chuColor: Int) {
        if (maiPaint.color != maiColor) {
            maiPaint.color = maiColor
            invalidate()
        }
        if (chuPaint.color != chuColor) {
            chuPaint.color = chuColor
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPaths(w.toFloat(), h.toFloat())
    }

    private fun rebuildPaths(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        maiPath.reset()
        maiPath.moveTo(w / 2f, 0f)
        maiPath.lineTo(w, 0f)
        maiPath.lineTo(w, h * 0.8f)
        maiPath.close()

        chuPath.reset()
        chuPath.moveTo(w * 0.75f, h)
        chuPath.lineTo(w, h)
        chuPath.lineTo(w, h / 3f)
        chuPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(chuPath, chuPaint)
        canvas.drawPath(maiPath, maiPaint)
    }
}
