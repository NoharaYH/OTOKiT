package com.otokit.noharayh.ui.animation.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.constants.motion.ContainerMotion

/** 通用状态插值：调用方提供数据与插值，不接触业务、View 或布局。 */
internal class ContentTransition<T>(initial: T, private val interpolate: (T, T, Float) -> T,
    private val onFrame: (T) -> Unit,
) {
    var frame: T = initial
        private set
    var isRunning = false
        private set
    private var target = initial
    private var animator: ValueAnimator? = null

    fun play(to: T, animate: Boolean = true, from: T = frame) {
        stop()
        target = to
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || from == to) { emit(to); return }
        isRunning = true
        emit(from)
        val next = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ContainerMotion.DURATION_MS
            interpolator = MotionInterpolators.of(ContainerMotion.CURVE)
            addUpdateListener { emit(interpolate(from, to, it.animatedValue as Float)) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null; this@ContentTransition.isRunning = false; emit(to)
                }
            })
        }
        animator = next
        next.start()
    }
    fun finish() { stop(); emit(target) }
    fun stop() {
        val old = animator
        animator = null; isRunning = false
        old?.removeAllUpdateListeners(); old?.removeAllListeners(); old?.cancel()
    }
    private fun emit(value: T) { frame = value; onFrame(value) }
}
