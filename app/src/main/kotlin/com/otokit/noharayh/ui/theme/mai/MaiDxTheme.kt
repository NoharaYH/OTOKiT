package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「DX」：官网背景的离线版本与主题取色。 */
object MaiDxTheme {
    const val HTML_ASSET_PATH: String = "background/MaiDx/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFF00B9EF.toInt(),
            secondaryColor = 0xFFE74846.toInt(),
        ),
    )
}
