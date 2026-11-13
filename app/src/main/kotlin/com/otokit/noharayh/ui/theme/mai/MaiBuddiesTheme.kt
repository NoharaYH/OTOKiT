package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「BUDDiES」：官网背景的离线版本与主题取色。 */
object MaiBuddiesTheme {
    const val HTML_ASSET_PATH: String = "background/MaiBuddies/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFF6B446.toInt(),
            secondaryColor = 0xFFE84472.toInt(),
        ),
    )
}
