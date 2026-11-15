package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors as Colors
import kotlin.math.roundToInt

/** 只混合现有主色与菜单底色，不重新定调或计算已取消的几何色块。 */
internal data class ThemeMenuPreviewPalette(val primary: Int, val underpaint: Int) {
    companion object {
        fun fromPrimary(primary: Int) = ThemeMenuPreviewPalette(primary,
            previewColorBetween(Colors.BASE, primary, Colors.UNDERPAINT_PRIMARY_FRACTION))
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
