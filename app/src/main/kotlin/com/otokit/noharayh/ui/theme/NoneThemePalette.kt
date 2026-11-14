package com.otokit.noharayh.ui.theme

import com.materialkolor.hct.Hct

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

        fun resolve(seed: Int, wallpaper: Boolean, primary: Int?, secondary: Int?, wallpaperSecondary: Int? = null): NoneThemePalette {
            if (!wallpaper && primary != null && secondary != null) return fromColors(primary, secondary)
            val generated = fromSeed(seed, wallpaperSecondary)
            if (wallpaper) return generated
            return fromColors(primary ?: generated.colors.primaryColor, secondary ?: generated.colors.secondaryColor)
        }

        fun fromColors(primary: Int, secondary: Int): NoneThemePalette = NoneThemePalette(
            ThemeColors(primary, secondary),
            Hct.from(Hct.fromInt(primary).hue, BACKGROUND_CHROMA, BACKGROUND_TONE).toInt(),
        )

        /** 在系统公开的第二、第三候选色中选择有彩度、色相有区别的副色。 */
        fun selectWallpaperSecondary(primary: Int, candidates: List<Int>): Int? {
            val source = Hct.fromInt(primary)
            fun distance(hue: Double): Double {
                val delta = kotlin.math.abs(source.hue - hue)
                return minOf(delta, 360.0 - delta)
            }
            return candidates.filter { it ushr 24 == 255 && it != primary }
                .filter { Hct.fromInt(it).let { hct -> hct.chroma >= 8.0 && distance(hct.hue) >= 15.0 } }
                .maxByOrNull { Hct.fromInt(it).let { hct -> minOf(distance(hct.hue), 90.0) * minOf(hct.chroma, 48.0) } }
        }

        fun fromSeed(seedColor: Int, secondarySeed: Int? = null): NoneThemePalette {
            require(seedColor ushr 24 == 0xFF) { "主题种子色必须是不透明 ARGB" }
            require(secondarySeed == null || secondarySeed ushr 24 == 0xFF)
            val source = Hct.fromInt(seedColor)
            val secondary = Hct.fromInt(secondarySeed ?: seedColor)
            return NoneThemePalette(
                // 保留原色相与彩度，仅调整明度以保证白底、浅彩背景上的可读性。
                colors = ThemeColors(
                    primaryColor = Hct.from(source.hue, source.chroma, 40.0).toInt(),
                    secondaryColor = Hct.from(secondary.hue, secondary.chroma, 45.0).toInt(),
                ),
                backgroundColor = Hct.from(source.hue, minOf(source.chroma * 0.3, BACKGROUND_CHROMA), BACKGROUND_TONE).toInt(),
            )
        }
    }
}
