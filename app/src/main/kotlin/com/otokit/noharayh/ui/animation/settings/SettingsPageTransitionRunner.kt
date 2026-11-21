package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.common.TransitionTiming
import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition
import com.otokit.noharayh.ui.constants.motion.MotionCurve

/** 唯一父子页时钟；状态提交和 View 挂载由调用方负责。 */
class SettingsPageTransitionRunner(private val onFrame: (TransitionProgress) -> Unit) {
    private var animator: ValueAnimator? = null
    private var durationMs = SettingsPageTransition.DURATION_MS
    internal var curve = SettingsPageTransition.CURVE
        private set
    var progress = 0f
        private set

    fun preview(value: Float) {
        stop()
        emit(value, true)
    }

    fun play(to: Float, onEnd: () -> Unit) {
        stop()
        if (!ValueAnimator.areAnimatorsEnabled() || kotlin.math.abs(progress - to) < 0.001f) {
            emit(to, false)
            onEnd()
            return
        }
        val next = ValueAnimator.ofFloat(progress, to).apply {
            duration = TransitionTiming.remainingDuration(durationMs, progress, to)
            interpolator = MotionInterpolators.of(curve)
            addUpdateListener { emit(it.animatedValue as Float, true) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    emit(to, false)
                    onEnd()
                }
            })
        }
        animator = next
        next.start()
    }

    fun reset(
        durationMs: Long = SettingsPageTransition.DURATION_MS,
        curve: MotionCurve = SettingsPageTransition.CURVE,
    ) {
        stop()
        this.durationMs = durationMs
        this.curve = curve
        progress = 0f
    }
    fun stop() {
        val old = animator
        animator = null
        old?.removeAllListeners()
        old?.removeAllUpdateListeners()
        old?.cancel()
    }
    private fun emit(value: Float, running: Boolean) {
        progress = value
        onFrame(TransitionProgress(value, running))
    }
    fun apply(view: View, frame: TransitionFrame) {
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
    }
}
