package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion

internal data class ThemeCardFrame(
    val height: Float = 0f,
    val first: Float = 0f,
    val second: Float = 0f,
    val prefix: Float = 0f,
    val value: Float = 1f,
) {
    fun towards(other: ThemeCardFrame, progress: Float): ThemeCardFrame {
        val t = progress.coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return ThemeCardFrame(mix(height, other.height), mix(first, other.first),
            mix(second, other.second), mix(prefix, other.prefix), mix(value, other.value))
    }
}

/** 卡片高度、两行进退和标签缺口共享一个非线性进度。 */
internal class ThemeCardTransitionRunner(private val onFrame: (ThemeCardFrame) -> Unit) {
    var frame = ThemeCardFrame()
        private set
    var isRunning = false
        private set
    private var target = frame
    private var animator: ValueAnimator? = null

    fun play(to: ThemeCardFrame, animate: Boolean, valueChanged: Boolean) {
        stop()
        target = to
        val from = frame.copy(value = if (valueChanged) 0f else 1f,
            prefix = if (frame.first == 0f) to.prefix else frame.prefix)
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || from == to) {
            emit(to)
            return
        }
        isRunning = true
        emit(from)
        val next = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ThemeSelectionMotion.CARD_DURATION_MS
            interpolator = MotionInterpolators.of(MotionCurve.FastOutSlowIn)
            addUpdateListener { emit(from.towards(to, it.animatedValue as Float)) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    this@ThemeCardTransitionRunner.isRunning = false
                    emit(to)
                }
            })
        }
        animator = next
        next.start()
    }

    fun finish() { stop(); emit(target) }
    private fun stop() {
        val old = animator
        animator = null
        isRunning = false
        old?.removeAllListeners()
        old?.removeAllUpdateListeners()
        old?.cancel()
    }
    private fun emit(value: ThemeCardFrame) { frame = value; onFrame(value) }
}
