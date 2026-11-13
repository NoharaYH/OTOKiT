package com.otokit.noharayh.ui.animation.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.NoneBackgroundColorMotion

/** 两页共享的无主题底色过渡；只输出颜色，重定向从当前显示帧接续。 */
internal class NoneBackgroundColorRunner(
    initialColor: Int,
    private val onFrame: (Int) -> Unit,
) {
    private var displayedColor = initialColor
    private var targetColor = initialColor
    private var animator: ValueAnimator? = null

    fun setColor(color: Int, animate: Boolean) {
        if (animate && color == targetColor) return
        cancel()
        targetColor = color
        if (!animate || displayedColor == color) {
            render(color)
            return
        }
        val next = ValueAnimator.ofArgb(displayedColor, color).apply {
            duration = NoneBackgroundColorMotion.DURATION_MS
            interpolator = MotionInterpolators.of(NoneBackgroundColorMotion.CURVE)
            addUpdateListener { render(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) {
                        animator = null
                        render(color)
                    }
                }
            })
        }
        animator = next
        next.start()
    }

    /** 后台直接收敛到已确认颜色，不留半途帧或后台动画。 */
    fun finish() {
        cancel()
        render(targetColor)
    }

    fun dispose() = cancel()

    private fun render(color: Int) {
        if (displayedColor == color) return
        displayedColor = color
        onFrame(color)
    }

    private fun cancel() {
        animator?.let {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
        animator = null
    }
}
