package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「CiRCLE+」主题：只提供 HTML 相对入口与原始两色。 */
object MaiCirclePlusTheme {

    /** 相对 assets 根目录的入口，不带 `assets/` 前缀。 */
    const val HTML_ASSET_PATH: String = "background/MaiCirclePlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        colors = ThemeColors(
            basicColor = 0xFF477FE5.toInt(),
            buttonColor = 0xFFCF9AFA.toInt(),
        ),
    )
}
