package com.otokit.noharayh.ui.theme

import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeVibrant

/** 无主题的派生配色；只保存输入色，颜色计算不依赖 Android、View 或系统壁纸。 */
data class NoneThemePalette(
    val colors: ThemeColors,
    val backgroundColor: Int,
) {
    companion object {
        /** 调色面板接入前的固定种子色。 */
        const val DEFAULT_SEED_COLOR: Int = 0xFF6750A4.toInt()
        private const val BACKGROUND_TONE = 88.0
        private const val BACKGROUND_CHROMA = 16.0

        val default: NoneThemePalette by lazy { fromSeed(DEFAULT_SEED_COLOR) }

        fun resolve(seed: Int, wallpaper: Boolean, primary: Int?, secondary: Int?): NoneThemePalette {
            if (!wallpaper && primary != null && secondary != null) return fromColors(primary, secondary)
            val generated = if (seed == DEFAULT_SEED_COLOR) default else fromSeed(seed)
            if (wallpaper) return generated
            return fromColors(primary ?: generated.colors.primaryColor, secondary ?: generated.colors.secondaryColor)
        }

        fun fromColors(primary: Int, secondary: Int): NoneThemePalette = NoneThemePalette(
            ThemeColors(primary, secondary),
            Hct.from(Hct.fromInt(primary).hue, BACKGROUND_CHROMA, BACKGROUND_TONE).toInt(),
        )

        fun fromSeed(seedColor: Int): NoneThemePalette {
            require(seedColor ushr 24 == 0xFF) { "主题种子色必须是不透明 ARGB" }
            val source = Hct.fromInt(seedColor)
            val scheme = SchemeVibrant(source, isDark = false, contrastLevel = 0.0)
            val roles = MaterialDynamicColors()
            return NoneThemePalette(
                colors = ThemeColors(
                    primaryColor = roles.primary().getArgb(scheme),
                    // 应用副色用于另一块装饰三角，采用 MCU tertiary 保留色相区别。
                    secondaryColor = roles.tertiary().getArgb(scheme),
                ),
                // 比 #E6E6E6 略暗，降低彩度而保留色相；不用接近白色的 surface。
                backgroundColor = Hct.from(source.hue, BACKGROUND_CHROMA, BACKGROUND_TONE).toInt(),
            )
        }
    }
}
