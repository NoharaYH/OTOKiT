package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「PRiSM+」：官网背景的离线版本与主题取色。 */
object MaiPrismPlusTheme {
    const val HTML_ASSET_PATH: String = "background/MaiPrismPlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/MaiPrismPlus", hasEyes = true, decorationFromRight = false, decorationCrop = ThemePreviewCrop(1152, 160, 3, 0, 1149, 160)),
        colors = ThemeColors(
            primaryColor = 0xFF92B0FF.toInt(),
            secondaryColor = 0xFFEFB6EF.toInt(),
        ),
    )
}
