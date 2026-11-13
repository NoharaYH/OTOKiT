package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「FESTiVAL+」：官网背景的离线版本与主题取色。 */
object MaiFestivalPlusTheme {
    const val HTML_ASSET_PATH: String = "background/MaiFestivalPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFD6AEFF.toInt(),
            secondaryColor = 0xFFFDFA78.toInt(),
        ),
    )
}
