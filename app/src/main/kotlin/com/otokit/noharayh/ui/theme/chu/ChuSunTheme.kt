package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「SUN」：官网背景的离线版本与主题取色。 */
object ChuSunTheme {
    const val HTML_ASSET_PATH: String = "background/ChuSun/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF1964DC.toInt(),
            secondaryColor = 0xFFA7CCD6.toInt(),
        ),
    )
}
