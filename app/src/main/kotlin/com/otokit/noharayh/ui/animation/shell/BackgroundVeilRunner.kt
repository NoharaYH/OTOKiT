package com.otokit.noharayh.ui.animation.shell

import android.animation.ValueAnimator
import android.view.View
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.BackgroundVeilTransition

/**
 * 背景首帧遮罩执行器。
 *
 * 只负责“保持 → 200ms 淡出”，不读 WebView，也不决定这次是否显示。
 * [show] 只负责铺灰色；[start] 必须在应用第一帧绘制前调用，避免启动窗口
 * 还没显示时 timeout 就已经在后台跑完。首帧/超时通过 [ready]/[start] 推入，重复触发幂等。
 */
class BackgroundVeilRunner(private val view: View) {

    private var animator: ValueAnimator? = null
    private var revealed = true
    private var started = false
    private var ready = false
    private var timeout: Runnable? = null

    /** 立即铺满灰色；此时不计时，等应用第一帧真正绘制后再 [start]。 */
    fun show() {
        cancelTimers()
        animator?.cancel()
        animator = null
        view.visibility = View.VISIBLE
        view.alpha = 1f
        started = false
        ready = false
        revealed = false
    }

    /** 应用第一帧绘制前启动计时；若首帧已提前就绪，直接淡出。 */
    fun start(timeoutMs: Long) {
        if (revealed || started) return
        started = true
        if (ready) {
            reveal()
            return
        }
        val timeout = Runnable { reveal() }
        this.timeout = timeout
        view.postDelayed(timeout, timeoutMs.coerceAtLeast(0L))
    }

    /** 首帧就绪；如果应用还没开始首帧，先记下，等 [start] 再淡出。 */
    fun ready() {
        ready = true
        if (started) reveal()
    }

    /** 首帧就绪或超时都走这里；只允许执行一次。 */
    fun reveal() {
        if (revealed) return
        revealed = true
        cancelTimers()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = BackgroundVeilTransition.FADE_DURATION_MS
            interpolator = MotionInterpolators.of(BackgroundVeilTransition.CURVE)
            addUpdateListener { animation ->
                view.alpha = animation.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator === animation) {
                        animator = null
                        view.alpha = 0f
                        view.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    fun dispose() {
        cancelTimers()
        animator?.cancel()
        animator = null
        view.animate().cancel()
        view.visibility = View.GONE
        revealed = true
        started = false
    }

    private fun cancelTimers() {
        timeout?.let { view.removeCallbacks(it) }
        timeout = null
    }
}
