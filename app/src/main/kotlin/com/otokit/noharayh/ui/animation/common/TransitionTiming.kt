package com.otokit.noharayh.ui.animation.common

import kotlin.math.abs

/** 自动播放、手势收尾和取消回弹共用剩余距离计时。 */
object TransitionTiming {
    fun remainingDuration(fullDurationMs: Long, from: Float, to: Float): Long =
        (fullDurationMs * abs(to.coerceIn(0f, 1f) - from.coerceIn(0f, 1f)))
            .toLong().coerceAtLeast(1L)
}
