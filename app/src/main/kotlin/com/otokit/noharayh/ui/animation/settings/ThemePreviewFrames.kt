package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.constants.motion.ThemePreviewMotion as Spec

/** 单菜单逐行采样；方向改变仅换目标，从当前值继续，绝不重置帧。 */
internal class ThemePreviewFrames(count: Int) {
    val progress = FloatArray(count)
    private val targets = FloatArray(count)
    private val previous = FloatArray(count)
    private val ready = BooleanArray(count)
    private val delay = FloatArray(count)

    fun geometry(index: Int, expansion: Float) {
        if (expansion > previous[index]) targets[index] = 1f
        if (expansion < previous[index]) targets[index] = 0f
        previous[index] = expansion
        // 完全折叠后没有图像，立即停止不可见行的尾部刷新。
        if (expansion == 0f) progress[index] = 0f
    }

    fun ready(index: Int, order: Int = 0) {
        if (!ready[index]) delay[index] = order * Spec.STAGGER_MS
        ready[index] = true
    }

    fun advance(milliseconds: Float): Boolean {
        var active = false
        for (index in progress.indices) {
            if (!ready[index] || targets[index] == progress[index]) continue
            val available = (milliseconds - delay[index]).coerceAtLeast(0f)
            delay[index] = (delay[index] - milliseconds).coerceAtLeast(0f)
            val step = available / Spec.DURATION_MS
            progress[index] = if (progress[index] < targets[index]) (progress[index] + step).coerceAtMost(targets[index])
                else (progress[index] - step).coerceAtLeast(targets[index])
            if (targets[index] != progress[index]) active = true
        }
        return active
    }

    fun finish() {
        delay.fill(0f)
        for (index in progress.indices) if (ready[index]) progress[index] = targets[index]
    }

    companion object {
        fun layer(progress: Float, start: Float, end: Float): Float {
            val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
            return 1f - (1f - t) * (1f - t) * (1f - t)
        }
    }
}
