package com.otokit.noharayh.ui.animation.shell

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.otokit.noharayh.ui.constants.motion.BackgroundSwitchMotion as Spec

/** 黑幕及禁用视觉共用同一帧；输入许可仍由主壳控制。 */
internal class BackgroundSwitchRunner(private val scrim: View) {
    private var generation = 0
    private val callbacks = mutableListOf<Runnable>()
    private var animator: ValueAnimator? = null

    fun play(onCovered: () -> Unit, onFinished: () -> Unit, onProgress: (Float) -> Unit) {
        cancel()
        val token = generation
        scrim.visibility = View.VISIBLE
        render(0f, onProgress)
        animateTo(1f, Spec.COVER_MS, onProgress)
        post(Spec.COVER_MS, token) {
            animator?.cancel()
            render(1f, onProgress)
            onCovered()
            if (generation != token) return@post
            post(Spec.HOLD_MS, token) {
                animateTo(0f, Spec.REVEAL_MS, onProgress)
                post(Spec.FINISH_DELAY_MS, token) {
                    animator?.cancel()
                    animator = null
                    render(0f, onProgress)
                    scrim.visibility = View.GONE
                    onFinished()
                }
            }
        }
    }

    private fun animateTo(to: Float, durationMillis: Long, onProgress: (Float) -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(scrim.alpha, to).apply {
            duration = durationMillis
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { render(it.animatedValue as Float, onProgress) }
            start()
        }
    }

    private fun render(progress: Float, onProgress: (Float) -> Unit) {
        scrim.alpha = progress
        onProgress(progress)
    }

    fun cancel() {
        generation++
        callbacks.forEach(scrim::removeCallbacks)
        callbacks.clear()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
        scrim.visibility = View.GONE
    }

    private fun post(delay: Long, token: Int, action: () -> Unit) {
        val callback = object : Runnable {
            override fun run() {
                callbacks.remove(this)
                if (generation == token) action()
            }
        }
        callbacks += callback
        scrim.postDelayed(callback, delay)
    }
}
