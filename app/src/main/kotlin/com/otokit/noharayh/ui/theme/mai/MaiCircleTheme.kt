package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「CiRCLE」：官网背景的离线版本与主题取色。 */
object MaiCircleTheme {
    const val HTML_ASSET_PATH: String = "background/MaiCircle/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/MaiCircle", hasEyes = true, decorationFromRight = false, decorationCrop = ThemePreviewCrop(1152, 160, 977, 79, 97, 81)),
        colors = ThemeColors(
            primaryColor = 0xFFFF73AB.toInt(),
            secondaryColor = 0xFFFFAAC9.toInt(),
        ),
    )
}
