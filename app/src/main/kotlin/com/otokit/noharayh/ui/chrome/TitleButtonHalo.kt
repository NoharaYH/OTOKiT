package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.otokit.noharayh.ui.animation.chrome.TitleButtonFeedback
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.animation.chrome.TitleButtonPressSpec
import kotlin.math.min

/**
 * 标题主按钮的黑色弹性外扩层，非交互、固定中心。
 *
 * 所有绘制都限制在标题行与屏幕安全边距内；环重叠时用一次 `saveLayerAlpha` 合成，
 * 避免多处 alpha 叠加出现深浅分层的第二圈。
 */
@SuppressLint("ViewConstructor") // 只由标题栏在代码中构造，不存在 XML 入口。
internal class TitleButtonHalo(
    context: Context,
    private val feedback: TitleButtonFeedback,
) : View(context) {

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 不透明黑；整体透明度由 saveLayerAlpha 统一施加。
        color = Color.BLACK
    }
    private val restOutset = resources.getDimension(ShellDimensions.titleButtonHaloRestOutset)
    private val peakOutset = resources.getDimension(ShellDimensions.titleButtonHaloPeakOutset)
    private val gap = resources.getDimension(ShellDimensions.titleButtonHaloEdgeGap)
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var radiusLimit = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        feedback.peakScale = peakOutset / restOutset
    }

    fun setButtonBounds(left: Int, top: Int, right: Int, bottom: Int) {
        centerX = (left + right) / 2f
        centerY = (top + bottom) / 2f
        baseRadius = min(right - left, bottom - top) / 2f
        radiusLimit = (minOf(centerX, centerY, width - centerX, height - centerY) - gap)
            .coerceAtLeast(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val layer = canvas.saveLayerAlpha(null, TitleButtonPressSpec.HALO_ALPHA)
        feedback.forEachExpansion { expansion ->
            val radius = min(baseRadius + restOutset * expansion, radiusLimit)
            if (expansion > 0f && radius > baseRadius) {
                canvas.drawCircle(centerX, centerY, radius, black)
            }
        }
        canvas.restoreToCount(layer)
    }
}
