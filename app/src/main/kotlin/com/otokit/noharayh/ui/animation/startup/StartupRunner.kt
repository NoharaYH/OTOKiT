package com.otokit.noharayh.ui.animation.startup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

/** 一次性的时钟/淡出执行器。调用方负责挂载和确认状态；stop 不重放完成事件。 */
class StartupRunner {
    private var animator: ValueAnimator? = null

    fun play(durationMs: Long, frame: (Float) -> Unit, complete: () -> Unit) {
        stop()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { frame(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    complete()
                }
            })
            start()
        }
    }

    fun stop() {
        animator?.apply { removeAllListeners(); removeAllUpdateListeners(); cancel() }
        animator = null
    }
}
