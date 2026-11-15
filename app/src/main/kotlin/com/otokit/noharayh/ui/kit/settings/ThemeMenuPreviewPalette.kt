package com.otokit.noharayh.ui.kit.settings

import com.materialkolor.hct.Hct
import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors as Colors
import kotlin.math.roundToInt

/** 原主色不重新定调；另外两块只改变同一颜色的明度。 */
internal data class ThemeMenuPreviewPalette(val primary: Int, val variant: Int, val accent: Int, val underpaint: Int) {
    companion object {
        fun fromPrimary(primary: Int): ThemeMenuPreviewPalette {
            val hct = Hct.fromInt(primary)
            val step = Colors.TONE_STEP
            val (first, second) = when {
                hct.tone >= Colors.LIGHT_TONE -> -step to -step * 2
                hct.tone <= Colors.DARK_TONE -> step to step * 2
                else -> -step to step
            }
            fun shade(delta: Double) = Hct.from(hct.hue, hct.chroma, (hct.tone + delta).coerceIn(0.0, 100.0)).toInt()
            return ThemeMenuPreviewPalette(primary, shade(first), shade(second),
                previewColorBetween(Colors.BASE, primary, Colors.UNDERPAINT_PRIMARY_FRACTION))
        }
    }
}

/** 不依赖 Android Color，纯配色计算也可在 JVM 回归。 */
internal fun previewColorBetween(from: Int, to: Int, fraction: Float): Int {
    val t = fraction.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = from ushr shift and 255
        return (a + ((to ushr shift and 255) - a) * t).roundToInt() shl shift
    }
    return channel(24) or channel(16) or channel(8) or channel(0)
}
