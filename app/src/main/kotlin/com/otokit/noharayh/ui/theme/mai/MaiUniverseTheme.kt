package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「UNiVERSE」：官网背景的离线版本与主题取色。 */
object MaiUniverseTheme {
    const val HTML_ASSET_PATH: String = "background/MaiUniverse/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF50B5FB.toInt(),
            secondaryColor = 0xFF9DF7FF.toInt(),
        ),
    )
}
