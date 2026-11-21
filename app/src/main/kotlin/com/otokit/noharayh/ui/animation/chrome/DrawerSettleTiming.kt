package com.otokit.noharayh.ui.animation.chrome

import kotlin.math.abs
import kotlin.math.roundToInt

internal object DrawerSettleTiming {
    private const val AUTO_OPEN_MS = 320
    private const val OPEN_MS = 280
    private const val CLOSE_MS = 340
    private const val MIN_OPEN_MS = 80
    private const val MIN_CLOSE_MS = 160

    fun gestureOpen(remainingPx: Int, distanceFromThresholdPx: Int): Int {
        if (remainingPx <= 0) return 0
        if (remainingPx >= distanceFromThresholdPx) return AUTO_OPEN_MS
        return (MIN_OPEN_MS + (AUTO_OPEN_MS - MIN_OPEN_MS) * remainingPx.toFloat() / distanceFromThresholdPx).roundToInt()
    }

    fun duration(delta: Int, drawerWidth: Int, velocityX: Float): Int {
        if (delta == 0 || drawerWidth <= 0) return 0
        val remaining = (abs(delta).toFloat() / drawerWidth).coerceAtMost(1f)
        val fullDuration = if (delta > 0) OPEN_MS else CLOSE_MS
        val minDuration = if (delta > 0) MIN_OPEN_MS else MIN_CLOSE_MS
        val duration = minDuration + (fullDuration - minDuration) * remaining
        if (delta * velocityX <= 0f) return duration.roundToInt()
        val travelMs = 1000f * delta / velocityX
        return travelMs.coerceIn(minDuration.toFloat(), duration).roundToInt()
    }
}
