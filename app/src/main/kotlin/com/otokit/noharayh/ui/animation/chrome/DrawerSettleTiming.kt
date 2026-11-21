package com.otokit.noharayh.ui.animation.chrome

import kotlin.math.abs
import kotlin.math.roundToInt

internal object DrawerSettleTiming {
    const val AUTO_OPEN_MS = 320
    private const val OPEN_MS = 280
    private const val CLOSE_MS = 220
    private const val MIN_MS = 80

    fun duration(delta: Int, drawerWidth: Int, velocityX: Float): Int {
        if (delta == 0 || drawerWidth <= 0) return 0
        val remaining = (abs(delta).toFloat() / drawerWidth).coerceAtMost(1f)
        val fullDuration = if (delta > 0) OPEN_MS else CLOSE_MS
        val duration = MIN_MS + (fullDuration - MIN_MS) * remaining
        if (delta * velocityX <= 0f) return duration.roundToInt()
        val travelMs = 1000f * delta / velocityX
        return travelMs.coerceIn(MIN_MS.toFloat(), duration).roundToInt()
    }
}
