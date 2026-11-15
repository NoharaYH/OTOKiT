package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「MAGiCAL」：官网背景的离线版本与主题取色。 */
object MaiMagicalTheme {
    const val HTML_ASSET_PATH: String = "background/MaiMagical/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/MaiMagical", hasEyes = true, decorationFromRight = false, decorationCrop = ThemePreviewCrop(1152, 160, 20, 56, 133, 69)),
        colors = ThemeColors(
            primaryColor = 0xFF58C3FF.toInt(),
            secondaryColor = 0xFF70F3E2.toInt(),
        ),
    )
}
