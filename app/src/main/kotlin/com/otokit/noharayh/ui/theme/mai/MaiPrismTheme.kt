package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「PRiSM」：官网背景的离线版本与主题取色。 */
object MaiPrismTheme {
    const val HTML_ASSET_PATH: String = "background/MaiPrism/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF8C9CF8.toInt(),
            secondaryColor = 0xFFBEB3FA.toInt(),
        ),
    )
}
