package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.constants.motion.ThemePreviewMotion as Spec

/** 单菜单逐行入场只向前完成；行高控制渐隐，完全折叠才释放本轮播放状态。 */
internal class ThemePreviewFrames(count: Int) {
    val progress = FloatArray(count)
    private val targets = FloatArray(count)
    private val ready = BooleanArray(count)
    private val delay = FloatArray(count)

    /** 返回本轮是否刚展开；错峰仅按同一批新展开项计数，不按整列位置计数。 */
    fun geometry(index: Int, expansion: Float, order: Int = 0): Boolean {
        val entering = expansion > 0f && targets[index] == 0f
        if (expansion == 0f) {
            targets[index] = 0f
            progress[index] = 0f
            ready[index] = false
            delay[index] = 0f
        } else {
            if (entering) delay[index] = order * Spec.STAGGER_MS
            targets[index] = 1f
        }
        return entering
    }

    fun ready(index: Int) {
        if (targets[index] == 0f) return
        ready[index] = true
    }

    fun advance(milliseconds: Float): Boolean {
        var active = false
        for (index in progress.indices) {
            if (!ready[index] || targets[index] == progress[index]) continue
            val available = (milliseconds - delay[index]).coerceAtLeast(0f)
            delay[index] = (delay[index] - milliseconds).coerceAtLeast(0f)
            val step = available / Spec.DURATION_MS
            progress[index] = (progress[index] + step).coerceAtMost(targets[index])
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
