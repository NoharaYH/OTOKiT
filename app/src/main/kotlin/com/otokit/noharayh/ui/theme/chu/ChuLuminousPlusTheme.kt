package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「LUMINOUS+」：官网背景的离线版本与主题取色。 */
object ChuLuminousPlusTheme {
    const val HTML_ASSET_PATH: String = "background/ChuLuminousPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF70B8FF.toInt(),
            secondaryColor = 0xFFFF98F6.toInt(),
        ),
    )
}
