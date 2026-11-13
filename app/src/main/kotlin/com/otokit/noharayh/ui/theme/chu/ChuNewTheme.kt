package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「NEW」：官网背景的离线版本与主题取色。 */
object ChuNewTheme {
    const val HTML_ASSET_PATH: String = "background/ChuNew/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFF3A607.toInt(),
            secondaryColor = 0xFFFED032.toInt(),
        ),
    )
}
