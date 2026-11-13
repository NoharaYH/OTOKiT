package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「X-Verse-X」主题：只提供 HTML 相对入口与原始两色。 */
object ChuXVerseXTheme {

    /** 相对 assets 根目录的入口，不带 `assets/` 前缀。 */
    const val HTML_ASSET_PATH: String = "background/ChuXVerseX/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF6F8CFF.toInt(),
            secondaryColor = 0xFFB4CBF6.toInt(),
        ),
    )
}
