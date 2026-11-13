package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「BUDDiES+」：官网背景的离线版本与主题取色。 */
object MaiBuddiesPlusTheme {
    const val HTML_ASSET_PATH: String = "background/MaiBuddiesPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFF45BA7.toInt(),
            secondaryColor = 0xFF56C2DE.toInt(),
        ),
    )
}
