package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「NEW+」：官网背景的离线版本与主题取色。 */
object ChuNewPlusTheme {
    const val HTML_ASSET_PATH: String = "background/ChuNewPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            primaryColor = 0xFFEC8E21.toInt(),
            secondaryColor = 0xFFF8E231.toInt(),
        ),
    )
}
