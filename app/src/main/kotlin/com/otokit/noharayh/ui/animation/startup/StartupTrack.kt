package com.otokit.noharayh.ui.animation.startup

import android.view.animation.Interpolator
import androidx.core.graphics.ColorUtils

/** XML 导入的时间轨道，毫秒输入。HOLD 用于形变帧切换，其他轨道共享同一个时钟。 */
internal class StartupTrack(private val keys: List<Key>, private val color: Boolean = false) {
    data class Key(val time: Float, val value: Double, val hold: Boolean, val easing: Interpolator?)

    fun value(time: Float): Double {
        if (time <= keys.first().time) return keys.first().value
        val next = keys.indexOfFirst { it.time > time }
        if (next < 0) return keys.last().value
        val a = keys[next - 1]
        val b = keys[next]
        if (a.hold) return a.value
        val fraction = ((time - a.time) / (b.time - a.time)).coerceIn(0f, 1f)
        val eased = a.easing?.getInterpolation(fraction) ?: fraction
        return if (color) ColorUtils.blendARGB(a.value.toInt(), b.value.toInt(), eased).toDouble()
            else a.value + (b.value - a.value) * eased
    }
}
