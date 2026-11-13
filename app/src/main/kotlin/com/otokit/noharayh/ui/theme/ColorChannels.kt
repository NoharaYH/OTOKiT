package com.otokit.noharayh.ui.theme

import kotlin.math.abs
import kotlin.math.roundToInt

enum class NoneThemeColorRole { Primary, Secondary }

/** 编辑模型只转换同一个不透明 sRGB 颜色，不负责选择状态或动画。 */
enum class ColorChannels(val labels: List<String>, val maxima: List<Float>) {
    HSV(listOf("H", "S", "V"), listOf(360f, 100f, 100f)),
    HSL(listOf("H", "S", "L"), listOf(360f, 100f, 100f)),
    RGB(listOf("R", "G", "B"), listOf(255f, 255f, 255f));

    fun fromColor(color: Int, fallbackHue: Float = 0f): FloatArray {
        val r = (color ushr 16 and 255) / 255f
        val g = (color ushr 8 and 255) / 255f
        val b = (color and 255) / 255f
        if (this == RGB) return floatArrayOf(r * 255f, g * 255f, b * 255f)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = if (delta == 0f) fallbackHue else ((when (max) {
            r -> (g - b) / delta
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f) + 360f) % 360f
        if (this == HSV) return floatArrayOf(hue, if (max == 0f) 0f else delta / max * 100f, max * 100f)
        val lightness = (max + min) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        return floatArrayOf(hue, saturation * 100f, lightness * 100f)
    }

    fun toColor(values: FloatArray): Int {
        require(values.size == 3 && values.all { it.isFinite() })
        fun rgb(r: Float, g: Float, b: Float): Int = (0xFF shl 24) or
            (r.roundToInt().coerceIn(0, 255) shl 16) or
            (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        if (this == RGB) return rgb(values[0], values[1], values[2])
        val h = ((values[0] % 360f) + 360f) % 360f / 60f
        val s = values[1].coerceIn(0f, 100f) / 100f
        val v = values[2].coerceIn(0f, 100f) / 100f
        val c = if (this == HSV) v * s else (1f - abs(2f * v - 1f)) * s
        val x = c * (1f - abs(h % 2f - 1f))
        val m = if (this == HSV) v - c else v - c / 2f
        val channels = when (h.toInt()) {
            0 -> floatArrayOf(c, x, 0f)
            1 -> floatArrayOf(x, c, 0f)
            2 -> floatArrayOf(0f, c, x)
            3 -> floatArrayOf(0f, x, c)
            4 -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return rgb((channels[0] + m) * 255f, (channels[1] + m) * 255f, (channels[2] + m) * 255f)
    }

    /** 轨道表示当前通道的取值范围；RGB 保留红、绿、蓝的明确身份。 */
    fun ramps(values: FloatArray): Array<IntArray> = Array(3) { channel ->
        IntArray(7) { stop ->
            val fraction = stop / 6f
            when {
                this == RGB -> toColor(FloatArray(3) { if (it == channel) fraction * 255f else 0f })
                channel == 0 -> HSV.toColor(floatArrayOf(fraction * 360f, 100f, 100f))
                else -> toColor(values.copyOf().apply { this[channel] = fraction * maxima[channel] })
            }
        }
    }

    companion object {
        fun parseHex(text: String): Int? {
            val value = text.removePrefix("#")
            if (value.length != 6 || value.any { it.digitToIntOrNull(16) == null }) return null
            return (0xFF shl 24) or value.toInt(16)
        }
        fun hex(color: Int): String = (color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    }
}
