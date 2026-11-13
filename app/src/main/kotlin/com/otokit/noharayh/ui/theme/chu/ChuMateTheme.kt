package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「Mate」：官网背景的离线版本与主题取色。 */
object ChuMateTheme {
    const val HTML_ASSET_PATH: String = "background/ChuMate/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFFFF5C7.toInt(),
            secondaryColor = 0xFFFFBB4A.toInt(),
        ),
    )
}
