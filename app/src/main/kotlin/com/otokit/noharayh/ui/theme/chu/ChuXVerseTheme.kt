package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「X-VERSE」：官网背景的离线版本与主题取色。 */
object ChuXVerseTheme {
    const val HTML_ASSET_PATH: String = "background/ChuXVerse/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF9B82E8.toInt(),
            secondaryColor = 0xFF70F0B8.toInt(),
        ),
    )
}
