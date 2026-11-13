package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion

import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve

/** 面板生长与通道归位共用项目曲线，取消后从当前展示帧接续。 */
internal class ColorPickerTransitionRunner {
    private var animator: ValueAnimator? = null
    fun play(from: Float = 0f, to: Float = 1f, onFrame: (Float) -> Unit) {
        stop()
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = ColorPickerMotion.TRANSITION_MS
            interpolator = MotionInterpolators.of(MotionCurve.FastOutSlowIn)
            addUpdateListener { onFrame(it.animatedValue as Float) }
            start()
        }
    }
    fun stop() {
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }
}
