package com.otokit.noharayh.ui.animation.chrome

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 保持 ViewDragHelper 的水平收尾时长，只为公开的自定义曲线入口提供 duration。 */
internal object DrawerSettleTiming {
    fun duration(
        delta: Int, parentWidth: Int, range: Int,
        velocityX: Float, velocityY: Float, minVelocity: Int, maxVelocity: Int,
    ): Int {
        if (delta == 0 || parentWidth <= 0 || range <= 0) return 0
        fun speed(value: Float): Int {
            val magnitude = abs(value.toInt())
            return if (magnitude < minVelocity) 0 else min(magnitude, maxVelocity)
        }
        val x = speed(velocityX)
        val y = speed(velocityY)
        val weight = if (x != 0) x.toFloat() / (x + y) else 1f
        val axisDuration = if (x > 0) {
            val half = parentWidth / 2
            val ratio = min(1f, abs(delta).toFloat() / parentWidth)
            val distance = half + half * sin((ratio - 0.5f) * (Math.PI / 2.0 * 0.3).toFloat())
            4 * (1000f * abs(distance / x)).roundToInt()
        } else {
            ((abs(delta).toFloat() / range + 1f) * 256).toInt()
        }
        return (min(axisDuration, 600) * weight).toInt()
    }
}
