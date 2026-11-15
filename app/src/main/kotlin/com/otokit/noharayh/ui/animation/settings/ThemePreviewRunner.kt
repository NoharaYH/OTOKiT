package com.otokit.noharayh.ui.animation.settings

import android.animation.ValueAnimator
import android.os.Build
import android.view.Choreographer

/** 一条按需短时钟；没有活动采样时不再请求帧，不拥有菜单或稳定选择。 */
internal class ThemePreviewRunner(
    private val advance: (Float) -> Boolean,
    private val finish: () -> Unit,
    private val render: () -> Unit,
) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var posted = false
    val hasPendingWork: Boolean get() = posted
    private var previousNanos = 0L
    private var enabled = false

    fun setActive(active: Boolean) {
        if (enabled == active) return
        enabled = active
        if (active) request() else stop()
    }

    fun request() {
        if (!enabled || posted) return
        if (!ValueAnimator.areAnimatorsEnabled()) { finish(); render(); return }
        posted = true
        choreographer.postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        posted = false
        if (!enabled) return
        if (!ValueAnimator.areAnimatorsEnabled()) { finish(); render(); return }
        val scale = if (Build.VERSION.SDK_INT >= 33) ValueAnimator.getDurationScale().coerceAtLeast(0.01f) else 1f
        val delta = if (previousNanos == 0L) 0f else (frameTimeNanos - previousNanos) / 1_000_000f / scale
        previousNanos = frameTimeNanos
        val pending = advance(delta)
        render()
        if (pending) request() else previousNanos = 0L
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
        posted = false
        previousNanos = 0L
    }
}
