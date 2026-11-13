package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

internal class CapsuleCursorRunner(private val onAlpha: (Float) -> Unit) {
    private var animator: ValueAnimator? = null
    fun restart() {
        stop()
        onAlpha(1f)
        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = ColorPickerMotion.CURSOR_FADE_MS
            startDelay = ColorPickerMotion.CURSOR_HOLD_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { onAlpha(it.animatedValue as Float) }
            start()
        }
    }
    fun stop() {
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }
}
