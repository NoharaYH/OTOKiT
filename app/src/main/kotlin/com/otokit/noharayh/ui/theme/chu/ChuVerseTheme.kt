package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「VERSE」：官网背景的离线版本与主题取色。 */
object ChuVerseTheme {
    const val HTML_ASSET_PATH: String = "background/ChuVerse/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF70E7D9.toInt(),
            secondaryColor = 0xFFBDAEFF.toInt(),
        ),
    )
}
