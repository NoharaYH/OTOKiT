package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.graphics.Rect
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.common.TransitionTiming
import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition

/** 唯一父子页时钟；状态提交和 View 挂载由调用方负责。 */
class SettingsPageTransitionRunner(private val onFrame: (TransitionProgress) -> Unit) {
    private var animator: ValueAnimator? = null
    private var unfold = false
    private val revealBounds = Rect()
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
            duration = TransitionTiming.remainingDuration(if (unfold) SettingsPageTransition.UNFOLD_DURATION_MS
                else SettingsPageTransition.DURATION_MS, progress, to)
            interpolator = MotionInterpolators.of(if (unfold) SettingsPageTransition.UNFOLD_CURVE else SettingsPageTransition.CURVE)
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

    fun reset(unfold: Boolean = false) { stop(); this.unfold = unfold; progress = 0f }
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
    fun apply(view: View, frame: com.otokit.noharayh.ui.animation.common.TransitionFrame, reveal: Float = 1f) {
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
        // 只扩展可见区域，文字与图标始终保持原比例；终态释放裁剪，保留外溢反馈。
        if (reveal >= 1f) view.clipBounds = null else {
            revealBounds.set(0, 0, view.width, (view.height * reveal.coerceIn(0f, 1f)).toInt())
            view.clipBounds = revealBounds
        }
    }
}
