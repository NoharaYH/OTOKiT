package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「Splash」：官网背景的离线版本与主题取色。 */
object MaiSplashTheme {
    const val HTML_ASSET_PATH: String = "background/MaiSplash/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF7BE0B6.toInt(),
            secondaryColor = 0xFFDCF99C.toInt(),
        ),
    )
}
