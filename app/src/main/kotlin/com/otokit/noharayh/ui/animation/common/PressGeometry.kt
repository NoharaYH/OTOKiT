package com.otokit.noharayh.ui.animation.common

import kotlin.math.max
import kotlin.math.min

/** 纯几何：按压内缩、胶囊化与外环使用同一组轮廓，不改变布局或业务状态。 */
internal data class PressGeometry(
    val progress: Float, val inset: Float, val radius: Float, val top: Float,
    val bottom: Float, val haloBottom: Float, val fillInset: Float,
) {
    companion object {
        fun resolve(width: Float, height: Float, feedbackHeight: Float, progress: Float,
            pressInset: Float, baseRadius: Float, topRadius: Float?, bottomRadius: Float?,
            bottomFraction: Float, surfaceFillInset: Float): PressGeometry {
            val t = progress.coerceIn(0f, 1f)
            val inset = max(progress, 0f) * pressInset
            val cap = (min(width - inset * 2f, height - inset * 2f) / 2f).coerceAtLeast(0f)
            val pressedCap = min(cap, (feedbackHeight - inset * 2f).coerceAtLeast(0f) / 2f)
            fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
            return PressGeometry(t, inset,
                mix(baseRadius.coerceAtMost(cap), pressedCap, t).coerceAtMost(cap),
                mix((topRadius ?: baseRadius).coerceAtMost(cap), pressedCap, t),
                mix((bottomRadius ?: baseRadius).coerceAtMost(cap), pressedCap, t * bottomFraction.coerceIn(0f, 1f)),
                mix((bottomRadius ?: baseRadius).coerceAtMost(pressedCap), pressedCap, t),
                surfaceFillInset.coerceIn(0f, cap))
        }
    }
}
