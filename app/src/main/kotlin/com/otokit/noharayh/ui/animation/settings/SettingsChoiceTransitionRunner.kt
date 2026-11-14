package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.SettingsChoiceMotion

/** 每个分段按钮只有一个形状/文字动画；不持有按钮或选中状态。 */
internal class SettingsChoiceTransitionRunner {
    private val animators = mutableMapOf<Int, ValueAnimator>()
    fun play(index: Int, fromRadius: Float, toRadius: Float, fromColor: Int, toColor: Int,
        onFrame: (Float, Int) -> Unit) {
        stop(index)
        val evaluator = ArgbEvaluator()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SettingsChoiceMotion.DURATION_MS
            interpolator = MotionInterpolators.of(SettingsChoiceMotion.CURVE)
            addUpdateListener {
                val t = it.animatedValue as Float
                onFrame(fromRadius + (toRadius - fromRadius) * t,
                    evaluator.evaluate(t, fromColor, toColor) as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animators[index] === animation) animators.remove(index)
                }
            })
        }
        animators[index] = animator
        animator.start()
    }
    fun stop(index: Int) {
        animators.remove(index)?.let {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
    }
    fun stop() { animators.keys.toList().forEach(::stop) }
}
