package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion
import kotlin.math.abs

/** 由实际滑块路程产生齿感，不排队、不用定时器补震。 */
internal class SliderDragTicks {
    enum class Feedback { Tick, Edge }
    private var lastValue = 0f
    private var distanceDp = 0f
    private var lastTickAt = 0L

    fun begin(value: Float, now: Long) {
        lastValue = value
        distanceDp = 0f
        lastTickAt = now
    }

    fun move(value: Float, widthDp: Float, now: Long): Feedback? {
        if (value == lastValue) return null
        distanceDp += abs(value - lastValue) * widthDp
        lastValue = value
        if (value == 0f || value == 1f) {
            distanceDp = 0f
            if (now - lastTickAt < ColorPickerMotion.DRAG_TICK_INTERVAL_MS) return null
            lastTickAt = now
            return Feedback.Edge
        }
        if (distanceDp < ColorPickerMotion.DRAG_TICK_DISTANCE_DP ||
            now - lastTickAt < ColorPickerMotion.DRAG_TICK_INTERVAL_MS) return null
        distanceDp %= ColorPickerMotion.DRAG_TICK_DISTANCE_DP
        lastTickAt = now
        return Feedback.Tick
    }
}
