package com.otokit.noharayh.ui.animation.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.AnimationUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.constants.motion.PressHaloSpec as Spec

/**
 * 内容区按钮通用的黑色外溢层。
 *
 * 只负责绘制和按压弹簧，不持有业务、导航或触觉；宿主仍由 PressSurface 统一处理触摸。
 * 标题圆钮与抽屉胶囊有各自的多环/目标定位逻辑，不强行套用这里的单层规则。
 *
 * **宿主约束：** 本类会画到 host 边界之外，host 的直接父容器必须允许子 View 外溢
 * （`clipChildren=false`、`clipToPadding=false`）。本类不修改父级裁剪，调用方负责。
 */
internal class PressHalo(private val host: View) {
    private val density = host.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(Spec.ALPHA, 0, 0, 0)
    }
    private val rect = RectF()
    private val path = Path()
    private val corners = FloatArray(8)
    private val expansion = FloatValueHolder(0f)
    private val spring = SpringAnimation(expansion).apply {
        setMinimumVisibleChange(Spec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ -> host.invalidate() }
    }
    private var released = true
    private var pressedAt = 0L
    private val rest = Runnable {
        if (!released) animate(1f, Spec.REST_DAMPING, Spec.REST_STIFFNESS)
    }
    private val shrink = Runnable {
        if (released) animate(0f, Spec.RELEASE_DAMPING, Spec.RELEASE_STIFFNESS)
    }

    fun press() {
        host.removeCallbacks(rest)
        host.removeCallbacks(shrink)
        released = false
        pressedAt = AnimationUtils.currentAnimationTimeMillis()
        animate(Spec.PEAK_OUTSET_DP / Spec.REST_OUTSET_DP, Spec.EXPAND_DAMPING, Spec.EXPAND_STIFFNESS)
        host.postDelayed(rest, Spec.EXPAND_DURATION_MS)
    }

    fun release() {
        if (released) return
        released = true
        host.removeCallbacks(rest)
        host.removeCallbacks(shrink)
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - pressedAt
        host.postDelayed(shrink, (Spec.EXPAND_DURATION_MS - elapsed).coerceAtLeast(0L))
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        inset: Float,
        cornerRadius: Float,
        bottomCornerRadius: Float = cornerRadius,
    ) {
        val outset = Spec.REST_OUTSET_DP * density * expansion.value.coerceAtLeast(0f)
        if (outset < 0.01f || width <= 0 || height <= 0) return
        rect.set(
            inset - outset,
            inset - outset,
            width - inset + outset,
            height - inset + outset,
        )
        corners.fill((cornerRadius + outset).coerceAtMost(rect.height() / 2f), 0, 4)
        corners.fill((bottomCornerRadius + outset).coerceAtMost(rect.height() / 2f), 4, 8)
        path.reset()
        path.addRoundRect(rect, corners, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }

    fun reset() {
        host.removeCallbacks(rest)
        host.removeCallbacks(shrink)
        spring.cancel()
        expansion.value = 0f
        released = true
        host.invalidate()
    }

    private fun animate(target: Float, damping: Float, stiffness: Float) {
        spring.spring = SpringForce(target).setDampingRatio(damping).setStiffness(stiffness)
        spring.animateToFinalPosition(target)
    }
}
