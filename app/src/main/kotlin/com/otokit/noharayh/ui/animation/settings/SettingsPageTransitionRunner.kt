package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.common.TransitionTiming
import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition

/** 唯一父子页时钟；状态提交和 View 挂载由调用方负责。 */
class SettingsPageTransitionRunner(private val onFrame: (TransitionProgress) -> Unit) {
    private var animator: ValueAnimator? = null
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
        animator = ValueAnimator.ofFloat(progress, to).apply {
            duration = TransitionTiming.remainingDuration(SettingsPageTransition.DURATION_MS, progress, to)
            interpolator = MotionInterpolators.of(SettingsPageTransition.CURVE)
            addUpdateListener { emit(it.animatedValue as Float, true) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    emit(to, false)
                    onEnd()
                }
            })
            start()
        }
    }

    fun reset() { stop(); progress = 0f }
    fun stop() {
        val old = animator
        animator = null
        old?.removeAllListeners()
        old?.removeAllUpdateListeners()
        old?.cancel()
    }
    private fun emit(value: Float, running: Boolean) {
        progress = value.coerceIn(0f, 1f)
        onFrame(TransitionProgress(progress, running))
    }
    companion object {
        fun apply(view: View, frame: com.otokit.noharayh.ui.animation.common.TransitionFrame) {
            view.alpha = frame.alpha
            view.scaleX = frame.scale
            view.scaleY = frame.scale
            view.translationX = frame.translationX
            view.translationY = frame.translationY
        }
    }
}
