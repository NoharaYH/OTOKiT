package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「BUDDiES」：官网背景的离线版本与主题取色。 */
object MaiBuddiesTheme {
    const val HTML_ASSET_PATH: String = "background/MaiBuddies/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/MaiBuddies", hasEyes = true, decorationFromRight = false, decorationCrop = ThemePreviewCrop(1152, 160, 0, 0, 1152, 153)),
        colors = ThemeColors(
            primaryColor = 0xFFF6B446.toInt(),
            secondaryColor = 0xFFE84472.toInt(),
        ),
    )
}
