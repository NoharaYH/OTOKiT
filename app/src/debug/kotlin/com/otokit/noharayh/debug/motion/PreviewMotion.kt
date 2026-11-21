package com.otokit.noharayh.debug.motion

import android.view.animation.Interpolator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.exp

internal enum class PreviewMotion(val label: String, private val curve: Interpolator?) {
    STANDARD("A · Material 3 标准", MotionInterpolators.of(MotionCurve.Standard)),
    CLASSIC("B · Android 经典", MotionInterpolators.of(MotionCurve.FastOutSlowIn)),
    EMPHASIZED("C · 快推慢停", MotionInterpolators.of(MotionCurve.Emphasized)),
    SMOOTH("D · 柔和停稳", null),
    BOUNCE("E · 轻轻回弹", MotionInterpolators.of(MotionCurve.GentleBounce));

    val drawerCurve: Interpolator get() = curve ?:
        Interpolator { t -> if (t == 1f) 1f else 1f - (1f + 8f * t) * exp(-8f * t) }

    // Same 320 ms comparison window as the interactive samples. Spring velocity is in page widths/s.
    fun animator(from: Float, to: Float, velocity: Float = 0f, frame: (Float) -> Unit, end: () -> Unit) =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = LinearInterpolator()
            val offset = from - to
            val speed = velocity * .32f
            addUpdateListener {
                val t = it.animatedValue as Float
                val value = when {
                    t == 1f -> to
                    curve != null -> from - offset * curve.getInterpolation(t)
                    else -> to + (offset + (8f * offset + speed) * t) * exp(-8f * t)
                }
                frame(value)
            }
            doOnEnd { end() }
        }
}
