package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「PRiSM+」：官网背景的离线版本与主题取色。 */
object MaiPrismPlusTheme {
    const val HTML_ASSET_PATH: String = "background/MaiPrismPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF92B0FF.toInt(),
            secondaryColor = 0xFFEFB6EF.toInt(),
        ),
    )
}
