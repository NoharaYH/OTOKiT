package com.otokit.noharayh.ui.animation.common

import android.animation.ValueAnimator
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.SwitchMotion

/** 只执行开关指示的过渡；反向时从当前呈现值接续。 */
internal class SwitchTransitionRunner(private val onFrame: (Float) -> Unit) {
    private var animator: ValueAnimator? = null

    fun play(from: Float, to: Float) {
        stop()
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = SwitchMotion.DURATION_MS
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
